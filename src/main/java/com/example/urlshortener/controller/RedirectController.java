package com.example.urlshortener.controller;

import com.example.urlshortener.dto.UrlResponse;
import com.example.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Implements the "catch all route i.e. /*" behavior shown in the
 * architecture diagram: a bare GET /{shortCode} at the root resolves the
 * short code and issues a 301 redirect straight to the original URL, the
 * same way a real short.com/abc123 link would behave in a browser.
 *
 * This lives in its own controller (separate from /shorten/**) so the two
 * concerns stay clean: UrlController is the JSON REST API, this is the
 * public-facing redirect surface.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Redirect", description = "Public redirect endpoint used by end users clicking a short link")
public class RedirectController {

    private final UrlShortenerService urlShortenerService;

    @GetMapping("/{shortCode:[a-zA-Z0-9]+}")
    @Operation(summary = "Redirect a short code to its original URL (301)")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        UrlResponse response = urlShortenerService.getOriginalUrl(shortCode);
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(response.getUrl()))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .build();
    }
}
