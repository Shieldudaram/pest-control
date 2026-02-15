package com.Chris__.pest_control.match;

import com.Chris__.pest_control.Tier;

public final class ScalingModel {

    private ScalingModel() {
    }

    public static int clampPlayerCount(int playerCount) {
        return Math.max(1, Math.min(8, playerCount));
    }

    public static double playerScale(int playerCount) {
        int p = clampPlayerCount(playerCount);
        return clamp(0.55 + 0.12 * p, 0.55, 1.60);
    }

    public static double tierPressure(Tier tier) {
        if (tier == null) return 1.0;
        return switch (tier) {
            case NOVICE -> 1.00;
            case INTERMEDIATE -> 1.35;
            case VETERAN -> 1.75;
        };
    }

    public static double spawnIntervalSeconds(int playerCount, double baseIntervalSeconds) {
        double base = baseIntervalSeconds <= 0.0 ? 8.0 : baseIntervalSeconds;
        return base / playerScale(playerCount);
    }

    public static int spawnCountPerWave(Tier tier, int playerCount) {
        int base = switch (tier == null ? Tier.NOVICE : tier) {
            case NOVICE -> 2;
            case INTERMEDIATE -> 3;
            case VETERAN -> 4;
        };
        return Math.max(1, (int) Math.round(base * playerScale(playerCount)));
    }

    public static int portalHp(Tier tier, int playerCount) {
        int base = switch (tier == null ? Tier.NOVICE : tier) {
            case NOVICE -> 2200;
            case INTERMEDIATE -> 3200;
            case VETERAN -> 4600;
        };
        int p = clampPlayerCount(playerCount);
        return Math.max(1, (int) Math.round(base * (0.75 + 0.08 * p)));
    }

    public static int knightHp(Tier tier, int playerCount) {
        int base = switch (tier == null ? Tier.NOVICE : tier) {
            case NOVICE -> 2500;
            case INTERMEDIATE -> 3600;
            case VETERAN -> 5000;
        };
        int p = clampPlayerCount(playerCount);
        return Math.max(1, (int) Math.round(base * (0.80 + 0.07 * p)));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
