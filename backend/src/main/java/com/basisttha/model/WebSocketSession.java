package com.basisttha.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WebSocketSession {
    @SuppressWarnings("unused")
    private String displayName;
    @SuppressWarnings("unused")
    private String docId;
}
