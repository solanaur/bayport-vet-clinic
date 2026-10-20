package com.bayport.service;

import com.bayport.entity.InventoryItem;
import com.bayport.entity.MedicalRecordType;
import com.bayport.entity.Owner;
import com.bayport.entity.Pet;
import com.bayport.entity.PetMedicalRecord;
import com.bayport.repository.InventoryItemRepository;
import com.bayport.repository.OwnerRepository;
import com.bayport.repository.PetMedicalRecordRepository;
import com.bayport.repository.PetRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ClinicDataService {

    private final OwnerRepository ownerRepository;
    private final PetRepository petRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final PetMedicalRecordRepository petMedicalRecordRepository;
    private final BayportService bayportService;
    private final BillingService billingService;

    public ClinicDataService(OwnerRepository ownerRepository,
                             PetRepository petRepository,
                             InventoryItemRepository inventoryItemRepository,
                             PetMedicalRecordRepository petMedicalRecordRepository,
                             BayportService bayportService,
                             BillingService billingService) {
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.petMedicalRecordRepository = petMedicalRecordRepository;
        this.bayportService = bayportService;
        this.billingService = billingService;
    }

    public String template(String kind) {
        return String.join(",", ClinicImportRules.headers(kind)) + "\n";
    }

    public String exportCsv(String kind) {
        List<String> headers = exportHeaders(kind);
        StringBuilder csv = new StringBuilder(String.join(",", headers)).append('\n');
        for (List<String> row : exportRows(kind)) {
            csv.append(row.stream().map(ClinicDataService::cell).collect(Collectors.joining(","))).append('\n');
        }
        return csv.toString();
    }

    public byte[] exportPdf(String kind) {
        List<String> headers = exportHeaders(kind);
        List<List<String>> rows = exportRows(kind);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(new Paragraph("Bayport Veterinary Clinic — " + kind));
            PdfPTable table = new PdfPTable(headers.size());
            for (String header : headers) {
                table.addCell(header);
            }
            for (List<String> row : rows) {
                for (String value : row) {
                    table.addCell(value == null ? "" : value);
                }
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build the PDF.");
        }
    }

    @Transactional
    public Map<String, Object> importRows(String kind, List<Map<String, String>> rows, boolean commit) {
        Set<String> existing = existingKeys(kind);
        List<Map<String, String>> errors = ClinicImportRules.check(kind, rows == null ? List.of() : rows, existing);
        if (!errors.isEmpty() || !commit) {
            return Map.of("ok", errors.isEmpty(), "errors", errors, "committed", false);
        }
        saveAll(kind, rows);
        return Map.of("ok", true, "errors", List.of(), "committed", true, "count", rows.size());
    }

    public Map<String, Object> alerts() {
        List<Map<String, Object>> lowStock = new ArrayList<>();
        List<Map<String, Object>> expiring = new ArrayList<>();
        LocalDate soon = LocalDate.now().plusDays(90);
        for (InventoryItem item : inventoryItemRepository.findAll()) {
            if (item.getQuantity() != null && item.getReorderLevel() != null && item.getQuantity() <= item.getReorderLevel()) {
                lowStock.add(Map.of("name", item.getName(), "quantity", item.getQuantity()));
            }
            if (item.getExpiryDate() != null && !item.getExpiryDate().isAfter(soon)) {
                expiring.add(Map.of("name", item.getName(), "expiryDate", item.getExpiryDate().toString()));
            }
        }
        return Map.of("lowStock", lowStock, "expiring", expiring);
    }

    private void saveAll(String kind, List<Map<String, String>> rows) {
        switch (ClinicImportRules.normalize(kind)) {
            case "owners" -> rows.forEach(row -> {
                Owner owner = new Owner();
                owner.setFullName(row.get("fullName").trim());
                owner.setPhone(row.get("phone").trim());
                owner.setEmail(blankToNull(row.get("email")));
                owner.setAddress(blankToNull(row.get("address")));
                ownerRepository.save(owner);
            });
            case "pets" -> rows.forEach(row -> {
                Owner owner = ownerRepository.findByPhone(row.get("ownerPhone").trim()).stream().findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No owner with that phone."));
                Pet pet = new Pet();
                pet.setName(row.get("name").trim());
                pet.setSpecies(blankToNull(row.get("species")));
                pet.setBreed(blankToNull(row.get("breed")));
                pet.setGender(blankToNull(row.get("gender")));
                pet.setOwnerId(owner.getId());
                pet.setOwner(owner.getFullName());
                pet.setRecordStatus("ACTIVE");
                petRepository.save(pet);
            });
            case "vaccinations" -> rows.forEach(row -> {
                Pet pet = findPet(row.get("petName"), row.get("ownerPhone"));
                PetMedicalRecord record = new PetMedicalRecord();
                record.setPetId(pet.getId());
                record.setRecordType(MedicalRecordType.VACCINATION);
                record.setVaccineType(row.get("vaccineType").trim());
                record.setRecordDate(LocalDate.parse(row.get("recordDate").trim()));
                if (row.get("nextDueDate") != null && !row.get("nextDueDate").isBlank()) {
                    record.setNextDueDate(LocalDate.parse(row.get("nextDueDate").trim()));
                }
                petMedicalRecordRepository.save(record);
            });
            case "inventory" -> rows.forEach(row -> {
                InventoryItem item = new InventoryItem();
                item.setName(row.get("name").trim());
                item.setSku(row.get("sku").trim());
                item.setQuantity(parseInt(row.get("quantity")));
                item.setReorderLevel(parseInt(row.get("reorderLevel")));
                if (row.get("unitPrice") != null && !row.get("unitPrice").isBlank()) {
                    item.setUnitPrice(new BigDecimal(row.get("unitPrice").trim()));
                }
                item.setBatchNo(blankToNull(row.get("batchNo")));
                if (row.get("expiryDate") != null && !row.get("expiryDate").isBlank()) {
                    item.setExpiryDate(LocalDate.parse(row.get("expiryDate").trim()));
                }
                inventoryItemRepository.save(item);
            });
            default -> throw new IllegalArgumentException("Unknown import type.");
        }
    }

    private Set<String> existingKeys(String kind) {
        return switch (ClinicImportRules.normalize(kind)) {
            case "owners" -> ownerRepository.findAll().stream().map(Owner::getPhone).collect(Collectors.toSet());
            case "pets" -> petRepository.findAll().stream()
                    .map(pet -> (pet.getName() == null ? "" : pet.getName().toLowerCase(Locale.ROOT)) + "|" + phoneFor(pet))
                    .collect(Collectors.toSet());
            case "inventory" -> inventoryItemRepository.findAll().stream()
                    .map(item -> item.getSku() == null ? "" : item.getSku().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            case "vaccinations" -> Set.of();
            default -> Set.of();
        };
    }

    private String phoneFor(Pet pet) {
        if (pet.getOwnerId() == null) {
            return "";
        }
        return ownerRepository.findById(pet.getOwnerId()).map(Owner::getPhone).orElse("");
    }

    private Pet findPet(String name, String phone) {
        return petRepository.findAll().stream()
                .filter(pet -> pet.getName() != null && pet.getName().equalsIgnoreCase(name.trim()))
                .filter(pet -> phone.trim().equals(phoneFor(pet)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pet was not found for that owner."));
    }

    private List<String> exportHeaders(String kind) {
        return switch (kind == null ? "" : kind.toLowerCase(Locale.ROOT)) {
            case "owners" -> ClinicImportRules.headers("owners");
            case "pets" -> List.of("name", "species", "breed", "owner", "recordStatus");
            case "appointments" -> List.of("date", "time", "vet", "owner", "status");
            case "invoices" -> List.of("id", "ownerName", "petName", "amount", "status");
            case "prescriptions" -> List.of("pet", "drug", "dosage", "prescriber", "date");
            case "inventory" -> List.of("name", "sku", "quantity", "reorderLevel", "batchNo", "expiryDate");
            default -> throw new IllegalArgumentException("Unknown export type.");
        };
    }

    private List<List<String>> exportRows(String kind) {
        return switch (kind == null ? "" : kind.toLowerCase(Locale.ROOT)) {
            case "owners" -> ownerRepository.findAll().stream()
                    .filter(owner -> owner.getDeleted() == null || !owner.getDeleted())
                    .map(owner -> List.of(text(owner.getFullName()), text(owner.getPhone()), text(owner.getEmail()), text(owner.getAddress())))
                    .toList();
            case "pets" -> petRepository.findAll().stream()
                    .filter(pet -> pet.getDeleted() == null || !pet.getDeleted())
                    .map(pet -> List.of(text(pet.getName()), text(pet.getSpecies()), text(pet.getBreed()), text(pet.getOwner()), text(pet.getRecordStatus())))
                    .toList();
            case "appointments" -> bayportService.getAllAppointments().stream()
                    .map(row -> List.of(String.valueOf(row.getDate()), text(row.getTime()), text(row.getVet()), text(row.getOwner()), text(row.getStatus())))
                    .toList();
            case "invoices" -> billingService.listAll().stream()
                    .map(row -> List.of(String.valueOf(row.getId()), text(row.getOwnerName()), text(row.getPetName()), String.valueOf(row.getAmount()), String.valueOf(row.getStatus())))
                    .toList();
            case "prescriptions" -> bayportService.getAllPrescriptions().stream()
                    .map(row -> List.of(text(row.getPet()), text(row.getDrug()), text(row.getDosage()), text(row.getPrescriber()), String.valueOf(row.getDate())))
                    .toList();
            case "inventory" -> inventoryItemRepository.findAll().stream()
                    .map(row -> List.of(text(row.getName()), text(row.getSku()), String.valueOf(row.getQuantity()), String.valueOf(row.getReorderLevel()), text(row.getBatchNo()), String.valueOf(row.getExpiryDate())))
                    .toList();
            default -> List.of();
        };
    }

    private static String cell(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.valueOf(value.trim());
    }
}
