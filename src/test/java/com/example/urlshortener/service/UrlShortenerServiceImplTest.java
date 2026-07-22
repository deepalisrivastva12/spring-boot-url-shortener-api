package com.example.urlshortener.service;

import com.example.urlshortener.dto.UrlRequest;
import com.example.urlshortener.dto.UrlResponse;
import com.example.urlshortener.entity.UrlMapping;
import com.example.urlshortener.exception.ShortCodeGenerationException;
import com.example.urlshortener.exception.UrlNotFoundException;
import com.example.urlshortener.repository.UrlMappingRepository;
import com.example.urlshortener.service.impl.UrlShortenerServiceImpl;
import com.example.urlshortener.util.ShortCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceImplTest {

    @Mock
    private UrlMappingRepository repository;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @InjectMocks
    private UrlShortenerServiceImpl service;

    private UrlMapping existingMapping;

    @BeforeEach
    void setUp() {
        existingMapping = UrlMapping.builder()
                .id(1L)
                .originalUrl("https://www.example.com/some/long/url")
                .shortCode("abc123")
                .createdAt(Instant.parse("2021-09-01T12:00:00Z"))
                .updatedAt(Instant.parse("2021-09-01T12:00:00Z"))
                .accessCount(0L)
                .build();
    }

    @Test
    void createShortUrl_savesMappingWithGeneratedCode() {
        when(shortCodeGenerator.generate()).thenReturn("abc123");
        when(repository.existsByShortCode("abc123")).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping m = invocation.getArgument(0);
            m.setId(1L);
            m.setCreatedAt(Instant.now());
            m.setUpdatedAt(Instant.now());
            return m;
        });

        UrlRequest request = new UrlRequest("https://www.example.com/some/long/url");
        UrlResponse response = service.createShortUrl(request);

        assertThat(response.getShortCode()).isEqualTo("abc123");
        assertThat(response.getUrl()).isEqualTo("https://www.example.com/some/long/url");
        verify(repository).save(any(UrlMapping.class));
    }

    @Test
    void createShortUrl_retriesOnCollision() {
        when(shortCodeGenerator.generate()).thenReturn("dup001", "dup001", "fresh1");
        when(repository.existsByShortCode("dup001")).thenReturn(true);
        when(repository.existsByShortCode("fresh1")).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UrlRequest request = new UrlRequest("https://www.example.com/x");
        UrlResponse response = service.createShortUrl(request);

        assertThat(response.getShortCode()).isEqualTo("fresh1");
        verify(shortCodeGenerator, times(3)).generate();
    }

    @Test
    void createShortUrl_throwsAfterMaxAttempts() {
        when(shortCodeGenerator.generate()).thenReturn("dup001");
        when(repository.existsByShortCode("dup001")).thenReturn(true);

        UrlRequest request = new UrlRequest("https://www.example.com/x");

        assertThatThrownBy(() -> service.createShortUrl(request))
                .isInstanceOf(ShortCodeGenerationException.class);
    }

    @Test
    void getOriginalUrl_returnsMappingAndIncrementsAccessCount() {
        when(repository.findByShortCode("abc123")).thenReturn(Optional.of(existingMapping));

        UrlResponse response = service.getOriginalUrl("abc123");

        assertThat(response.getUrl()).isEqualTo(existingMapping.getOriginalUrl());
        verify(repository).incrementAccessCount("abc123");
    }

    @Test
    void getOriginalUrl_throwsWhenNotFound() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOriginalUrl("missing"))
                .isInstanceOf(UrlNotFoundException.class);

        verify(repository, never()).incrementAccessCount(anyString());
    }

    @Test
    void updateShortUrl_updatesOriginalUrlAndTimestamp() {
        when(repository.findByShortCode("abc123")).thenReturn(Optional.of(existingMapping));
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UrlRequest request = new UrlRequest("https://www.example.com/updated");
        UrlResponse response = service.updateShortUrl("abc123", request);

        assertThat(response.getUrl()).isEqualTo("https://www.example.com/updated");
        assertThat(existingMapping.getUpdatedAt()).isAfterOrEqualTo(existingMapping.getCreatedAt());
    }

    @Test
    void updateShortUrl_throwsWhenNotFound() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());
        UrlRequest request = new UrlRequest("https://www.example.com/updated");

        assertThatThrownBy(() -> service.updateShortUrl("missing", request))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void deleteShortUrl_deletesExistingMapping() {
        when(repository.findByShortCode("abc123")).thenReturn(Optional.of(existingMapping));

        service.deleteShortUrl("abc123");

        verify(repository).delete(existingMapping);
    }

    @Test
    void deleteShortUrl_throwsWhenNotFound() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteShortUrl("missing"))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void getStats_returnsAccessCount() {
        existingMapping.setAccessCount(42L);
        when(repository.findByShortCode("abc123")).thenReturn(Optional.of(existingMapping));

        UrlResponse response = service.getStats("abc123");

        assertThat(response.getAccessCount()).isEqualTo(42L);
    }

    @Test
    void getStats_throwsWhenNotFound() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStats("missing"))
                .isInstanceOf(UrlNotFoundException.class);
    }
}
