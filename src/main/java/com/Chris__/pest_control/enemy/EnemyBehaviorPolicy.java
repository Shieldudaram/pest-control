package com.Chris__.pest_control.enemy;

import com.Chris__.pest_control.EnemyType;

public final class EnemyBehaviorPolicy {

    public enum TargetPriority {
        GATE_THEN_PLAYER,
        PLAYER_THEN_KNIGHT,
        STRUCTURES,
        FLANK_PLAYER,
        ACTIVE_PORTAL,
        CLUSTERED_PLAYER,
        BURN_STRUCTURES
    }

    public record BehaviorProfile(
            EnemyType type,
            TargetPriority priority,
            int structureDamageMultiplierPercent,
            int playerDamageMultiplierPercent,
            boolean teleportFlank,
            boolean suicideAoe,
            boolean healsPortal
    ) {
    }

    public BehaviorProfile profileFor(EnemyType type) {
        if (type == null) type = EnemyType.BRAWLER;
        return switch (type) {
            case BRAWLER -> new BehaviorProfile(type, TargetPriority.GATE_THEN_PLAYER, 100, 110, false, false, false);
            case DEFILER -> new BehaviorProfile(type, TargetPriority.PLAYER_THEN_KNIGHT, 90, 120, false, false, false);
            case RAVAGER -> new BehaviorProfile(type, TargetPriority.STRUCTURES, 180, 80, false, false, false);
            case SHIFTER -> new BehaviorProfile(type, TargetPriority.FLANK_PLAYER, 110, 100, true, false, false);
            case SPINNER -> new BehaviorProfile(type, TargetPriority.ACTIVE_PORTAL, 70, 80, false, false, true);
            case SPLATTER -> new BehaviorProfile(type, TargetPriority.CLUSTERED_PLAYER, 100, 150, false, true, false);
            case TORCHER -> new BehaviorProfile(type, TargetPriority.BURN_STRUCTURES, 140, 100, false, false, false);
        };
    }
}
