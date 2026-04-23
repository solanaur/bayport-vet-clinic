package com.bayport.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses line items from POS {@link com.bayport.entity.Sale#getNote()} strings produced by
 * {@link com.bayport.service.PosService#checkout} — e.g.
 * {@code Payment: Cash. Consult ×1 @ ₱500.00 | Shampoo x2 @ ₱150.00}
 */
public final class PosSaleNoteParser {

    /** Unicode ×, ASCII x/X; optional peso sign after @ */
    private static final List<Pattern> LINE_PATTERNS = List.of(
            Pattern.compile("^(.+?)\\s*[×xX](\\d+)\\s*@\\s*\u20B1\\s*([\\d,]+(?:\\.\\d+)?)\\s*$"),
            Pattern.compile("^(.+?)\\s*[×xX](\\d+)\\s*@\\s*([\\d,]+(?:\\.\\d+)?)\\s*$")
    );

    private PosSaleNoteParser() {}

    public record ParsedLine(String itemName, int quantity, BigDecimal unitPrice) {}

    /**
     * Strips the leading {@code Payment: … .} prefix and splits remaining segments on {@code |}.
     */
    public static List<ParsedLine> parse(String note) {
        List<ParsedLine> out = new ArrayList<>();
        if (note == null || note.isBlank()) {
            return out;
        }
        String body = note.replaceFirst("^\\s*Payment:\\s*[^.]*\\.\\s*", "").trim();
        if (body.isEmpty()) {
            return out;
        }
        for (String segment : body.split("\\s*\\|\\s*")) {
            String s = segment.trim();
            if (s.isEmpty()) {
                continue;
            }
            ParsedLine parsed = tryParseSegment(s);
            if (parsed != null) {
                out.add(parsed);
            }
        }
        return out;
    }

    private static ParsedLine tryParseSegment(String s) {
        for (Pattern p : LINE_PATTERNS) {
            Matcher m = p.matcher(s);
            if (m.matches()) {
                String name = m.group(1).trim();
                int qty = Integer.parseInt(m.group(2), 10);
                BigDecimal unit = parseMoney(m.group(3));
                if (qty > 0 && unit != null && unit.signum() > 0) {
                    return new ParsedLine(name, qty, unit);
                }
            }
        }
        return null;
    }

    private static BigDecimal parseMoney(String raw) {
        if (raw == null) {
            return null;
        }
        String n = raw.replace(",", "").trim();
        try {
            return new BigDecimal(n).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
