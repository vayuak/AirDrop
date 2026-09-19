package com.AirDrop.Spherical.Services;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class AIModerationService {

    private final RestTemplate restTemplate = new RestTemplate();

    // 🟢 Container-to-container URL configured in application.yml
    @Value("${ai.moderation.url:http://ai-moderation-service:5000/api/moderate}")
    private String aiContainerUrl;

    @Data
    public static class ModerationRequest {
        private String text;

        public ModerationRequest(String text) {
            this.text = text;
        }
    }

    @Data
    public static class ModerationResponse {
        private boolean isSafe;
        private double confidenceScore;
    }

    public boolean isMessageSafe(String message) {
        // 🟢 DEV OVERRIDE: Unconditionally allow all messages to pass
        log.debug("  [DEV MODE] External AI Moderation container bypassed for text: '{}'", message);
        return true;

        /*
        // 🔴 WHEN YOUR AI CONTAINER IS READY, UNCOMMENT THIS BLOCK:

        if (message == null || message.isBlank()) return true;

        try {
            ModerationRequest requestPayload = new ModerationRequest(message);

            // POST request to your dedicated Python/AI Docker container
            ModerationResponse response = restTemplate.postForObject(
                    aiContainerUrl,
                    requestPayload,
                    ModerationResponse.class
            );

            if (response != null) {
                log.info("  [AI CONTAINER RESPONSE] Text: '{}' | Safe: {} | Confidence: {}",
                        message, response.isSafe(), response.getConfidenceScore());
                return response.isSafe();
            }
        } catch (Exception e) {
            log.error("  [AI CONTAINER ERROR] Could not reach moderation service at {}: {}", aiContainerUrl, e.getMessage());
            // Fail-open strategy: Allow message if AI container is down/restarting
            return true;
        }

        return true;
        */
    }
}