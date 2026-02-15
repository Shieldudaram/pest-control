package com.Chris__.pest_control.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RepositoriesRoundTripTest {

    @TempDir
    Path tempDir;

    @Test
    void pointsAndStatsPersistAcrossReload() {
        PestPointsRepository points = new PestPointsRepository(tempDir, null);
        PestStatsRepository stats = new PestStatsRepository(tempDir, null);

        points.addPoints("u1", 42);
        points.setLastKnownName("u1", "TestPlayer");
        points.saveCurrent();

        stats.setLastKnownName("u1", "TestPlayer");
        stats.recordMatch("u1", com.Chris__.pest_control.Tier.NOVICE, true, 42, true);
        stats.saveCurrent();

        PestPointsRepository pointsReloaded = new PestPointsRepository(tempDir, null);
        PestStatsRepository statsReloaded = new PestStatsRepository(tempDir, null);

        assertEquals(42, pointsReloaded.getPoints("u1"));
        var st = statsReloaded.getCopy("u1");
        assertNotNull(st);
        assertEquals(1, st.matchesPlayed);
        assertEquals(1, st.noviceWins);
        assertEquals(1, st.mvpCount);
        assertEquals(42, st.totalPointsEarned);
    }
}
