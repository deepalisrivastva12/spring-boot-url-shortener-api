package com.example.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UrlResponse {

    private String id;
    private String url;
    private String shortCode;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Only populated by the /stats endpoint; omitted everywhere else
     * thanks to @JsonInclude(NON_NULL).
     */
    private Long accessCount;
}
