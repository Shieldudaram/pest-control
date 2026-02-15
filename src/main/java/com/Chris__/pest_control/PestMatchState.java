package com.Chris__.pest_control;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PestMatchState {

    public String matchId = "";
    public Tier tier = Tier.NOVICE;
    public MatchPhase phase = MatchPhase.QUEUEING;
    public String arenaId = "";

    public long seed;

    public long createdAtMillis;
    public long phaseStartedAtMillis;
    public long matchEndAtMillis;

    public final Set<String> playerUuids = new HashSet<>();
    public final Map<String, Integer> contribution = new HashMap<>();

    public final List<Integer> portalOrder = new ArrayList<>();
    public final Map<Integer, PortalRuntime> portals = new HashMap<>();
    public int currentPortalOrderIndex = -1;

    public int voidKnightHp;
    public int voidKnightMaxHp;

    public long nextEnemyWaveAtMillis;

    public SideObjectiveRuntime activeObjective;
    public final Set<SideObjectiveType> usedObjectiveTypes = new HashSet<>();

    public final EnumMap<TeamBuffType, Long> activeBuffsUntilMillis = new EnumMap<>(TeamBuffType.class);

    public boolean won;
    public String endReason = "";

    public void normalize() {
        if (matchId == null) matchId = "";
        if (tier == null) tier = Tier.NOVICE;
        if (phase == null) phase = MatchPhase.QUEUEING;
        if (arenaId == null) arenaId = "";
        if (endReason == null) endReason = "";

        for (int i = 0; i < 4; i++) {
            portals.computeIfAbsent(i, PortalRuntime::new).normalize();
        }
        for (Map.Entry<Integer, PortalRuntime> entry : portals.entrySet()) {
            if (entry.getValue() != null) entry.getValue().normalize();
        }

        if (activeObjective != null) activeObjective.normalize();
    }

    public int activePortalIndex() {
        if (currentPortalOrderIndex < 0 || currentPortalOrderIndex >= portalOrder.size()) return -1;
        return portalOrder.get(currentPortalOrderIndex);
    }

    public boolean areAllPortalsDestroyed() {
        for (int i = 0; i < 4; i++) {
            PortalRuntime p = portals.get(i);
            if (p == null || p.state != PortalState.DESTROYED) return false;
        }
        return true;
    }

    public static final class PortalRuntime {
        public int index;
        public PortalState state = PortalState.SHIELDED;
        public int maxHp = 1000;
        public int hp = 1000;

        public PortalRuntime() {
            this(0);
        }

        public PortalRuntime(int index) {
            this.index = index;
        }

        public void normalize() {
            if (state == null) state = PortalState.SHIELDED;
            if (maxHp <= 0) maxHp = 1;
            if (hp < 0) hp = 0;
            if (hp > maxHp) hp = maxHp;
        }
    }

    public static final class SideObjectiveRuntime {
        public SideObjectiveType type = SideObjectiveType.CAPTURE_POINT;
        public long startedAtMillis;
        public long expiresAtMillis;
        public boolean completed;

        public void normalize() {
            if (type == null) type = SideObjectiveType.CAPTURE_POINT;
            if (expiresAtMillis < startedAtMillis) expiresAtMillis = startedAtMillis;
        }
    }
}
