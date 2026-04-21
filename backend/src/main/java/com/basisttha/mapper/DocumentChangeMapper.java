package com.basisttha.mapper;

import com.basisttha.engine.Item;
import com.basisttha.response.DocumentChangeDto;
import org.springframework.stereotype.Service;

@Service
public class DocumentChangeMapper {

    public DocumentChangeDto toDocumentChangeDto(Item item) {
        return DocumentChangeDto.builder()
                .id(item.getId())
                // getLeft()/getRight() return null for the head and tail items —
                // guard here so we never throw a NullPointerException.
                .left(item.getLeft()  != null ? item.getLeft().getId()  : null)
                .right(item.getRight() != null ? item.getRight().getId() : null)
                .content(item.getContent())
                .operation(item.getOperation())
                .isBold(item.isBold())
                .isItalic(item.isItalic())
                .isUnderline(item.isUnderline())
                .isStrike(item.isStrike())
                .header(item.getHeader())
                .align(item.getAlign())
                .list(item.getList())
                .indent(item.getIndent())
                .color(item.getColor())
                .background(item.getBackground())
                .isDeleted(item.isDeleted())
                .build();
    }
}
