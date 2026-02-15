package com.Chris__.pest_control.objective;

import com.Chris__.pest_control.PestMatchState;
import com.Chris__.pest_control.SideObjectiveType;
import com.Chris__.pest_control.TeamBuffType;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.config.PestConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class SideObjectiveService {

    private final ConfigRepository configRepository;

    public SideObjectiveService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public PestMatchState.SideObjectiveRuntime startNextObjective(PestMatchState state, long nowMillis) {
        if (state == null) return null;

        PestConfig cfg = configRepository == null ? null : configRepository.get();
        int durationSeconds = (cfg == null || cfg.timers == null) ? 60 : Math.max(1, cfg.timers.sideObjectiveDurationSeconds);

        List<SideObjectiveType> enabled = (cfg == null || cfg.sideObjectives == null || cfg.sideObjectives.enabled == null)
                ? defaultEnabled()
                : cfg.sideObjectives.enabled;

        SideObjectiveType type = pickNextType(state, enabled);
        PestMatchState.SideObjectiveRuntime objective = new PestMatchState.SideObjectiveRuntime();
        objective.type = type;
        objective.startedAtMillis = nowMillis;
        objective.expiresAtMillis = nowMillis + durationSeconds * 1000L;
        objective.completed = false;
        objective.normalize();

        state.activeObjective = objective;
        state.usedObjectiveTypes.add(type);

        return objective;
    }

    public void completeObjective(PestMatchState state, long nowMillis) {
        if (state == null || state.activeObjective == null) return;
        if (state.activeObjective.completed) return;

        state.activeObjective.completed = true;
        TeamBuffType buff = mapBuffFor(state.activeObjective.type);

        PestConfig cfg = configRepository == null ? null : configRepository.get();
        int durationSeconds = (cfg == null || cfg.timers == null) ? 60 : Math.max(1, cfg.timers.sideObjectiveDurationSeconds);

        state.activeBuffsUntilMillis.put(buff, nowMillis + durationSeconds * 1000L);
    }

    public void expire(PestMatchState state, long nowMillis) {
        if (state == null) return;

        if (state.activeObjective != null && state.activeObjective.expiresAtMillis <= nowMillis) {
            state.activeObjective = null;
        }

        state.activeBuffsUntilMillis.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= nowMillis);
    }

    private static List<SideObjectiveType> defaultEnabled() {
        return List.of(
                SideObjectiveType.CAPTURE_POINT,
                SideObjectiveType.ESCORT_PAYLOAD,
                SideObjectiveType.DEFEND_NODE
        );
    }

    private static SideObjectiveType pickNextType(PestMatchState state, List<SideObjectiveType> enabled) {
        if (enabled == null || enabled.isEmpty()) return SideObjectiveType.CAPTURE_POINT;

        List<SideObjectiveType> candidates = new ArrayList<>();
        for (SideObjectiveType type : enabled) {
            if (type == null) continue;
            if (!state.usedObjectiveTypes.contains(type)) candidates.add(type);
        }
        if (candidates.isEmpty()) {
            candidates.addAll(enabled);
            state.usedObjectiveTypes.clear();
        }

        long salt = state.usedObjectiveTypes.size() * 31L + state.currentPortalOrderIndex * 13L + 7L;
        Random random = new Random(state.seed ^ salt);
        return candidates.get(random.nextInt(candidates.size()));
    }

    public static TeamBuffType mapBuffFor(SideObjectiveType type) {
        if (type == null) return TeamBuffType.BATTLE_FERVOR;
        return switch (type) {
            case CAPTURE_POINT -> TeamBuffType.BATTLE_FERVOR;
            case ESCORT_PAYLOAD -> TeamBuffType.FIELD_REPAIRS;
            case DEFEND_NODE -> TeamBuffType.RAPID_RESPONSE;
        };
    }

    public double damageMultiplier(Map<TeamBuffType, Long> activeBuffsUntilMillis, long nowMillis, PestConfig cfg) {
        if (activeBuffsUntilMillis == null || cfg == null || cfg.sideObjectives == null || cfg.sideObjectives.buffs == null) return 1.0;
        Long until = activeBuffsUntilMillis.get(TeamBuffType.BATTLE_FERVOR);
        if (until == null || until <= nowMillis) return 1.0;
        return cfg.sideObjectives.buffs.battleFervorDamageMultiplier;
    }

    public double repairMultiplier(Map<TeamBuffType, Long> activeBuffsUntilMillis, long nowMillis, PestConfig cfg) {
        if (activeBuffsUntilMillis == null || cfg == null || cfg.sideObjectives == null || cfg.sideObjectives.buffs == null) return 1.0;
        Long until = activeBuffsUntilMillis.get(TeamBuffType.FIELD_REPAIRS);
        if (until == null || until <= nowMillis) return 1.0;
        return cfg.sideObjectives.buffs.fieldRepairsMultiplier;
    }

    public double moveSpeedMultiplier(Map<TeamBuffType, Long> activeBuffsUntilMillis, long nowMillis, PestConfig cfg) {
        if (activeBuffsUntilMillis == null || cfg == null || cfg.sideObjectives == null || cfg.sideObjectives.buffs == null) return 1.0;
        Long until = activeBuffsUntilMillis.get(TeamBuffType.RAPID_RESPONSE);
        if (until == null || until <= nowMillis) return 1.0;
        return cfg.sideObjectives.buffs.rapidResponseSpeedMultiplier;
    }
}
