package com.basisttha.request;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CursorDto {
    @SuppressWarnings("unused")
    private String username;
    @SuppressWarnings("unused")
    private int index;
    private int length;
}
