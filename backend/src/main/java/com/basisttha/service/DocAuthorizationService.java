package com.basisttha.service;

import com.basisttha.model.Doc;

public interface DocAuthorizationService {

    /** True if the user identified by their Spring-Security email can read this doc. */
    boolean canAccessByEmail(Long docId, String userEmail);

    /** True if the user identified by their Spring-Security email can edit this doc. */
    boolean canEditByEmail(Long docId, String userEmail);

    /** True if {@code username} (display name) has at least VIEW permission. */
    boolean canAccess(Long docId);

    /** True if {@code username} (display name) has EDIT or OWNER permission. */
    boolean canEdit(String username, Doc doc);

    /** True if {@code username} (display name) is the document owner. */
    boolean fullAccess(String username, Doc doc);
}
