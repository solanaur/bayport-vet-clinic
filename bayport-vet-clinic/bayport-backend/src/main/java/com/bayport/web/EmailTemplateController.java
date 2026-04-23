package com.bayport.web;

import com.bayport.entity.EmailTemplate;
import com.bayport.repository.EmailTemplateRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class EmailTemplateController {

    private final EmailTemplateRepository repo;

    public EmailTemplateController(EmailTemplateRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/pet")
    public List<EmailTemplate> petTemplates() {
        return repo.findByCategoryIgnoreCase("PET");
    }

    @GetMapping("/general")
    public List<EmailTemplate> generalTemplates() {
        return repo.findByCategoryIgnoreCase("GENERAL");
    }
}
