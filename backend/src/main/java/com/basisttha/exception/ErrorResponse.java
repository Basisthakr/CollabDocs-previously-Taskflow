package com.basisttha.exception;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ErrorResponse (
        LocalDateTime timestamp,
        String errorMessage,
        String endpoint
) {
}
