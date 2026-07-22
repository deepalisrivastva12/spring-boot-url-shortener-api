package com.example.urlshortener.controller;

import com.example.urlshortener.dto.UrlRequest;
import com.example.urlshortener.dto.UrlResponse;
import com.example.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shorten")
@RequiredArgsConstructor
@Tag(name = "URL Shortener", description = "Create, retrieve, update, delete short URLs and view access stats")
public class UrlController {

    private final UrlShortenerService urlShortenerService;

    @PostMapping
    @Operation(summary = "Create a new short URL")
    public ResponseEntity<UrlResponse> createShortUrl(@Valid @RequestBody UrlRequest request) {
        UrlResponse response = urlShortenerService.createShortUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortCode}")
    @Operation(summary = "Retrieve the original URL bethind a shor code")
    public ResponseEntity<UrlResponse> getOriginalUrl(@PathVariable String shortCode) {
        UrlResponse response = urlShortenerService.getOriginalUrl(shortCode);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{shortCode}")
    @Operation(summary = "Update the destination URL for an existing short code")
    public ResponseEntity<UrlResponse> updateShortUrl(
            @PathVariable String shortCode,
            @Valid @RequestBody UrlRequest request) {
        UrlResponse response = urlShortenerService.updateShortUrl(shortCode, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{shortCode}")
    @Operation(summary = "Delete an existing short URL")
    public ResponseEntity<Void> deleteShortUrl(@PathVariable String shortCode) {
        urlShortenerService.deleteShortUrl(shortCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{shortCode}/stats")
    @Operation(summary = "Get access statistics for a short URL")
    public ResponseEntity<UrlResponse> getStats(@PathVariable String shortCode) {
        UrlResponse response = urlShortenerService.getStats(shortCode);
        return ResponseEntity.ok(response);
    }
}
