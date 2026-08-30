package com.AirDrop.Spherical.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AIModerationService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${moderation.openai.api-key}")
    private String openAiApiKey;

    /**
     * Checks text safety using OpenAI's free Moderation endpoint.
     * Returns true if safe, false if flagged for hate, harassment, violence, or profanity.
     */
    public boolean isMessageSafe(String message) {
        if (message == null || message.isBlank()) return true;

        boolean isSafe = checkOpenAI(message);

        log.info("🛡️ [OPENAI MODERATION] Status: {} for text preview: '{}'",
                isSafe ? "PASSED" : "BLOCKED",
                message.length() > 20 ? message.substring(0, 20) + "..." : message);

        return isSafe;
    }

    private boolean checkOpenAI(String text) {
        try {
            String url = "https://api.openai.com/v1/moderations";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            Map<String, Object> body = Map.of("input", text);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");
                if (results != null && !results.isEmpty()) {
                    Boolean flagged = (Boolean) results.get(0).get("flagged");
                    return !Boolean.TRUE.equals(flagged); // Returns true if message is SAFE
                }
            }
        } catch (Exception e) {
            log.error("❌ [OPENAI MODERATION ERROR]:", e);
        }
        return false; // Fail-closed on API error to prevent unsafe leakage
    }
}