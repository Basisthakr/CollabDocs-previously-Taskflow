package com.basisttha.service;

import com.basisttha.exception.ResourceNotFoundException;
import com.basisttha.exception.UnauthorizedUserException;
import com.basisttha.exception.UserException;
import com.basisttha.request.DocTitleDto;
import com.basisttha.response.DocumentChangeDto;
import com.basisttha.response.DocumentDto;
import com.basisttha.response.UserDocDto;

import java.util.List;

public interface DocService {

    DocumentDto createDoc(DocTitleDto titleDto);
    Long deleteDoc(Long docId) throws ResourceNotFoundException, UnauthorizedUserException;
    String updateDocTitle(Long docId, DocTitleDto titleDto) throws ResourceNotFoundException, UnauthorizedUserException;
    UserDocDto addUser(Long docId, UserDocDto userDocDto) throws ResourceNotFoundException, UnauthorizedUserException, UserException;
    List<UserDocDto> getSharedUsers(Long docId) throws ResourceNotFoundException;
    String removeUser(Long docId, UserDocDto userDocDto) throws ResourceNotFoundException, UnauthorizedUserException;
    String updatePermission(Long id, UserDocDto userDocDto) throws ResourceNotFoundException, UnauthorizedUserException;
    List<DocumentDto> getAllDocs();
    List<DocumentChangeDto> getDocChanges(Long docId) throws ResourceNotFoundException, UnauthorizedUserException;
    DocumentDto getDoc(Long docId) throws ResourceNotFoundException, UnauthorizedUserException;
    void saveDoc(Long docId);
}
