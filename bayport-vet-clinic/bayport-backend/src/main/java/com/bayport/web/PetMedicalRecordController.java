package com.bayport.web;

import com.bayport.dto.VaccinationScheduleItem;
import com.bayport.entity.PetMedicalRecord;
import com.bayport.service.PdfService;
import com.bayport.service.PetMedicalRecordService;
import com.bayport.service.PetMedicalRecordService.MedicalHistoryBundle;
import com.bayport.repository.PrescriptionRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/pets/{petId}/medical-records")
public class PetMedicalRecordController {

    private final PetMedicalRecordService medicalRecordService;
    private final PdfService pdfService;
    private final PrescriptionRepository prescriptionRepository;
    private final com.bayport.security.RecordAccessService recordAccessService;

    public PetMedicalRecordController(
            PetMedicalRecordService medicalRecordService,
            PdfService pdfService,
            PrescriptionRepository prescriptionRepository,
            com.bayport.security.RecordAccessService recordAccessService) {
        this.medicalRecordService = medicalRecordService;
        this.pdfService = pdfService;
        this.prescriptionRepository = prescriptionRepository;
        this.recordAccessService = recordAccessService;
    }

    @GetMapping
    public List<PetMedicalRecord> list(@PathVariable Long petId) {
        recordAccessService.requirePetAccess(petId);
        return medicalRecordService.listByPet(petId);
    }

    @GetMapping("/{recordId}")
    public PetMedicalRecord get(@PathVariable Long petId, @PathVariable Long recordId) {
        recordAccessService.requirePetAccess(petId);
        return medicalRecordService.getById(petId, recordId);
    }

    @PostMapping
    public PetMedicalRecord create(@PathVariable Long petId, @RequestBody PetMedicalRecord record) {
        recordAccessService.requirePetAccess(petId);
        return medicalRecordService.create(petId, record);
    }

    @PutMapping("/{recordId}")
    public PetMedicalRecord update(
            @PathVariable Long petId,
            @PathVariable Long recordId,
            @RequestBody PetMedicalRecord record) {
        recordAccessService.requirePetAccess(petId);
        return medicalRecordService.update(petId, recordId, record);
    }

    @DeleteMapping("/{recordId}")
    public ResponseEntity<Void> delete(@PathVariable Long petId, @PathVariable Long recordId) {
        recordAccessService.requirePetAccess(petId);
        medicalRecordService.delete(petId, recordId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{recordId}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PetMedicalRecord uploadAttachment(
            @PathVariable Long petId,
            @PathVariable Long recordId,
            @RequestParam("file") MultipartFile file) throws Exception {
        recordAccessService.requirePetAccess(petId);
        return medicalRecordService.attachFile(petId, recordId, file);
    }
}

@RestController
@RequestMapping("/api/pets")
class PetMedicalHistoryController {

    private final PetMedicalRecordService medicalRecordService;
    private final PdfService pdfService;
    private final PrescriptionRepository prescriptionRepository;

    PetMedicalHistoryController(
            PetMedicalRecordService medicalRecordService,
            PdfService pdfService,
            PrescriptionRepository prescriptionRepository) {
        this.medicalRecordService = medicalRecordService;
        this.pdfService = pdfService;
        this.prescriptionRepository = prescriptionRepository;
    }

    @GetMapping("/{petId}/vaccination-schedule")
    public List<VaccinationScheduleItem> vaccinationSchedule(@PathVariable Long petId) {
        return medicalRecordService.getVaccinationSchedule(petId);
    }

    @GetMapping(value = "/{petId}/medical-history/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadMedicalHistoryPdf(@PathVariable Long petId) {
        MedicalHistoryBundle bundle = medicalRecordService.loadHistoryBundle(petId);
        var prescriptions = prescriptionRepository.findByPetId(petId);
        byte[] pdf = pdfService.buildMedicalHistoryPdf(
                bundle.pet(),
                bundle.records(),
                prescriptions,
                bundle.vaccinationSchedule(),
                bundle.reminders());
        String safeName = bundle.pet().getName().replaceAll("[^a-zA-Z0-9]", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=Medical_History_" + safeName + "_" + petId + ".pdf")
                .body(pdf);
    }
}
