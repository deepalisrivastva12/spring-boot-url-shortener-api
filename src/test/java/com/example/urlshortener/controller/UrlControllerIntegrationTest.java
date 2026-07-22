package com.example.urlshortener.controller;

import com.example.urlshortener.repository.UrlMappingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests that exercise the full stack (controller -> service ->
 * repository -> H2) for every endpoint in the spec, including the error
 * paths (400 / 404).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UrlControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlMappingRepository repository;

    @BeforeEach
    void cleanDb() {
        repository.deleteAll();
    }

    @Test
    void fullLifecycle_createRetrieveUpdateStatsDelete() throws Exception {
        // 1. Create
        String createBody = objectMapper.writeValueAsString(new UrlPayload("https://www.example.com/some/long/url"));

        String responseJson = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.shortCode").isString())
                .andExpect(jsonPath("$.url").value("https://www.example.com/some/long/url"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(responseJson);
        String shortCode = created.get("shortCode").asText();

        // 2. Retrieve
        mockMvc.perform(get("/shorten/{shortCode}", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://www.example.com/some/long/url"));

        // 3. Update
        String updateBody = objectMapper.writeValueAsString(new UrlPayload("https://www.example.com/some/updated/url"));
        mockMvc.perform(put("/shorten/{shortCode}", shortCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://www.example.com/some/updated/url"));

        // 4. Stats should reflect the one GET above
        mockMvc.perform(get("/shorten/{shortCode}/stats", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessCount").value(1));

        // 5. Delete
        mockMvc.perform(delete("/shorten/{shortCode}", shortCode))
                .andExpect(status().isNoContent());

        // 6. Now gone
        mockMvc.perform(get("/shorten/{shortCode}", shortCode))
                .andExpect(status().isNotFound());
    }

    @Test
    void createShortUrl_rejectsInvalidUrl() throws Exception {
        String body = objectMapper.writeValueAsString(new UrlPayload("not-a-valid-url"));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages", not(empty())));
    }

    @Test
    void createShortUrl_rejectsBlankUrl() throws Exception {
        String body = objectMapper.writeValueAsString(new UrlPayload(""));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOriginalUrl_returns404ForUnknownCode() throws Exception {
        mockMvc.perform(get("/shorten/{shortCode}", "doesNotExist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateShortUrl_returns404ForUnknownCode() throws Exception {
        String body = objectMapper.writeValueAsString(new UrlPayload("https://www.example.com/x"));

        mockMvc.perform(put("/shorten/{shortCode}", "doesNotExist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteShortUrl_returns404ForUnknownCode() throws Exception {
        mockMvc.perform(delete("/shorten/{shortCode}", "doesNotExist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStats_returns404ForUnknownCode() throws Exception {
        mockMvc.perform(get("/shorten/{shortCode}/stats", "doesNotExist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void redirect_returns301ToOriginalUrl() throws Exception {
        String createBody = objectMapper.writeValueAsString(new UrlPayload("https://www.example.com/redirect-target"));
        String responseJson = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String shortCode = objectMapper.readTree(responseJson).get("shortCode").asText();

        mockMvc.perform(get("/{shortCode}", shortCode))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://www.example.com/redirect-target"));
    }

    // Small local record just for building JSON request bodies in these tests.
    private record UrlPayload(String url) {}
}
