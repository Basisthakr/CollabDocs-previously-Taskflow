package com.basisttha.service.impl;

import com.basisttha.engine.Crdt;
import com.basisttha.engine.CrdtManagerService;
import com.basisttha.exception.ResourceNotFoundException;
import com.basisttha.exception.UnauthorizedUserException;
import com.basisttha.exception.UserException;
import com.basisttha.mapper.DocumentChangeMapper;
import com.basisttha.mapper.DocumentMapper;
import com.basisttha.mapper.UserDocMapper;
import com.basisttha.model.Doc;
import com.basisttha.model.User;
import com.basisttha.model.UserDoc;
import com.basisttha.model.UserDocId;
import com.basisttha.repo.DocRepository;
import com.basisttha.repo.UserDocRepository;
import com.basisttha.repo.UserRepository;
import com.basisttha.request.DocTitleDto;
import com.basisttha.response.DocumentChangeDto;
import com.basisttha.response.DocumentDto;
import com.basisttha.response.UserDocDto;
import com.basisttha.security.SecurityUtil;
import com.basisttha.service.DocAuthorizationService;
import com.basisttha.service.DocService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocServiceImpl implements DocService {

    private final UserRepository         userRepository;
    private final DocRepository          docRepository;
    private final UserDocRepository      userDocRepository;
    private final DocumentMapper         documentMapper;
    private final DocumentChangeMapper   documentChangeMapper;
    private final UserDocMapper          userDocMapper;
    private final DocAuthorizationService docAuthorizationService;
    private final CrdtManagerService     crdtManagerService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User getCurrentUser() {
        String email = SecurityUtil.getCurrentUserEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    private Doc getDocById(Long docId) throws ResourceNotFoundException {
        return docRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
    }

    // -------------------------------------------------------------------------
    // Document CRUD
    // -------------------------------------------------------------------------

    @Transactional
    @Override
    public DocumentDto createDoc(DocTitleDto titleDto) {
        User user = getCurrentUser();
        Doc doc = Doc.builder()
                .owner(user)
                .title(titleDto.getTitle().trim())
                .content(new byte[0])
                .sharedWith(new ArrayList<>())
                .build();
        Doc saved = docRepository.save(doc);
        log.info("Document {} ('{}') created by {}", saved.getId(), saved.getTitle(),
                user.getDisplayName());
        return documentMapper.toDocumentDto(saved);
    }

    @Transactional
    @Override
    public Long deleteDoc(Long docId) throws ResourceNotFoundException, UnauthorizedUserException {
        Doc  doc      = getDocById(docId);
        User user     = getCurrentUser();
        String username = user.getDisplayName();

        if (!docAuthorizationService.fullAccess(username, doc)) {
            log.warn("User {} attempted to delete document {} without owner rights", username, docId);
            throw new UnauthorizedUserException("Only the document owner can delete it.");
        }

        docRepository.deleteById(docId);
        log.info("Document {} deleted by {}", docId, username);
        return docId;
    }

    @Transactional
    @Override
    public String updateDocTitle(Long docId, DocTitleDto titleDto)
            throws ResourceNotFoundException, UnauthorizedUserException {
        Doc  doc      = getDocById(docId);
        String username = getCurrentUser().getDisplayName();

        if (!docAuthorizationService.canEdit(username, doc)) {
            throw new UnauthorizedUserException("You do not have permission to rename this document.");
        }

        doc.setTitle(titleDto.getTitle().trim());
        docRepository.save(doc);
        log.info("Document {} renamed to '{}' by {}", docId, doc.getTitle(), username);
        return "Title updated successfully";
    }

    // -------------------------------------------------------------------------
    // Sharing / permission management
    // -------------------------------------------------------------------------

    @Transactional
    @Override
    public UserDocDto addUser(Long docId, UserDocDto userDocDto)
            throws ResourceNotFoundException, UnauthorizedUserException, UserException {
        Doc  doc      = getDocById(docId);
        String callerUsername = getCurrentUser().getDisplayName();

        if (!docAuthorizationService.canEdit(callerUsername, doc)) {
            throw new UnauthorizedUserException("You do not have permission to share this document.");
        }

        User targetUser = userRepository.findByUsername(userDocDto.getUsername())
                .orElseThrow(() -> new UserException(
                        "User not found: " + userDocDto.getUsername()));

        UserDocId id = UserDocId.builder()
                .docId(docId)
                .username(targetUser.getDisplayName())
                .build();

        UserDoc userDoc = UserDoc.builder()
                .userDocId(id)
                .user(targetUser)
                .doc(doc)
                .permission(userDocDto.getPermission())
                .build();

        userDocRepository.save(userDoc);
        log.info("Document {} shared with {} ({}) by {}",
                docId, targetUser.getDisplayName(), userDocDto.getPermission(), callerUsername);
        return userDocDto;
    }

    @Transactional
    @Override
    public List<UserDocDto> getSharedUsers(Long docId) throws ResourceNotFoundException {
        Doc doc = getDocById(docId);
        return doc.getSharedWith().stream()
                .map(userDocMapper::toUserDocDto)
                .toList();
    }

    @Transactional
    @Override
    public String removeUser(Long docId, UserDocDto userDocDto)
            throws ResourceNotFoundException, UnauthorizedUserException {
        Doc  doc      = getDocById(docId);
        String username = getCurrentUser().getDisplayName();

        // Only the owner can revoke access.
        if (!docAuthorizationService.fullAccess(username, doc)) {
            throw new UnauthorizedUserException("Only the document owner can remove users.");
        }

        int removed = userDocRepository.deleteUserDocBy(userDocDto.getUsername(), docId, username);
        log.info("User {} removed from document {} by {}", userDocDto.getUsername(), docId, username);
        return removed != 0 ? "User removed successfully." : "User not found on this document.";
    }

    @Transactional
    @Override
    public String updatePermission(Long docId, UserDocDto userDocDto)
            throws ResourceNotFoundException, UnauthorizedUserException {
        Doc  doc      = getDocById(docId);
        String username = getCurrentUser().getDisplayName();

        // Permission changes are owner-only — not just any editor.
        if (!docAuthorizationService.fullAccess(username, doc)) {
            throw new UnauthorizedUserException("Only the document owner can change permissions.");
        }

        int updated = userDocRepository.updateUserDocBy(
                userDocDto.getUsername(), docId, username, userDocDto.getPermission());

        if (updated == 0) {
            throw new UnauthorizedUserException(
                    "Permission update failed — user may not have access to this document.");
        }

        log.info("Permission for user {} on document {} updated to {} by {}",
                userDocDto.getUsername(), docId, userDocDto.getPermission(), username);
        return "Permission updated successfully.";
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @Override
    public List<DocumentDto> getAllDocs() {
        String username = getCurrentUser().getDisplayName();
        return docRepository.findByUsername(username).stream()
                .map(documentMapper::toDocumentDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<DocumentChangeDto> getDocChanges(Long docId)
            throws ResourceNotFoundException, UnauthorizedUserException {
        // Authorization: the caller must have at least VIEW permission.
        if (!docAuthorizationService.canAccess(docId)) {
            throw new UnauthorizedUserException(
                    "You do not have access to document " + docId);
        }

        Crdt crdt = crdtManagerService.getCrdt(docId);
        if (crdt == null) {
            // Document is not currently open — load a read-only snapshot from DB.
            Doc doc = getDocById(docId);
            crdt = new Crdt();
            if (doc.getContent() != null && doc.getContent().length > 0) {
                // CrdtManagerService handles deserialization; create a temporary Crdt.
                crdtManagerService.createCrdt(docId);
                crdt = crdtManagerService.getCrdt(docId);
            }
        }

        if (crdt == null) return List.of();

        return crdt.getItems().stream()
                .map(documentChangeMapper::toDocumentChangeDto)
                .toList();
    }

    @Override
    public void saveDoc(Long docId) throws ResourceNotFoundException, UnauthorizedUserException {
        crdtManagerService.saveCrdt(docId);
    }

    @Transactional(readOnly = true)
    @Override
    public DocumentDto getDoc(Long docId)
            throws ResourceNotFoundException, UnauthorizedUserException {
        Doc doc = getDocById(docId);
        String username = getCurrentUser().getDisplayName();

        // Both owner and shared users may read document metadata.
        if (!docAuthorizationService.canEdit(username, doc)
                && !docAuthorizationService.fullAccess(username, doc)) {
            // Fall through to canAccess for VIEW-only users.
            if (!docAuthorizationService.canAccess(docId)) {
                throw new UnauthorizedUserException(
                        "You do not have access to document " + docId);
            }
        }

        return documentMapper.toDocumentDto(doc);
    }
}
