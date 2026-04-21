package com.basisttha.controller;

import com.basisttha.engine.Crdt;
import com.basisttha.engine.CrdtManagerService;
import com.basisttha.engine.Item;
import com.basisttha.exception.UnauthorizedUserException;
import com.basisttha.request.CursorDto;
import com.basisttha.response.DocumentChangeDto;
import com.basisttha.service.DocAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP message handlers for real-time collaborative editing.
 *
 * <p>Every handler receives the authenticated {@link Principal} injected by
 * Spring WebSocket — this is the {@code UsernamePasswordAuthenticationToken}
 * set in {@code WebSocketAuthenticationConfig} during the CONNECT handshake.
 * {@code principal.getName()} returns the user's email address.
 *
 * <h3>Authorization</h3>
 * <ul>
 *   <li>{@code /docs/change/{id}} — requires EDIT or OWNER permission.</li>
 *   <li>{@code /docs/cursor/{id}} — requires at least VIEW permission (read).</li>
 *   <li>{@code /docs/username/{id}} — requires at least VIEW permission.</li>
 * </ul>
 * If the permission check fails we throw {@link UnauthorizedUserException};
 * Spring's {@code @MessageExceptionHandler} propagates it back to the client.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DocWebSocketController {

    private final CrdtManagerService       crdtManagerService;
    private final SimpMessagingTemplate    messagingTemplate;
    private final DocAuthorizationService  docAuthorizationService;

    // -------------------------------------------------------------------------
    // Document-change handler
    // -------------------------------------------------------------------------

    @MessageMapping("/change/{docId}")
    public void onChange(
            @DestinationVariable String docId,
            DocumentChangeDto message,
            Principal principal) throws UnauthorizedUserException {

        String userEmail = principal.getName();
        long   id        = Long.parseLong(docId);

        if (!docAuthorizationService.canEditByEmail(id, userEmail)) {
            log.warn("User {} attempted to edit document {} without permission", userEmail, docId);
            throw new UnauthorizedUserException(
                    "You do not have edit permission for document " + docId);
        }

        Crdt crdt = crdtManagerService.getCrdt(id);
        if (crdt == null) {
            log.error("No live CRDT found for document {} — subscriber map is out of sync", docId);
            return;
        }

        applyOperation(crdt, message);

        // Broadcast to all subscribers including the sender so every replica
        // applies the same operation in the same order.
        messagingTemplate.convertAndSend("/docs/broadcast/changes/" + docId, message);
        log.debug("Broadcast {} op from {} on doc {}", message.getOperation(), userEmail, docId);
    }

    // -------------------------------------------------------------------------
    // Cursor-position handler
    // -------------------------------------------------------------------------

    @MessageMapping("/cursor/{docId}")
    public void cursor(
            @DestinationVariable String docId,
            CursorDto message,
            Principal principal) throws UnauthorizedUserException {

        enforceReadAccess(docId, principal.getName());
        messagingTemplate.convertAndSend("/docs/broadcast/cursors/" + docId, message);
    }

    // -------------------------------------------------------------------------
    // Active-user broadcast handler
    // -------------------------------------------------------------------------

    @MessageMapping("/username/{docId}")
    public void usernames(
            @DestinationVariable String docId,
            String message,
            Principal principal) throws UnauthorizedUserException {

        enforceReadAccess(docId, principal.getName());
        messagingTemplate.convertAndSend("/docs/broadcast/usernames/" + docId, message);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void applyOperation(Crdt crdt, DocumentChangeDto msg) {
        switch (msg.getOperation()) {
            case "insert" -> {
                Item item = Item.builder()
                        .id(msg.getId())
                        .content(msg.getContent())
                        .left(crdt.getItem(msg.getLeft()))
                        .right(crdt.getItem(msg.getRight()))
                        .operation(msg.getOperation())
                        .isBold(msg.isBold())
                        .isItalic(msg.isItalic())
                        .isUnderline(msg.isUnderline())
                        .isStrike(msg.isStrike())
                        .isDeleted(msg.isDeleted())
                        .header(msg.getHeader())
                        .align(msg.getAlign())
                        .list(msg.getList())
                        .indent(msg.getIndent())
                        .color(msg.getColor())
                        .background(msg.getBackground())
                        .link(msg.getLink())
                        .build();
                crdt.insert(item);
            }
            case "delete" -> crdt.delete(msg.getId());
            case "format" -> crdt.format(msg.getId(),
                    msg.isBold(), msg.isItalic(),
                    msg.isUnderline(), msg.isStrike(),
                    msg.getHeader(), msg.getAlign(), msg.getList(), msg.getIndent(),
                    msg.getColor(), msg.getBackground(), msg.getLink());
            default -> log.warn("Unknown CRDT operation '{}' — ignored", msg.getOperation());
        }
    }

    private void enforceReadAccess(String docId, String userEmail)
            throws UnauthorizedUserException {
        if (!docAuthorizationService.canAccessByEmail(Long.parseLong(docId), userEmail)) {
            log.warn("User {} attempted to access document {} without permission", userEmail, docId);
            throw new UnauthorizedUserException(
                    "You do not have access to document " + docId);
        }
    }
}
