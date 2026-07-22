package com.example.urlshortener.service.impl;

import com.example.urlshortener.dto.UrlRequest;
import com.example.urlshortener.dto.UrlResponse;
import com.example.urlshortener.entity.UrlMapping;
import com.example.urlshortener.exception.ShortCodeGenerationException;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.repository.UrlMappingRepository;
import com.example.urlshortener.service.UrlShortenerService;
import com.example.urlshortener.util.ShortCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Note on caching: an earlier draft cached getOriginalUrl() with
 * @Cacheable, but that is intentionally NOT done here. Every call to
 * getOriginalUrl() increments the access counter as a side effect; a
 * method-level cache would skip that increment on cache hits and silently
 * under-report access statistics, which is one of this service's explicit
 * requirements. Accuracy of stats is prioritized over shaving a single
 * indexed lookup off the hot path. (A production system that truly needed
 * to cache reads at scale would instead batch/debounce the counter
 * increments asynchronously - noted in the README as a "next steps" item.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlShortenerServiceImpl implements UrlShortenerService {

    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final UrlMappingRepository repository;
    private final ShortCodeGenerator shortCodeGenerator;

    @Override
    @Transactional
    public UrlResponse createShortUrl(UrlRequest request) {
        String shortCode = generateUniqueShortCode();

        UrlMapping mapping = UrlMapping.builder()
                .originalUrl(request.getUrl())
                .shortCode(shortCode)
                .accessCount(0L)
                .build();

        UrlMapping saved = repository.save(mapping);
        log.info("Created short URL [{}] -> [{}]", saved.getShortCode(), saved.getOriginalUrl());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public UrlResponse getOriginalUrl(String shortCode) {
        UrlMapping mapping = findByShortCodeOrThrow(shortCode);
        repository.incrementAccessCount(shortCode);
        return toResponse(mapping);
    }

    @Override
    @Transactional
    public UrlResponse updateShortUrl(String shortCode, UrlRequest request) {
        UrlMapping mapping = findByShortCodeOrThrow(shortCode);
        mapping.setOriginalUrl(request.getUrl());
        mapping.setUpdatedAt(Instant.now());
        UrlMapping updated = repository.save(mapping);
        log.info("Updated short URL [{}] -> [{}]", updated.getShortCode(), updated.getOriginalUrl());
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteShortUrl(String shortCode) {
        UrlMapping mapping = findByShortCodeOrThrow(shortCode);
        repository.delete(mapping);
        log.info("Deleted short URL [{}]", shortCode);
    }

    @Override
    @Transactional(readOnly = true)
    public UrlResponse getStats(String shortCode) {
        UrlMapping mapping = findByShortCodeOrThrow(shortCode);
        return toStatsResponse(mapping);
    }

    private UrlMapping findByShortCodeOrThrow(String shortCode) {
        return repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
    }

    /**
     * Generates a short code and retries on the rare chance of a collision.
     * With 7-character Base62 codes (~3.5 trillion combinations) collisions
     * are extremely unlikely, but handling them explicitly avoids a subtle
     * production bug rather than trusting probability alone.
     */
    private String generateUniqueShortCode() {
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = shortCodeGenerator.generate();
            if (!repository.existsByShortCode(candidate)) {
                return candidate;
            }
            log.warn("Short code collision on attempt {}: {}", attempt, candidate);
        }
        throw new ShortCodeGenerationException(
                "Failed to generate a unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private UrlResponse toResponse(UrlMapping mapping) {
        return UrlResponse.builder()
                .id(String.valueOf(mapping.getId()))
                .url(mapping.getOriginalUrl())
                .shortCode(mapping.getShortCode())
                .createdAt(mapping.getCreatedAt())
                .updatedAt(mapping.getUpdatedAt())
                .build();
    }

    private UrlResponse toStatsResponse(UrlMapping mapping) {
        return UrlResponse.builder()
                .id(String.valueOf(mapping.getId()))
                .url(mapping.getOriginalUrl())
                .shortCode(mapping.getShortCode())
                .createdAt(mapping.getCreatedAt())
                .updatedAt(mapping.getUpdatedAt())
                .accessCount(mapping.getAccessCount())
                .build();
    }
}
