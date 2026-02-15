package com.Chris__.pest_control.reward;

import com.Chris__.pest_control.Tier;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.config.PestConfig;
import com.Chris__.pest_control.data.PestPointsRepository;
import com.Chris__.pest_control.data.PestStatsRepository;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class PestRewardService {

    public record PlayerPayout(int pointsAwarded, boolean mvp) {
    }

    public record PayoutResult(Map<String, PlayerPayout> byPlayer) {
        public int pointsFor(String playerUuid) {
            PlayerPayout payout = byPlayer.get(playerUuid);
            return payout == null ? 0 : payout.pointsAwarded();
        }
    }

    private final ConfigRepository configRepository;
    private final PestPointsRepository pointsRepository;
    private final PestStatsRepository statsRepository;

    public PestRewardService(ConfigRepository configRepository,
                             PestPointsRepository pointsRepository,
                             PestStatsRepository statsRepository) {
        this.configRepository = configRepository;
        this.pointsRepository = pointsRepository;
        this.statsRepository = statsRepository;
    }

    public PayoutResult applyRoundOutcome(Set<String> participants,
                                          Tier tier,
                                          boolean won,
                                          Map<String, Integer> contribution) {
        if (participants == null || participants.isEmpty()) {
            return new PayoutResult(Map.of());
        }

        Tier actualTier = tier == null ? Tier.NOVICE : tier;
        PestConfig cfg = configRepository == null ? null : configRepository.get();
        int points = 0;
        if (won && cfg != null && cfg.tiers != null) {
            points = Math.max(0, cfg.tiers.forTier(actualTier).flatWinPoints);
        }

        String mvpUuid = pickMvp(participants, contribution);

        Map<String, PlayerPayout> result = new LinkedHashMap<>();
        for (String uuid : participants) {
            if (uuid == null || uuid.isBlank()) continue;
            boolean isMvp = uuid.equals(mvpUuid);
            if (pointsRepository != null && points > 0) {
                pointsRepository.addPoints(uuid, points);
            }
            if (statsRepository != null) {
                statsRepository.recordMatch(uuid, actualTier, won, points, isMvp);
            }
            result.put(uuid, new PlayerPayout(points, isMvp));
        }

        if (pointsRepository != null) pointsRepository.saveCurrent();
        if (statsRepository != null) statsRepository.saveCurrent();

        return new PayoutResult(result);
    }

    private static String pickMvp(Set<String> participants, Map<String, Integer> contribution) {
        if (participants == null || participants.isEmpty()) return "";

        Map<String, Integer> scores = (contribution == null) ? new HashMap<>() : new HashMap<>(contribution);
        String winner = "";
        int best = Integer.MIN_VALUE;

        for (String uuid : participants) {
            if (uuid == null || uuid.isBlank()) continue;
            int score = scores.getOrDefault(uuid, 0);
            if (winner.isBlank() || score > best || (score == best && uuid.compareTo(winner) < 0)) {
                winner = uuid;
                best = score;
            }
        }

        return winner;
    }
}
