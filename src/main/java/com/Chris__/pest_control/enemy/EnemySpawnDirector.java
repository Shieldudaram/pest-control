package com.Chris__.pest_control.enemy;

import com.Chris__.pest_control.EnemyType;
import com.Chris__.pest_control.PestMatchState;
import com.Chris__.pest_control.Tier;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.config.PestConfig;
import com.Chris__.pest_control.match.ScalingModel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class EnemySpawnDirector {

    public record SpawnPlan(long nextWaveAtMillis, Map<EnemyType, Integer> counts) {
    }

    private final ConfigRepository configRepository;

    public EnemySpawnDirector(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public SpawnPlan buildWavePlan(PestMatchState state, int playerCount, long nowMillis) {
        if (state == null) {
            return new SpawnPlan(nowMillis + 1000L, Map.of());
        }

        Tier tier = state.tier == null ? Tier.NOVICE : state.tier;
        int clampedPlayers = ScalingModel.clampPlayerCount(playerCount);

        PestConfig cfg = configRepository == null ? null : configRepository.get();
        double baseInterval = 8.0;
        if (cfg != null && cfg.tiers != null && cfg.tiers.forTier(tier) != null) {
            // interval remains formula-driven; tier pressure is used for composition weighting.
            baseInterval = 8.0;
        }

        long nextAt = nowMillis + Math.max(500L, (long) (ScalingModel.spawnIntervalSeconds(clampedPlayers, baseInterval) * 1000L));

        int total = ScalingModel.spawnCountPerWave(tier, clampedPlayers);
        Map<EnemyType, Integer> weights = weightedRoster(tier);

        List<EnemyType> bag = new ArrayList<>();
        for (Map.Entry<EnemyType, Integer> e : weights.entrySet()) {
            int w = Math.max(0, e.getValue());
            for (int i = 0; i < w; i++) {
                bag.add(e.getKey());
            }
        }
        if (bag.isEmpty()) {
            bag.add(EnemyType.BRAWLER);
        }

        Random random = new Random(state.seed ^ (state.currentPortalOrderIndex * 997L) ^ nowMillis);
        EnumMap<EnemyType, Integer> counts = new EnumMap<>(EnemyType.class);
        for (EnemyType type : EnemyType.values()) counts.put(type, 0);

        for (int i = 0; i < total; i++) {
            EnemyType pick = bag.get(random.nextInt(bag.size()));
            counts.put(pick, counts.getOrDefault(pick, 0) + 1);
        }

        return new SpawnPlan(nextAt, counts);
    }

    private static Map<EnemyType, Integer> weightedRoster(Tier tier) {
        EnumMap<EnemyType, Integer> m = new EnumMap<>(EnemyType.class);
        if (tier == Tier.VETERAN) {
            m.put(EnemyType.BRAWLER, 4);
            m.put(EnemyType.DEFILER, 3);
            m.put(EnemyType.RAVAGER, 3);
            m.put(EnemyType.SHIFTER, 3);
            m.put(EnemyType.SPINNER, 2);
            m.put(EnemyType.SPLATTER, 2);
            m.put(EnemyType.TORCHER, 3);
            return m;
        }

        if (tier == Tier.INTERMEDIATE) {
            m.put(EnemyType.BRAWLER, 5);
            m.put(EnemyType.DEFILER, 3);
            m.put(EnemyType.RAVAGER, 2);
            m.put(EnemyType.SHIFTER, 2);
            m.put(EnemyType.SPINNER, 2);
            m.put(EnemyType.SPLATTER, 1);
            m.put(EnemyType.TORCHER, 2);
            return m;
        }

        m.put(EnemyType.BRAWLER, 6);
        m.put(EnemyType.DEFILER, 2);
        m.put(EnemyType.RAVAGER, 1);
        m.put(EnemyType.SHIFTER, 1);
        m.put(EnemyType.SPINNER, 1);
        m.put(EnemyType.SPLATTER, 1);
        m.put(EnemyType.TORCHER, 1);
        return m;
    }
}
