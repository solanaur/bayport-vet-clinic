package com.bayport.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Checks import rows before anything is saved. A commit with any error saves nothing.
 */
public final class ClinicImportRules {

    private ClinicImportRules() {}

    public static List<String> headers(String kind) {
        return switch (normalize(kind)) {
            case "owners" -> List.of("fullName", "phone", "email", "address");
            case "pets" -> List.of("name", "species", "breed", "ownerPhone", "gender");
            case "vaccinations" -> List.of("petName", "ownerPhone", "vaccineType", "recordDate", "nextDueDate");
            case "inventory" -> List.of("name", "sku", "quantity", "reorderLevel", "unitPrice", "batchNo", "expiryDate");
            default -> throw new IllegalArgumentException("Unknown import type.");
        };
    }

    public static List<Map<String, String>> check(String kind, List<Map<String, String>> rows, java.util.Set<String> existingKeys) {
        List<Map<String, String>> errors = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        int rowNumber = 1;
        for (Map<String, String> row : rows) {
            rowNumber++;
            String key = key(kind, row);
            String problem = problem(kind, row);
            if (problem == null && (seen.contains(key) || existingKeys.contains(key))) {
                problem = "Duplicate " + key;
            }
            if (problem != null) {
                Map<String, String> error = new LinkedHashMap<>();
                error.put("row", String.valueOf(rowNumber));
                error.put("message", problem);
                errors.add(error);
            } else {
                seen.add(key);
            }
        }
        return errors;
    }

    public static String key(String kind, Map<String, String> row) {
        return switch (normalize(kind)) {
            case "owners" -> text(row, "phone");
            case "pets" -> text(row, "name").toLowerCase(Locale.ROOT) + "|" + text(row, "ownerPhone");
            case "vaccinations" -> text(row, "petName").toLowerCase(Locale.ROOT) + "|" + text(row, "recordDate") + "|" + text(row, "vaccineType").toLowerCase(Locale.ROOT);
            case "inventory" -> text(row, "sku").toUpperCase(Locale.ROOT);
            default -> "";
        };
    }

    private static String problem(String kind, Map<String, String> row) {
        return switch (normalize(kind)) {
            case "owners" -> {
                String missing = required(row, "fullName", "phone");
                if (missing != null) {
                    yield missing;
                }
                if (!text(row, "phone").matches("\\d{11}")) {
                    yield "phone must be 11 digits";
                }
                yield null;
            }
            case "pets" -> required(row, "name", "ownerPhone");
            case "vaccinations" -> required(row, "petName", "ownerPhone", "vaccineType", "recordDate");
            case "inventory" -> required(row, "name", "sku");
            default -> "Unknown import type.";
        };
    }

    private static String required(Map<String, String> row, String... names) {
        for (String name : names) {
            if (text(row, name).isBlank()) {
                return name + " is required";
            }
        }
        return null;
    }

    private static String text(Map<String, String> row, String name) {
        if (row == null || row.get(name) == null) {
            return "";
        }
        return row.get(name).trim();
    }

    public static String normalize(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    }
}
