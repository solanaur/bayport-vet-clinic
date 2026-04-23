package com.bayport.web;

import com.bayport.service.EmailService;
import com.bayport.repository.EmailTemplateRepository;
import com.bayport.entity.EmailTemplate;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private final EmailService emailService;
    private final EmailTemplateRepository templates;

    public EmailController(EmailService emailService, EmailTemplateRepository templates) {
        this.emailService = emailService;
        this.templates = templates;
    }

    // Send custom email
    @PostMapping("/send")
    public String sendEmail(@RequestBody EmailRequest req) {
        emailService.send(req.to, req.subject, req.message);
        return "OK";
    }

    // Load templates by CATEGORY
    @GetMapping("/templates/{category}")
    public List<EmailTemplate> getTemplates(@PathVariable String category) {
        return templates.findByCategoryIgnoreCase(category);
    }

    // Simple request wrapper
    public static class EmailRequest {
        public String to;
        public String subject;
        public String message;
    }
}
