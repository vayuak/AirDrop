package com.AirDrop.Spherical.Services;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class AIModerationService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${moderation.openai.api-key}")
    private String openAiApiKey;

    @Data
    public static class OpenAIModerationRequest {
        private String input;
        private String model;

        public OpenAIModerationRequest(String input) {
            this.input = input;
            this.model = "omni-moderation-latest"; // 🟢 Explicitly target moderation model
        }
    }

    @Data
    public static class OpenAIModerationResponse {
        private List<Result> results;

        @Data
        public static class Result {
            private boolean flagged;
        }
    }

    public boolean isMessageSafe(String message) {
        if (message == null || message.isBlank()) return true;

        try {
            String url = "https://api.openai.com/v1/moderations";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey.trim());

            OpenAIModerationRequest requestPayload = new OpenAIModerationRequest(message);
            HttpEntity<OpenAIModerationRequest> entity = new HttpEntity<>(requestPayload, headers);

            ResponseEntity<OpenAIModerationResponse> response = restTemplate.postForEntity(
                    url, entity, OpenAIModerationResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<OpenAIModerationResponse.Result> results = response.getBody().getResults();
                if (results != null && !results.isEmpty()) {
                    boolean isFlagged = results.get(0).isFlagged();
                    log.info("🛡️ [OPENAI MODERATION] Text: '{}' | Flagged: {}", message, isFlagged);
                    return !isFlagged;
                }
            }
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("⚠️ [OPENAI RATE LIMITED 429] Quota or Rate limit exceeded. Bypassing check for text: '{}'", message);
            return true; // 🟢 Bypass check when OpenAI rate limits/locks your account
        } catch (Exception e) {
            log.error("❌ [OPENAI MODERATION ERROR]: {}", e.getMessage());
        }

        return false; // Fail-closed for severe network failures
    }
}