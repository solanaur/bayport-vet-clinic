package com.bayport.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Outbound SMS for MFA codes and clinic announcements.
 * Stays idle until API credentials are set. Email still works without SMS.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    public record SmsResult(boolean accepted, String detail) {}

    private final boolean enabled;
    private final String provider;
    private final String apiKey;
    private final String apiSecret;
    private final String sender;
    private final String accountSid;
    private final String endpoint;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public SmsService(
            @Value("${bayport.sms.enabled:false}") boolean enabled,
            @Value("${bayport.sms.provider:semaphore}") String provider,
            @Value("${bayport.sms.api-key:}") String apiKey,
            @Value("${bayport.sms.api-secret:}") String apiSecret,
            @Value("${bayport.sms.sender:Bayport}") String sender,
            @Value("${bayport.sms.account-sid:}") String accountSid,
            @Value("${bayport.sms.endpoint:}") String endpoint) {
        this.enabled = enabled;
        this.provider = provider == null ? "semaphore" : provider.trim().toLowerCase();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.sender = sender == null || sender.isBlank() ? "Bayport" : sender.trim();
        this.accountSid = accountSid == null ? "" : accountSid.trim();
        this.endpoint = endpoint == null ? "" : endpoint.trim();
    }

    public boolean isConfigured() {
        if (!enabled || apiKey.isBlank()) {
            return false;
        }
        return switch (provider) {
            case "twilio" -> !accountSid.isBlank() && !apiSecret.isBlank() && !sender.isBlank();
            case "http", "generic" -> !endpoint.isBlank();
            default -> true;
        };
    }

    public String describeProvider() {
        if (!isConfigured()) {
            return "not-configured";
        }
        return provider;
    }

    public SmsResult sendVerificationCode(String phone, String code) {
        String text = "Bayport Veterinary Clinic verification code: " + code
                + ". It expires in 5 minutes. Do not share this code.";
        return send(phone, text);
    }

    public SmsResult sendAnnouncement(String phone, String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            return new SmsResult(false, "Message is empty");
        }
        if (!text.toLowerCase().contains("bayport")) {
            text = "Bayport Veterinary Clinic: " + text;
        }
        return send(phone, text);
    }

    public SmsResult send(String phone, String message) {
        if (!isConfigured()) {
            return new SmsResult(false, "SMS is not configured");
        }
        String normalized = normalizePhone(phone);
        if (normalized.length() < 10) {
            return new SmsResult(false, "Invalid mobile number");
        }
        try {
            HttpResponse<String> response = switch (provider) {
                case "twilio" -> postTwilio(normalized, message);
                case "http", "generic" -> postGeneric(normalized, message);
                default -> postSemaphore(normalized, message);
            };
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!ok) {
                log.warn("SMS provider {} rejected {} (HTTP {})", provider, mask(normalized), response.statusCode());
                return new SmsResult(false, "SMS provider returned HTTP " + response.statusCode());
            }
            log.info("SMS accepted by {} for {}", provider, mask(normalized));
            return new SmsResult(true, "sent");
        } catch (Exception e) {
            log.warn("SMS send failed for {}: {}", mask(normalized), e.getMessage());
            return new SmsResult(false, e.getMessage() == null ? "SMS send failed" : e.getMessage());
        }
    }

    /** Digits only, Philippine mobiles stored as 09… become 63…. */
    public static String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("0") && digits.length() == 11) {
            digits = "63" + digits.substring(1);
        } else if (digits.startsWith("9") && digits.length() == 10) {
            digits = "63" + digits;
        }
        return digits;
    }

    private HttpResponse<String> postSemaphore(String number, String message) throws Exception {
        String body = form(
                "apikey", apiKey,
                "number", number,
                "message", message,
                "sendername", sender);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.semaphore.co/api/v4/messages"))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postTwilio(String number, String message) throws Exception {
        String to = number.startsWith("+") ? number : "+" + number;
        String from = sender.startsWith("+") || sender.startsWith("09") ? sender : sender;
        String body = form("To", to, "From", from, "Body", message);
        String basic = Base64.getEncoder().encodeToString((accountSid + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + url(accountSid) + "/Messages.json"))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postGeneric(String number, String message) throws Exception {
        String json = "{\"to\":\"" + escapeJson(number) + "\",\"message\":\"" + escapeJson(message) + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String form(String... pairs) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (out.length() > 0) {
                out.append('&');
            }
            out.append(url(pairs[i])).append('=').append(url(pairs[i + 1]));
        }
        return out.toString();
    }

    private static String url(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static String mask(String number) {
        if (number.length() <= 4) {
            return "****";
        }
        return "…" + number.substring(number.length() - 4);
    }
}
