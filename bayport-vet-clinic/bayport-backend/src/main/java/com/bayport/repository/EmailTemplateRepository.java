package com.bayport.repository;

import com.bayport.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    List<EmailTemplate> findByCategoryIgnoreCase(String category);
}
