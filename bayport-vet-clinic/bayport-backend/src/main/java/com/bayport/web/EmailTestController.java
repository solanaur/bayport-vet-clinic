package com.bayport.web;

import com.bayport.service.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Test controller for email functionality.
 * 
 * Usage:
 * POST /api/notifications/test-email
 * Body: { "to": "recipient@example.com", "subject": "Test", "message": "Test message" }
 */
@RestController
@RequestMapping("/api/notifications")
public class EmailTestController {

    private final EmailService emailService;

    public EmailTestController(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Test endpoint for sending a simple email.
     * 
     * @param request Email request with to, subject, and message
     * @return Success or error response
     */
    @PostMapping("/test-email")
    public ResponseEntity<ApiResponse<String>> testEmail(@Valid @RequestBody TestEmailRequest request) {
        try {
            emailService.sendSimpleEmail(request.to, request.subject, request.message);
            return ResponseEntity.ok(new ApiResponse<>(true, "Email sent successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to send email: " + e.getMessage(), null));
        }
    }

    /**
     * Request DTO for test email endpoint.
     */
    public static class TestEmailRequest {
        @NotBlank(message = "Recipient email is required")
        @Email(message = "Invalid email format")
        private String to;

        @NotBlank(message = "Subject is required")
        private String subject;

        @NotBlank(message = "Message is required")
        private String message;

        // Getters and setters
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * Standard API response wrapper.
     */
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public ApiResponse() {}

        public ApiResponse(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
    }
}

