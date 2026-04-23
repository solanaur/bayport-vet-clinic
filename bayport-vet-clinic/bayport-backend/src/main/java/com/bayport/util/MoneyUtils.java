package com.bayport.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * Centralizes all currency normalization/formatting logic so both the
 * persistence layer and API responses consistently show two decimal places.
 */
public final class MoneyUtils {

    private static final DecimalFormat TWO_DECIMAL =
            new DecimalFormat("#,##0.00");

    /** Unicode Philippine Peso sign (₱). */
    public static final String PESO_SIGN = "\u20B1";

    private MoneyUtils() {}

    /**
     * Normalizes any numeric input to a {@link BigDecimal} scaled to two
     * decimal places using HALF_UP rounding. Returns {@link BigDecimal#ZERO}
     * when the input itself is {@code null} so that callers can safely do
     * arithmetic without extra null checks and never produce NaN.
     */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Helper for controllers/services that need a human readable string with
     * two decimals (e.g. logging, PDF output).
     */
    public static String format(BigDecimal value) {
        return TWO_DECIMAL.format(value == null ? BigDecimal.ZERO : normalize(value));
    }

    /**
     * Formats a monetary value with the Philippine Peso sign (₱) and two
     * decimal places, e.g. {@code "₱1,234.56"}. Null values are treated as 0.00.
     */
    public static String formatPeso(BigDecimal value) {
        return PESO_SIGN + format(value);
    }
}

