package com.bayport.service;

import com.bayport.entity.Medication;
import com.bayport.entity.Procedure;
import com.bayport.entity.ProcedureMedication;
import com.bayport.entity.Prescription;
import com.bayport.repository.MedicationRepository;
import com.bayport.repository.ProcedureMedicationRepository;
import com.bayport.repository.ProcedureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing procedure-medication mappings and auto-generating prescriptions.
 */
@Service
@Transactional
public class ProcedureMedicationService {

    private final ProcedureMedicationRepository procedureMedicationRepository;
    private final MedicationRepository medicationRepository;
    private final ProcedureRepository procedureRepository;
    private final BayportService bayportService;

    public ProcedureMedicationService(
            ProcedureMedicationRepository procedureMedicationRepository,
            MedicationRepository medicationRepository,
            ProcedureRepository procedureRepository,
            BayportService bayportService) {
        this.procedureMedicationRepository = procedureMedicationRepository;
        this.medicationRepository = medicationRepository;
        this.procedureRepository = procedureRepository;
        this.bayportService = bayportService;
    }

    /**
     * Get all medications linked to a procedure.
     */
    public List<Medication> getMedicationsForProcedure(Long procedureId) {
        return procedureMedicationRepository.findByProcedureId(procedureId).stream()
                .map(ProcedureMedication::getMedication)
                .filter(m -> m != null && m.isActive())
                .collect(Collectors.toList());
    }

    /**
     * Link a medication to a procedure.
     */
    public ProcedureMedication linkMedicationToProcedure(Long procedureId, Long medicationId, 
                                                         String dosageOverride, String instructions) {
        Procedure procedure = procedureRepository.findById(procedureId)
                .orElseThrow(() -> new IllegalArgumentException("Procedure not found"));
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new IllegalArgumentException("Medication not found"));

        ProcedureMedication link = new ProcedureMedication();
        link.setProcedure(procedure);
        link.setMedication(medication);
        link.setDosageOverride(dosageOverride);
        link.setInstructions(instructions);
        return procedureMedicationRepository.save(link);
    }

    /**
     * Automatically generate prescriptions from a procedure's linked medications.
     * 
     * @param procedureId The procedure ID
     * @param petId The pet ID
     * @param prescriber The veterinarian name
     * @return List of created prescriptions
     */
    public List<Prescription> generatePrescriptionsFromProcedure(Long procedureId, Long petId, String prescriber) {
        Procedure procedure = procedureRepository.findById(procedureId)
                .orElseThrow(() -> new IllegalArgumentException("Procedure not found"));

        List<ProcedureMedication> links = procedureMedicationRepository.findByProcedureId(procedureId);
        if (links.isEmpty()) {
            throw new IllegalArgumentException("Selected procedure has no medication mapping configured");
        }
        if (!StringUtils.hasText(prescriber)) {
            throw new IllegalArgumentException("Prescriber is required");
        }
        List<Prescription> prescriptions = new ArrayList<>();

        for (ProcedureMedication link : links) {
            Medication medication = link.getMedication();
            if (medication == null || !medication.isActive()) {
                continue;
            }

            Prescription prescription = new Prescription();
            prescription.setPetId(petId);
            prescription.setDrug(medication.getName());
            prescription.setDosage(null);
            prescription.setDirections(null);
            prescription.setNotes(null);
            prescription.setPrescriber(prescriber);
            prescription.setDate(LocalDate.now());
            // Pricing is handled at POS from inventory, not on the prescription record.

            // Get pet name and owner
            bayportService.getPetById(petId).ifPresent(pet -> {
                prescription.setPet(pet.getName());
                prescription.setOwner(pet.getOwner());
            });

            Prescription saved = bayportService.savePrescription(prescription);
            prescriptions.add(saved);
        }

        return prescriptions;
    }

    /**
     * Get all active medications.
     */
    public List<Medication> getAllActiveMedications() {
        return medicationRepository.findByActiveTrue();
    }
}

