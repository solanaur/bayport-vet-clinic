package com.bayport.web;

import com.bayport.entity.Medication;
import com.bayport.entity.ProcedureMedication;
import com.bayport.service.ProcedureMedicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medications")
public class MedicationController {

    private final ProcedureMedicationService procedureMedicationService;

    public MedicationController(ProcedureMedicationService procedureMedicationService) {
        this.procedureMedicationService = procedureMedicationService;
    }

    @GetMapping
    public List<Medication> listActive() {
        return procedureMedicationService.getAllActiveMedications();
    }

    @GetMapping("/procedure/{procedureId}")
    public List<Medication> getMedicationsForProcedure(@PathVariable Long procedureId) {
        return procedureMedicationService.getMedicationsForProcedure(procedureId);
    }

    @PostMapping("/procedure/{procedureId}/link")
    public ResponseEntity<ProcedureMedication> linkMedication(
            @PathVariable Long procedureId,
            @RequestBody Map<String, Object> request) {
        Long medicationId = Long.valueOf(request.get("medicationId").toString());
        String dosageOverride = (String) request.get("dosageOverride");
        String instructions = (String) request.get("instructions");
        
        ProcedureMedication link = procedureMedicationService.linkMedicationToProcedure(
                procedureId, medicationId, dosageOverride, instructions);
        return ResponseEntity.ok(link);
    }

    @PostMapping("/procedure/{procedureId}/generate-prescriptions")
    public ResponseEntity<?> generatePrescriptions(
            @PathVariable Long procedureId,
            @RequestBody Map<String, Object> request) {
        try {
            Long petId = Long.valueOf(request.get("petId").toString());
            String prescriber = (String) request.get("prescriber");
            
            List<com.bayport.entity.Prescription> prescriptions = 
                    procedureMedicationService.generatePrescriptionsFromProcedure(procedureId, petId, prescriber);
            return ResponseEntity.ok(Map.of("success", true, "prescriptions", prescriptions));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}

