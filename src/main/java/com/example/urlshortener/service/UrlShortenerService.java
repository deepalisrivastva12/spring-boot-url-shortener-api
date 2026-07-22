package com.example.urlshortener.service;

import com.example.urlshortener.dto.UrlRequest;
import com.example.urlshortener.dto.UrlResponse;

public interface UrlShortenerService {

    UrlResponse createShortUrl(UrlRequest request);

    /**
     * Retrieves the original URL for a short code. Each call represents an
     * "access" of the short URL, so the access counter is incremented as
     * part of this operation (this is what the redirect flow calls).
     */
    UrlResponse getOriginalUrl(String shortCode);

    UrlResponse updateShortUrl(String shortCode, UrlRequest request);

    void deleteShortUrl(String shortCode);

    /**
     * Returns current stats WITHOUT incrementing the access counter -
     * checking stats should not itself count as a visit.
     */
    UrlResponse getStats(String shortCode);
}
