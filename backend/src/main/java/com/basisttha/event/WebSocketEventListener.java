package com.basisttha.event;

import com.basisttha.engine.CrdtManagerService;
import com.basisttha.model.User;
import com.basisttha.model.WebSocketSession;
import com.basisttha.repo.UserRepository;
import com.basisttha.response.ActiveUsers;
import com.basisttha.service.DocAuthorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.util.UriTemplate;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to Spring WebSocket lifecycle events to manage per-document CRDT
 * loading, active-user tracking, and CRDT persistence on disconnect.
 *
 * <h3>Session tracking</h3>
 * <ul>
 *   <li>{@code socketSession} — maps WebSocket session-id → session metadata
 *       (display name + docId the session is subscribed to).</li>
 *   <li>{@code docSession} — maps docId → list of session-ids currently
 *       subscribed to that document.</li>
 * </ul>
 * Both maps use {@link ConcurrentHashMap} because Spring may dispatch lifecycle
 * events on different threads.
 */
@Slf4j
@Component
public class WebSocketEventListener {

    @Autowired private SimpMessageSendingOperations messagingTemplate;
    @Autowired private CrdtManagerService           crdtManagerService;
    @Autowired private UserRepository               userRepository;
    @Autowired private DocAuthorizationService      docAuthorizationService;

    /** sessionId → session metadata */
    private final ConcurrentHashMap<String, WebSocketSession> socketSession = new ConcurrentHashMap<>();

    /** docId (string) → list of sessionIds */
    private final ConcurrentHashMap<String, List<String>> docSession = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // CONNECT
    // -------------------------------------------------------------------------

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId    = headers.getSessionId();
        String displayName  = resolveDisplayName(headers);
        socketSession.put(sessionId, new WebSocketSession(displayName, ""));
        log.info("WebSocket CONNECT: session={} user={}", sessionId, displayName);
    }

    // -------------------------------------------------------------------------
    // SUBSCRIBE
    // -------------------------------------------------------------------------

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());

        String destination = headers.getDestination();
        if (destination == null) return;

        String docId = extractDocId(destination);
        if (docId.isEmpty()) return;   // subscription to a non-document topic (cursors, etc.)

        String sessionId = headers.getSessionId();

        // --- Authorization: reject if user cannot read this document ----------
        Principal principal = headers.getUser();
        if (principal == null) {
            log.warn("Subscribe attempt with no principal on session {} — rejected", sessionId);
            return;
        }
        try {
            if (!docAuthorizationService.canAccessByEmail(Long.parseLong(docId), principal.getName())) {
                log.warn("User {} tried to subscribe to document {} without access — rejected",
                        principal.getName(), docId);
                return;
            }
        } catch (Exception e) {
            log.error("Authorization check failed for doc {}: {}", docId, e.getMessage());
            return;
        }

        // --- Register session -------------------------------------------------
        WebSocketSession sessionData = socketSession.get(sessionId);
        if (sessionData != null) {
            sessionData.setDocId(docId);
        }

        docSession.compute(docId, (key, existing) -> {
            List<String> list = existing != null ? existing : new ArrayList<>();
            if (!list.contains(sessionId)) {
                list.add(sessionId);
            }
            return list;
        });

        // --- Load CRDT from DB if this is the first subscriber ----------------
        crdtManagerService.createCrdt(Long.parseLong(docId));

        log.info("Subscribed: session={} doc={} user={}", sessionId, docId,
                principal.getName());

        notifyActiveUsers(docId);
    }

    // -------------------------------------------------------------------------
    // DISCONNECT
    // -------------------------------------------------------------------------

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId    = headers.getSessionId();
        WebSocketSession sessionData = socketSession.remove(sessionId);

        if (sessionData == null) {
            log.debug("DISCONNECT for unknown session {} — nothing to clean up", sessionId);
            return;
        }

        String docId = sessionData.getDocId();
        if (docId == null || docId.isEmpty()) return;

        log.info("WebSocket DISCONNECT: session={} doc={} user={}",
                sessionId, docId, sessionData.getDisplayName());

        docSession.compute(docId, (key, participants) -> {
            if (participants == null) return null;
            participants.remove(sessionId);
            return participants.isEmpty() ? null : participants;
        });

        boolean noMoreSubscribers = !docSession.containsKey(docId);
        if (noMoreSubscribers) {
            log.info("Last subscriber left document {} — persisting CRDT", docId);
            crdtManagerService.saveAndDeleteCrdt(Long.parseLong(docId));
        }

        notifyActiveUsers(docId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void notifyActiveUsers(String docId) {
        List<String> participants = docSession.get(docId);
        List<String> displayNames = new ArrayList<>();

        if (participants != null) {
            for (String sid : participants) {
                WebSocketSession s = socketSession.get(sid);
                if (s != null) displayNames.add(s.getDisplayName());
            }
        }

        ActiveUsers payload = new ActiveUsers();
        payload.setDisplayNames(displayNames);
        messagingTemplate.convertAndSend("/docs/broadcast/usernames/" + docId, payload);
    }

    private String resolveDisplayName(SimpMessageHeaderAccessor headers) {
        Principal principal = headers.getUser();
        if (principal == null) {
            return "anonymous";
        }
        String email = principal.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return user.getDisplayName();
    }

    /**
     * Extracts the document id from a STOMP destination like
     * {@code /docs/broadcast/changes/42}.  Returns an empty string for
     * destinations that don't match (e.g. cursor or username topics).
     */
    private String extractDocId(String destination) {
        for (String pattern : List.of(
                "/docs/broadcast/changes/{id}",
                "/docs/broadcast/usernames/{id}",
                "/docs/broadcast/cursors/{id}")) {
            Map<String, String> vars = new UriTemplate(pattern).match(destination);
            String id = vars.get("id");
            if (id != null) return id;
        }
        return "";
    }
}
