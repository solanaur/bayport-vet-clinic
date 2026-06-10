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
import java.util.Map;

/**
 * Relays outbound email through a Netlify serverless function (Brevo key lives on Netlify only).
 */
@Slf4j
@Component
public class NetlifyEmailRelayClient {

    private final String functionUrl;
    private final String hookSecret;
    private final RestTemplate restTemplate = new RestTemplate();

    public NetlifyEmailRelayClient(
            @Value("${bayport.email.netlify-function-url:}") String functionUrl,
            @Value("${bayport.email.netlify-hook-secret:}") String hookSecret) {
        this.functionUrl = functionUrl == null ? "" : functionUrl.trim();
        this.hookSecret = hookSecret == null ? "" : hookSecret.trim();
        if (isEnabled()) {
            log.info("Netlify email relay enabled ({})", this.functionUrl);
        }
    }

    public boolean isEnabled() {
        return !functionUrl.isEmpty() && !hookSecret.isEmpty();
    }

    public void sendHtmlEmail(String to, String subject, String html, String plainText) {
        if (!isEnabled()) {
            throw new IllegalStateException("Netlify email relay is not configured");
        }
        if (to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient email is required");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Bayport-Email-Secret", hookSecret);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", to.trim());
        body.put("subject", subject == null ? "Bayport Veterinary Clinic" : subject);
        body.put("message", plainText == null ? "" : plainText);
        body.put("html", html == null ? "" : html);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    functionUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Netlify email relay returned " + response.getStatusCode());
            }
            log.info("Email relayed via Netlify to {}", to);
        } catch (HttpStatusCodeException ex) {
            String detail = ex.getResponseBodyAsString();
            log.error("Netlify email relay error {}: {}", ex.getStatusCode(), detail);
            throw new RuntimeException(
                    "Email relay failed: " + (detail != null && !detail.isBlank() ? detail : ex.getMessage()), ex);
        }
    }
}
