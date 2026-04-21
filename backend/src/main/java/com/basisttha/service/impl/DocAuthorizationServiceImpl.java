package com.basisttha.service.impl;

import com.basisttha.exception.ResourceNotFoundException;
import com.basisttha.model.Doc;
import com.basisttha.model.User;
import com.basisttha.model.enums.Permission;
import com.basisttha.repo.DocRepository;
import com.basisttha.repo.UserRepository;
import com.basisttha.security.SecurityUtil;
import com.basisttha.service.DocAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocAuthorizationServiceImpl implements DocAuthorizationService {

    private final UserRepository userRepository;
    private final DocRepository  docRepository;

    // -------------------------------------------------------------------------
    // Email-based helpers (used by WebSocket handlers where the principal
    // carries an email, not a display name)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean canAccessByEmail(Long docId, String userEmail) {
        User user = findUserByEmail(userEmail);
        Doc  doc  = findDocById(docId);
        return isOwner(user.getDisplayName(), doc) || hasPermission(user.getDisplayName(), doc, null);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canEditByEmail(Long docId, String userEmail) {
        User user = findUserByEmail(userEmail);
        Doc  doc  = findDocById(docId);
        return canEdit(user.getDisplayName(), doc);
    }

    // -------------------------------------------------------------------------
    // Username/displayName-based helpers (used by HTTP service layer)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean canAccess(Long docId) {
        String email    = SecurityUtil.getCurrentUserEmail();
        User   user     = findUserByEmail(email);
        Doc    doc      = findDocById(docId);
        String username = user.getDisplayName();
        return isOwner(username, doc) || hasPermission(username, doc, null);
    }

    @Override
    public boolean canEdit(String username, Doc doc) {
        return isOwner(username, doc) || hasPermission(username, doc, Permission.EDIT);
    }

    @Override
    public boolean fullAccess(String username, Doc doc) {
        return isOwner(username, doc);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isOwner(String username, Doc doc) {
        return doc.getOwner().getDisplayName().equals(username);
    }

    /**
     * Returns true if the user has the specified permission (or ANY permission
     * when {@code required} is null).
     */
    private boolean hasPermission(String username, Doc doc, Permission required) {
        return doc.getSharedWith().stream().anyMatch(ud -> {
            if (!ud.getUser().getDisplayName().equals(username)) return false;
            return required == null || ud.getPermission() == required;
        });
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    private Doc findDocById(Long docId) {
        return docRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
    }
}
