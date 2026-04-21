package com.basisttha.model;

import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@Embeddable
public class UserDocId {

    @SuppressWarnings("unused")
    private Long docId;
    @SuppressWarnings("unused")
    private String username;

}
