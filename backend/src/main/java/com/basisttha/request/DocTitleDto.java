package com.basisttha.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocTitleDto {

    @NotBlank(message = "Title must not be blank")
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    private String title;
}
