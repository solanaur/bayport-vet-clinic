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
 * Sends email via Brevo HTTP API (port 443). Works on Render free tier.
 * After verifying your clinic sender email in Brevo, you can deliver to any recipient.
 */
@Slf4j
@Component
public class BrevoEmailClient {

    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final RestTemplate restTemplate = new RestTemplate();

    public BrevoEmailClient(
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${brevo.from.email:}") String fromEmail,
            @Value("${brevo.from.name:Bayport Veterinary Clinic}") String fromName) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.fromName = fromName == null || fromName.isBlank()
                ? "Bayport Veterinary Clinic"
                : fromName.trim();
        if (isEnabled()) {
            log.info("Brevo email delivery enabled (from={} <{}>)", this.fromName, this.fromEmail);
        }
    }

    public boolean isEnabled() {
        return !apiKey.isEmpty() && !fromEmail.isEmpty();
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void sendHtmlEmail(String to, String subject, String html, String plainText) {
        if (!isEnabled()) {
            throw new IllegalStateException("BREVO_API_KEY and BREVO_FROM_EMAIL are not set");
        }
        if (to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient email is required");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("api-key", apiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("name", fromName);
        sender.put("email", fromEmail);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", List.of(Map.of("email", to.trim())));
        body.put("subject", subject == null ? "Bayport Veterinary Clinic" : subject);
        body.put("htmlContent", html == null ? "" : html);
        if (plainText != null && !plainText.isBlank()) {
            body.put("textContent", plainText.trim());
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.brevo.com/v3/smtp/email",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Brevo API returned " + response.getStatusCode());
            }
            log.info("Brevo email sent to {}", to);
        } catch (HttpStatusCodeException ex) {
            String detail = ex.getResponseBodyAsString();
            log.error("Brevo API error {}: {}", ex.getStatusCode(), detail);
            throw new RuntimeException(
                    "Brevo failed: " + (detail != null && !detail.isBlank() ? detail : ex.getMessage()), ex);
        }
    }
}
