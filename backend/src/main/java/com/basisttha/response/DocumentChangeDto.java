package com.basisttha.response;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonAutoDetect(
    fieldVisibility    = JsonAutoDetect.Visibility.ANY,
    getterVisibility   = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility   = JsonAutoDetect.Visibility.NONE
)
public class DocumentChangeDto {

    private String id;
    private String left;
    private String right;
    private String content;
    private String operation;

    private boolean isDeleted;
    private boolean isBold;
    private boolean isItalic;
    private boolean isUnderline;
    private boolean isStrike;
    private Integer header;
    private String align;
    private String list;
    private int indent;
    private String color;
    private String background;
    private String link;

}
