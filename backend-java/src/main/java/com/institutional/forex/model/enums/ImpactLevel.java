package com.institutional.forex.model.enums;

/**
 * Economic release impact tier rating.
 */
public enum ImpactLevel {
    HIGH(3, "High Impact"),
    MED(2, "Medium Impact"),
    LOW(1, "Low Impact"),
    NONE(0, "Non-Economic / Holiday");

    private final int severity;
    private final String description;

    ImpactLevel(int severity, String description) {
        this.severity = severity;
        this.description = description;
    }

    public int getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public static ImpactLevel parseFromScraped(String raw) {
        if (raw == null) return NONE;
        String s = raw.toLowerCase().trim();
        if (s.contains("high") || s.contains("red") || s.contains("star-3") || s.contains("bull3") || s.contains("3")) {
            return HIGH;
        }
        if (s.contains("med") || s.contains("medium") || s.contains("ora") || s.contains("orange") || s.contains("star-2") || s.contains("bull2") || s.contains("2")) {
            return MED;
        }
        if (s.contains("low") || s.contains("yel") || s.contains("yellow") || s.contains("star-1") || s.contains("bull1") || s.contains("1")) {
            return LOW;
        }
        return NONE;
    }
}
