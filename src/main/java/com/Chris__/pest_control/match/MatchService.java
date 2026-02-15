package com.Chris__.pest_control.match;

import com.Chris__.pest_control.MatchPhase;
import com.Chris__.pest_control.PestArenaDefinition;
import com.Chris__.pest_control.PestMatchState;
import com.Chris__.pest_control.PortalState;
import com.Chris__.pest_control.SideObjectiveType;
import com.Chris__.pest_control.Tier;
import com.Chris__.pest_control.arena.ArenaService;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.config.PestConfig;
import com.Chris__.pest_control.data.AnalyticsLogger;
import com.Chris__.pest_control.data.MatchHistoryLogger;
import com.Chris__.pest_control.enemy.EnemySpawnDirector;
import com.Chris__.pest_control.interaction.InteractionService;
import com.Chris__.pest_control.objective.SideObjectiveService;
import com.Chris__.pest_control.queue.QueueService;
import com.Chris__.pest_control.reward.PestRewardService;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class MatchService {

    public interface StartGate {
        ArenaService.ValidationResult allowStart(PestArenaDefinition arena);
    }

    public record MatchSnapshot(
            PestMatchState state,
            InteractionService.ArenaInteractionState interactionState,
            String lastError,
            Map<String, PestRewardService.PlayerPayout> latestPayout
    ) {
    }

    public record StartResult(boolean started, String reason) {
        public static StartResult ok() {
            return new StartResult(true, "");
        }

        public static StartResult fail(String reason) {
            return new StartResult(false, reason == null ? "unknown" : reason);
        }
    }

    private static final int MAX_PLAYERS_PER_MATCH = 25;

    private final Object lock = new Object();

    private final ConfigRepository configRepository;
    private final ArenaService arenaService;
    private final QueueService queueService;
    private final SideObjectiveService sideObjectiveService;
    private final EnemySpawnDirector enemySpawnDirector;
    private final InteractionService interactionService;
    private final PestRewardService rewardService;
    private final MatchHistoryLogger historyLogger;
    private final AnalyticsLogger analyticsLogger;
    private final HytaleLogger logger;

    private final AtomicLong idCounter = new AtomicLong(Math.abs(new Random().nextLong(1000, 10_000_000L)));

    private PestMatchState current;
    private InteractionService.ArenaInteractionState currentInteractionState;
    private Map<String, PestRewardService.PlayerPayout> latestPayout = Map.of();
    private String lastError = "";
    private Long forcedSeed;

    private StartGate startGate;

    public MatchService(ConfigRepository configRepository,
                        ArenaService arenaService,
                        QueueService queueService,
                        SideObjectiveService sideObjectiveService,
                        EnemySpawnDirector enemySpawnDirector,
                        InteractionService interactionService,
                        PestRewardService rewardService,
                        MatchHistoryLogger historyLogger,
                        AnalyticsLogger analyticsLogger,
                        HytaleLogger logger) {
        this.configRepository = configRepository;
        this.arenaService = arenaService;
        this.queueService = queueService;
        this.sideObjectiveService = sideObjectiveService;
        this.enemySpawnDirector = enemySpawnDirector;
        this.interactionService = interactionService;
        this.rewardService = rewardService;
        this.historyLogger = historyLogger;
        this.analyticsLogger = analyticsLogger;
        this.logger = logger;
    }

    public void setStartGate(StartGate startGate) {
        this.startGate = startGate;
    }

    public StartResult forceStart(Tier requestedTier, long nowMillis) {
        synchronized (lock) {
            if (current != null) return StartResult.fail("a match is already active");
            return tryStartInternal(requestedTier, nowMillis, true);
        }
    }

    public boolean forceStop(String reason, long nowMillis) {
        synchronized (lock) {
            if (current == null) return false;
            endRound(false, reason == null ? "force_stop" : reason, nowMillis);
            return true;
        }
    }

    public boolean damagePortal(int portalIndex, int amount, String actorUuid, long nowMillis) {
        if (portalIndex < 0 || portalIndex > 3 || amount <= 0) return false;

        synchronized (lock) {
            if (current == null || current.phase != MatchPhase.ACTIVE) return false;

            int activePortal = current.activePortalIndex();
            if (activePortal != portalIndex) return false;

            PestMatchState.PortalRuntime portal = current.portals.get(portalIndex);
            if (portal == null || portal.state != PortalState.ACTIVE || portal.hp <= 0) return false;

            portal.hp = Math.max(0, portal.hp - amount);
            if (actorUuid != null && !actorUuid.isBlank()) {
                current.contribution.merge(actorUuid, amount, Integer::sum);
            }

            if (portal.hp > 0) return true;

            portal.state = PortalState.DESTROYED;
            onPortalDestroyed(nowMillis);
            return true;
        }
    }

    public boolean damageKnight(int amount, long nowMillis) {
        if (amount <= 0) return false;

        synchronized (lock) {
            if (current == null || current.phase != MatchPhase.ACTIVE) return false;
            current.voidKnightHp = Math.max(0, current.voidKnightHp - amount);
            if (current.voidKnightHp <= 0) {
                endRound(false, "knight_dead", nowMillis);
            }
            return true;
        }
    }

    public void addContribution(String playerUuid, int points) {
        if (playerUuid == null || playerUuid.isBlank() || points == 0) return;
        synchronized (lock) {
            if (current == null) return;
            current.contribution.merge(playerUuid, points, Integer::sum);
        }
    }

    public void tick(long nowMillis) {
        synchronized (lock) {
            if (current == null) {
                tryAutoStart(nowMillis);
                return;
            }

            sideObjectiveService.expire(current, nowMillis);

            if (current.phase == MatchPhase.COUNTDOWN) {
                if (nowMillis >= current.phaseStartedAtMillis + countdownMillis()) {
                    beginActive(nowMillis);
                }
                return;
            }

            if (current.phase == MatchPhase.ACTIVE) {
                if (nowMillis >= current.matchEndAtMillis) {
                    endRound(false, "timeout", nowMillis);
                    return;
                }

                if (current.voidKnightHp <= 0) {
                    endRound(false, "knight_dead", nowMillis);
                    return;
                }

                if (current.areAllPortalsDestroyed()) {
                    endRound(true, "all_portals_destroyed", nowMillis);
                    return;
                }

                if (nowMillis >= current.nextEnemyWaveAtMillis) {
                    EnemySpawnDirector.SpawnPlan plan = enemySpawnDirector.buildWavePlan(current, current.playerUuids.size(), nowMillis);
                    current.nextEnemyWaveAtMillis = plan.nextWaveAtMillis();
                    analyticsLogger.event("enemy_wave", Map.of(
                            "matchId", current.matchId,
                            "tier", String.valueOf(current.tier),
                            "counts", plan.counts()
                    ));
                }
                return;
            }

            if (current.phase == MatchPhase.ROUND_END) {
                if (nowMillis >= current.phaseStartedAtMillis + roundEndMillis()) {
                    current.phase = MatchPhase.RESETTING;
                    current.phaseStartedAtMillis = nowMillis;
                }
                return;
            }

            if (current.phase == MatchPhase.RESETTING) {
                if (nowMillis >= current.phaseStartedAtMillis + resetMillis()) {
                    current = null;
                    currentInteractionState = null;
                    lastError = "";
                }
            }
        }
    }

    public MatchSnapshot snapshot() {
        synchronized (lock) {
            return new MatchSnapshot(current, currentInteractionState, lastError, latestPayout);
        }
    }

    public String lastError() {
        synchronized (lock) {
            return lastError;
        }
    }

    public boolean completeActiveObjective(long nowMillis, String actorUuid) {
        synchronized (lock) {
            if (current == null || current.phase != MatchPhase.ACTIVE) return false;
            if (current.activeObjective == null || current.activeObjective.completed) return false;

            sideObjectiveService.completeObjective(current, nowMillis);
            if (actorUuid != null && !actorUuid.isBlank()) {
                current.contribution.merge(actorUuid, 120, Integer::sum);
            }

            analyticsLogger.event("side_objective_complete", Map.of(
                    "matchId", current.matchId,
                    "type", String.valueOf(current.activeObjective.type)
            ));
            return true;
        }
    }

    public boolean hasActiveMatch() {
        synchronized (lock) {
            return current != null && current.phase != MatchPhase.RESETTING;
        }
    }

    public void setForcedSeed(Long forcedSeed) {
        synchronized (lock) {
            this.forcedSeed = forcedSeed;
        }
    }

    private void tryAutoStart(long nowMillis) {
        PestConfig cfg = configRepository == null ? null : configRepository.get();
        int minPlayers = (cfg == null || cfg.queue == null) ? 1 : Math.max(1, cfg.queue.minPlayersToStart);

        Tier tier = queueService.pickTierByOldestTicket();
        if (tier == null) return;

        if (queueService.size(tier) < minPlayers) return;
        tryStartInternal(tier, nowMillis, false);
    }

    private StartResult tryStartInternal(Tier requestedTier, long nowMillis, boolean allowFallbackTier) {
        Tier tier = requestedTier;
        if (tier == null) {
            tier = queueService.pickTierByOldestTicket();
            if (tier == null) return StartResult.fail("no queued players");
        }

        PestArenaDefinition arena = arenaService.getFirstArenaForTier(tier);
        if (arena == null && allowFallbackTier) {
            for (Tier candidate : Tier.values()) {
                arena = arenaService.getFirstArenaForTier(candidate);
                if (arena != null) {
                    tier = candidate;
                    break;
                }
            }
        }

        if (arena == null) {
            lastError = "no valid arena for tier " + tier;
            return StartResult.fail(lastError);
        }

        ArenaService.ValidationResult arenaValidation = arenaService.validateArena(arena);
        if (!arenaValidation.valid()) {
            lastError = arenaValidation.reason();
            return StartResult.fail(lastError);
        }

        if (startGate != null) {
            ArenaService.ValidationResult gate = startGate.allowStart(arena);
            if (gate != null && !gate.valid()) {
                lastError = gate.reason();
                return StartResult.fail(lastError);
            }
        }

        List<String> players = queueService.drainPlayersForTier(tier, MAX_PLAYERS_PER_MATCH);
        if (players.isEmpty()) {
            lastError = "no queued players for tier " + tier;
            return StartResult.fail(lastError);
        }

        PestMatchState state = new PestMatchState();
        state.matchId = "pc-" + idCounter.incrementAndGet();
        state.tier = tier;
        state.phase = MatchPhase.COUNTDOWN;
        state.arenaId = arena.id;
        state.seed = computeSeed(nowMillis, players, tier);
        state.createdAtMillis = nowMillis;
        state.phaseStartedAtMillis = nowMillis;

        state.playerUuids.addAll(players);
        for (String player : players) {
            state.contribution.put(player, 0);
        }

        int playerCount = Math.max(1, players.size());
        state.voidKnightMaxHp = ScalingModel.knightHp(tier, playerCount);
        state.voidKnightHp = state.voidKnightMaxHp;

        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(order, new Random(state.seed));
        state.portalOrder.addAll(order);

        int portalHp = ScalingModel.portalHp(tier, playerCount);
        for (int i = 0; i < 4; i++) {
            PestMatchState.PortalRuntime portal = new PestMatchState.PortalRuntime(i);
            portal.maxHp = portalHp;
            portal.hp = portalHp;
            portal.state = PortalState.SHIELDED;
            state.portals.put(i, portal);
        }

        state.currentPortalOrderIndex = 0;
        int firstActive = state.activePortalIndex();
        PestMatchState.PortalRuntime firstPortal = state.portals.get(firstActive);
        if (firstPortal != null) firstPortal.state = PortalState.ACTIVE;

        state.normalize();

        this.current = state;
        this.currentInteractionState = interactionService.initializeForArena(arena, configRepository.get());
        this.latestPayout = Map.of();
        this.lastError = "";

        analyticsLogger.event("match_start", Map.of(
                "matchId", state.matchId,
                "tier", String.valueOf(state.tier),
                "players", players.size(),
                "seed", state.seed,
                "arena", state.arenaId
        ));

        if (logger != null) {
            logger.atInfo().log("[PestControl] Match started id=%s tier=%s players=%s", state.matchId, tier, players.size());
        }
        return StartResult.ok();
    }

    private void beginActive(long nowMillis) {
        if (current == null) return;
        current.phase = MatchPhase.ACTIVE;
        current.phaseStartedAtMillis = nowMillis;
        current.matchEndAtMillis = nowMillis + matchDurationMillis();

        EnemySpawnDirector.SpawnPlan plan = enemySpawnDirector.buildWavePlan(current, current.playerUuids.size(), nowMillis);
        current.nextEnemyWaveAtMillis = plan.nextWaveAtMillis();

        analyticsLogger.event("match_active", Map.of(
                "matchId", current.matchId,
                "endAt", current.matchEndAtMillis
        ));
    }

    private void onPortalDestroyed(long nowMillis) {
        if (current == null) return;

        if (current.areAllPortalsDestroyed()) {
            endRound(true, "all_portals_destroyed", nowMillis);
            return;
        }

        current.currentPortalOrderIndex++;
        int nextPortal = current.activePortalIndex();
        if (nextPortal >= 0) {
            PestMatchState.PortalRuntime portal = current.portals.get(nextPortal);
            if (portal != null && portal.state != PortalState.DESTROYED) {
                portal.state = PortalState.ACTIVE;
            }
        }

        // Trigger side-objective after every non-final portal kill.
        if (current.currentPortalOrderIndex >= 1 && current.currentPortalOrderIndex <= 3) {
            PestMatchState.SideObjectiveRuntime objective = sideObjectiveService.startNextObjective(current, nowMillis);
            analyticsLogger.event("side_objective_spawn", Map.of(
                    "matchId", current.matchId,
                    "type", objective == null ? String.valueOf(SideObjectiveType.CAPTURE_POINT) : String.valueOf(objective.type)
            ));
        }
    }

    private void endRound(boolean won, String reason, long nowMillis) {
        if (current == null) return;

        current.won = won;
        current.endReason = reason == null ? "" : reason;
        current.phase = MatchPhase.ROUND_END;
        current.phaseStartedAtMillis = nowMillis;

        PestRewardService.PayoutResult payouts = rewardService.applyRoundOutcome(
                Set.copyOf(current.playerUuids),
                current.tier,
                won,
                new HashMap<>(current.contribution)
        );
        this.latestPayout = payouts.byPlayer();

        historyLogger.logMatchEnd(current, current.playerUuids.size());
        analyticsLogger.event("match_end", Map.of(
                "matchId", current.matchId,
                "won", won,
                "reason", current.endReason,
                "tier", String.valueOf(current.tier)
        ));

        if (logger != null) {
            logger.atInfo().log("[PestControl] Match ended id=%s won=%s reason=%s", current.matchId, won, current.endReason);
        }
    }

    private long computeSeed(long nowMillis, List<String> players, Tier tier) {
        if (forcedSeed != null) {
            return forcedSeed;
        }
        long seed = nowMillis ^ (tier == null ? 0L : tier.ordinal() * 1_000_003L);
        for (String player : players) {
            if (player == null) continue;
            seed = seed * 31L + player.hashCode();
        }
        return seed;
    }

    private long countdownMillis() {
        PestConfig cfg = configRepository == null ? null : configRepository.get();
        int seconds = (cfg == null || cfg.timers == null) ? 20 : Math.max(0, cfg.timers.countdownSeconds);
        return seconds * 1000L;
    }

    private long matchDurationMillis() {
        PestConfig cfg = configRepository == null ? null : configRepository.get();
        int seconds = (cfg == null || cfg.timers == null) ? 20 * 60 : Math.max(1, cfg.timers.matchDurationSeconds);
        return seconds * 1000L;
    }

    private long roundEndMillis() {
        PestConfig cfg = configRepository == null ? null : configRepository.get();
        int seconds = (cfg == null || cfg.timers == null) ? 12 : Math.max(0, cfg.timers.roundEndSeconds);
        return seconds * 1000L;
    }

    private long resetMillis() {
        PestConfig cfg = configRepository == null ? null : configRepository.get();
        int seconds = (cfg == null || cfg.timers == null) ? 8 : Math.max(0, cfg.timers.resetSeconds);
        return seconds * 1000L;
    }
}
