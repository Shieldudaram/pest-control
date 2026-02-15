package com.Chris__.pest_control.reward;

import com.Chris__.pest_control.Tier;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.data.PestPointsRepository;
import com.Chris__.pest_control.data.PestStatsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PestRewardServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void awardsFlatTierPointsAndMvp() {
        ConfigRepository configRepository = new ConfigRepository(tempDir, null);
        PestPointsRepository pointsRepository = new PestPointsRepository(tempDir, null);
        PestStatsRepository statsRepository = new PestStatsRepository(tempDir, null);

        PestRewardService rewardService = new PestRewardService(configRepository, pointsRepository, statsRepository);

        PestRewardService.PayoutResult result = rewardService.applyRoundOutcome(
                Set.of("a", "b"),
                Tier.INTERMEDIATE,
                true,
                Map.of("a", 25, "b", 10)
        );

        assertEquals(35, result.pointsFor("a"));
        assertEquals(35, result.pointsFor("b"));
        assertTrue(result.byPlayer().get("a").mvp());
        assertFalse(result.byPlayer().get("b").mvp());

        assertEquals(35, pointsRepository.getPoints("a"));
        assertEquals(35, pointsRepository.getPoints("b"));
    }
}
