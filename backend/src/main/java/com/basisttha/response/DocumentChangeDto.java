package com.basisttha.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentChangeDto {

    private String id;
    private String left;
    private String right;
    private String content;
    private String operation;

    @JsonProperty("isDeleted")
    private boolean isDeleted;
    @JsonProperty("isBold")
    private boolean isBold;
    @JsonProperty("isItalic")
    private boolean isItalic;
    @JsonProperty("isUnderline")
    private boolean isUnderline;
    @JsonProperty("isStrike")
    private boolean isStrike;
    private Integer header;
    private String align;
    private String list;
    private int indent;
    private String color;
    private String background;

}
