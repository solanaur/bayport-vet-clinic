package com.bayport.web;

import com.bayport.dto.ConsultationCreateRequest;
import com.bayport.dto.ConsultationSaveResponse;
import com.bayport.security.RecordAccessService;
import com.bayport.service.ConsultationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consultations")
public class ConsultationController {

    private final ConsultationService consultationService;
    private final RecordAccessService recordAccessService;

    public ConsultationController(ConsultationService consultationService,
                                  RecordAccessService recordAccessService) {
        this.consultationService = consultationService;
        this.recordAccessService = recordAccessService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','FRONT_OFFICE','RECEPTIONIST','PHARMACIST','VET','VETERINARIAN','STAFF')")
    public ConsultationSaveResponse create(@RequestBody ConsultationCreateRequest request) {
        if (request == null || request.getPetId() == null) {
            throw new IllegalArgumentException("petId is required");
        }
        recordAccessService.requirePetAccess(request.getPetId());
        return consultationService.saveConsultation(request);
    }
}
