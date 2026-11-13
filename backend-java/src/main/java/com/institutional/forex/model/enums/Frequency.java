package com.institutional.forex.model.enums;

/**
 * Publication frequency of macro economic data.
 */
public enum Frequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY;

    public static Frequency fromString(String val) {
        if (val == null) return MONTHLY;
        try {
            return Frequency.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MONTHLY;
        }
    }
}
