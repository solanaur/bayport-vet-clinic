package com.bayport.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends email via Resend HTTP API (port 443). Required on Render free tier — SMTP ports 465/587 are blocked.
 */
@Slf4j
@Component
public class ResendEmailClient {

    private final String apiKey;
    private final String from;
    private final RestTemplate restTemplate = new RestTemplate();

    public ResendEmailClient(
            @Value("${resend.api-key:}") String apiKey,
            @Value("${resend.from:Bayport Veterinary Clinic <onboarding@resend.dev>}") String from) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from == null || from.isBlank()
                ? "Bayport Veterinary Clinic <onboarding@resend.dev>"
                : from.trim();
        if (isEnabled()) {
            log.info("Resend email delivery enabled (from={})", this.from);
        }
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty();
    }

    public String getFrom() {
        return from;
    }

    public void sendHtmlEmail(String to, String subject, String html, String plainText) {
        if (!isEnabled()) {
            throw new IllegalStateException("RESEND_API_KEY is not set");
        }
        if (to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient email is required");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", List.of(to.trim()));
        body.put("subject", subject == null ? "Bayport Veterinary Clinic" : subject);
        body.put("html", html == null ? "" : html);
        if (plainText != null && !plainText.isBlank()) {
            body.put("text", plainText.trim());
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.resend.com/emails",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Resend API returned " + response.getStatusCode());
            }
            log.info("Resend email sent to {}", to);
        } catch (HttpStatusCodeException ex) {
            String detail = ex.getResponseBodyAsString();
            log.error("Resend API error {}: {}", ex.getStatusCode(), detail);
            throw new RuntimeException(
                    "Resend failed: " + (detail != null && !detail.isBlank() ? detail : ex.getMessage()), ex);
        }
    }
}
