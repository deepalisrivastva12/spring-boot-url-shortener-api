package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UrlRequest {

    @NotBlank(message = "url must not be blank")
    @Pattern(
            regexp = "^(https?)://[\\w.-]+(:\\d+)?([/?#][^\\s]*)?$",
            message = "url must be a valid http/https URL"
    )
    private String url;
}
