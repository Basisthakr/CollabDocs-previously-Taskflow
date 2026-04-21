package com.basisttha.mapper;

import com.basisttha.model.Doc;
import com.basisttha.response.DocumentDto;
import com.basisttha.response.UserDocDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentMapper {

    private final UserDocMapper userDocMapper;

    public DocumentDto toDocumentDto(Doc savedDoc) {
        List<UserDocDto> userDocDtos = savedDoc.getSharedWith().stream()
                .map(userDocMapper::toUserDocDto)
                .toList();

        return DocumentDto.builder()
                .id(savedDoc.getId())
                .title(savedDoc.getTitle())
                .owner(savedDoc.getOwner().getDisplayName())
                .content(null)
                .sharedWith(userDocDtos)
                .build();
    }
}
