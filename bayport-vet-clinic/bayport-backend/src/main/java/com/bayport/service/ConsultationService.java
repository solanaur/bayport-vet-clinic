package com.bayport.service;

import com.bayport.dto.ConsultationCreateRequest;
import com.bayport.dto.ConsultationSaveResponse;
import com.bayport.dto.ConsultationServiceRequest;
import com.bayport.entity.BillingRecord;
import com.bayport.entity.Consultation;
import com.bayport.entity.Pet;
import com.bayport.entity.Procedure;
import com.bayport.exception.ResourceNotFoundException;
import com.bayport.repository.ConsultationRepository;
import com.bayport.repository.PetRepository;
import com.bayport.repository.ProcedureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@Transactional
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final ProcedureRepository procedureRepository;
    private final PetRepository petRepository;
    private final BayportService bayportService;
    private final BillingService billingService;

    public ConsultationService(ConsultationRepository consultationRepository,
                               ProcedureRepository procedureRepository,
                               PetRepository petRepository,
                               BayportService bayportService,
                               BillingService billingService) {
        this.consultationRepository = consultationRepository;
        this.procedureRepository = procedureRepository;
        this.petRepository = petRepository;
        this.bayportService = bayportService;
        this.billingService = billingService;
    }

    public ConsultationSaveResponse saveConsultation(ConsultationCreateRequest request) {
        if (request == null || request.getPetId() == null) {
            throw new IllegalArgumentException("petId is required");
        }
        if (request.getServices() == null || request.getServices().isEmpty()) {
            throw new IllegalArgumentException("Add at least one service for this consultation");
        }
        for (ConsultationServiceRequest service : request.getServices()) {
            if (service == null || !StringUtils.hasText(service.getName())) {
                throw new IllegalArgumentException("Each service needs a name");
            }
        }

        Pet pet = petRepository.findById(request.getPetId())
                .orElseThrow(() -> new ResourceNotFoundException("Pet not found with id: " + request.getPetId()));

        Consultation consultation = new Consultation();
        consultation.setPetId(pet.getId());
        consultation.setAppointmentId(request.getAppointmentId());
        consultation.setConsultDate(parseConsultDate(request.getDate()));
        consultation.setConsultTime(StringUtils.hasText(request.getTime()) ? request.getTime().trim() : null);
        consultation.setDiagnosis(trimToNull(request.getDiagnosis()));
        consultation.setNotes(trimToNull(request.getNotes()));
        consultation.setVet(trimToNull(request.getVet()));
        consultation.setCreatedAt(LocalDateTime.now());
        Consultation saved = consultationRepository.save(consultation);

        for (ConsultationServiceRequest service : request.getServices()) {
            Procedure procedure = new Procedure();
            procedure.setConsultationId(saved.getId());
            procedure.setPerformedAt(saved.getConsultDate());
            procedure.setName(service.getName().trim());
            procedure.setCategory(trimToNull(service.getCategory()));
            procedure.setLabType(trimToNull(service.getLabType()));
            procedure.setMedications(trimToNull(service.getMedications()));
            procedure.setNotes(sharedNotes(saved, service));
            procedure.setCost(service.getCost());
            procedure.setVet(saved.getVet());
            bayportService.addProcedureToPet(pet.getId(), procedure);
        }

        List<Procedure> procedures = procedureRepository.findByConsultationIdOrderByIdAsc(saved.getId());
        Pet billedPet = petRepository.findById(pet.getId()).orElse(pet);
        BillingRecord invoice = billingService.billConsultation(billedPet, saved, procedures);

        ConsultationSaveResponse response = new ConsultationSaveResponse();
        response.setConsultation(saved);
        response.setProcedures(procedures);
        response.setInvoice(invoice);
        return response;
    }

    private static LocalDate parseConsultDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Consultation date must be yyyy-MM-dd");
        }
    }

    private static String sharedNotes(Consultation consultation, ConsultationServiceRequest service) {
        if (StringUtils.hasText(service.getNotes())) {
            return service.getNotes().trim();
        }
        String diagnosis = consultation.getDiagnosis() != null ? consultation.getDiagnosis() : "N/A";
        String notes = consultation.getNotes() != null ? consultation.getNotes() : "N/A";
        return "Diagnosis: " + diagnosis + "\nClinical Notes: " + notes;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
