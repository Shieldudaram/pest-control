package com.Chris__.pest_control.match;

import com.Chris__.pest_control.MatchPhase;
import com.Chris__.pest_control.PestMatchState;
import com.Chris__.pest_control.PortalState;
import com.Chris__.pest_control.Tier;
import com.Chris__.pest_control.arena.ArenaRepository;
import com.Chris__.pest_control.arena.ArenaService;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.data.AnalyticsLogger;
import com.Chris__.pest_control.data.MatchHistoryLogger;
import com.Chris__.pest_control.data.PestPointsRepository;
import com.Chris__.pest_control.data.PestStatsRepository;
import com.Chris__.pest_control.enemy.EnemySpawnDirector;
import com.Chris__.pest_control.interaction.InteractionService;
import com.Chris__.pest_control.objective.SideObjectiveService;
import com.Chris__.pest_control.queue.QueueService;
import com.Chris__.pest_control.reward.PestRewardService;
import com.Chris__.pest_control.testutil.TestArenaFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void enforcesSingleActiveMatchAndStartsFromQueue() {
        TestHarness harness = createService(tempDir);
        MatchService service = harness.service();

        service.forceStart(Tier.NOVICE, 0L); // no players queued yet
        assertFalse(service.hasActiveMatch());

        QueueService queue = harness.queueService();
        queue.enqueue("p1", Tier.NOVICE, 1L);

        MatchService.StartResult start = service.forceStart(Tier.NOVICE, 1L);
        assertTrue(start.started());
        assertTrue(service.hasActiveMatch());

        MatchService.StartResult second = service.forceStart(Tier.NOVICE, 2L);
        assertFalse(second.started());
    }

    @Test
    void portalOrderIsDeterministicWithSeedOverride() {
        TestHarness harnessA = createService(tempDir.resolve("a"));
        TestHarness harnessB = createService(tempDir.resolve("b"));
        MatchService serviceA = harnessA.service();
        MatchService serviceB = harnessB.service();

        serviceA.setForcedSeed(12345L);
        serviceB.setForcedSeed(12345L);

        harnessA.queueService().enqueue("p1", Tier.NOVICE, 1L);
        harnessB.queueService().enqueue("p1", Tier.NOVICE, 1L);

        assertTrue(serviceA.forceStart(Tier.NOVICE, 10L).started());
        assertTrue(serviceB.forceStart(Tier.NOVICE, 10L).started());

        List<Integer> orderA = serviceA.snapshot().state().portalOrder;
        List<Integer> orderB = serviceB.snapshot().state().portalOrder;
        assertEquals(orderA, orderB);
    }

    @Test
    void destroyingPortalsTriggersObjectiveAndWin() {
        TestHarness harness = createService(tempDir);
        MatchService service = harness.service();
        harness.queueService().enqueue("p1", Tier.NOVICE, 1L);

        assertTrue(service.forceStart(Tier.NOVICE, 10L).started());

        // Move from COUNTDOWN -> ACTIVE
        service.tick(40_000L);

        PestMatchState state = service.snapshot().state();
        assertNotNull(state);
        assertEquals(MatchPhase.ACTIVE, state.phase);

        for (int i = 0; i < 4; i++) {
            int activePortal = state.activePortalIndex();
            PestMatchState.PortalRuntime portal = state.portals.get(activePortal);
            assertNotNull(portal);
            assertEquals(PortalState.ACTIVE, portal.state);

            assertTrue(service.damagePortal(activePortal, portal.hp, "p1", 41_000L + i));
            state = service.snapshot().state();

            if (i < 3) {
                assertNotNull(state.activeObjective);
            }
        }

        state = service.snapshot().state();
        assertEquals(MatchPhase.ROUND_END, state.phase);
        assertTrue(state.won);
        assertEquals("all_portals_destroyed", state.endReason);
    }

    private record TestHarness(MatchService service, QueueService queueService) {
    }

    private static TestHarness createService(Path path) {
        ConfigRepository config = new ConfigRepository(path, null);
        ArenaRepository arenaRepository = new ArenaRepository(path, null);
        ArenaService arenaService = new ArenaService(arenaRepository, config, null);
        QueueService queueService = new QueueService();

        arenaService.upsertArena(TestArenaFactory.validArena("arena_n", Tier.NOVICE, "world"));
        arenaService.upsertArena(TestArenaFactory.validArena("arena_i", Tier.INTERMEDIATE, "world"));
        arenaService.upsertArena(TestArenaFactory.validArena("arena_v", Tier.VETERAN, "world"));

        PestPointsRepository pointsRepository = new PestPointsRepository(path, null);
        PestStatsRepository statsRepository = new PestStatsRepository(path, null);

        PestRewardService rewards = new PestRewardService(config, pointsRepository, statsRepository);

        MatchService service = new MatchService(
                config,
                arenaService,
                queueService,
                new SideObjectiveService(config),
                new EnemySpawnDirector(config),
                new InteractionService(),
                rewards,
                new MatchHistoryLogger(path, null),
                new AnalyticsLogger(path, null),
                null
        );

        service.setStartGate(arena -> ArenaService.ValidationResult.ok());
        return new TestHarness(service, queueService);
    }
}
