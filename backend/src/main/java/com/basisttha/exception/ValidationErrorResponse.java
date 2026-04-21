package com.basisttha.exception;

import java.util.HashMap;

public record ValidationErrorResponse(
        HashMap<String, String> error
) {
}
