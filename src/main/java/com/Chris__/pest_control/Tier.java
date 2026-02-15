package com.Chris__.pest_control;

import java.util.Locale;

public enum Tier {
    NOVICE,
    INTERMEDIATE,
    VETERAN;

    public static Tier parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "NOVICE", "N" -> NOVICE;
            case "INTERMEDIATE", "MID", "I" -> INTERMEDIATE;
            case "VETERAN", "VET", "V" -> VETERAN;
            default -> null;
        };
    }
}
