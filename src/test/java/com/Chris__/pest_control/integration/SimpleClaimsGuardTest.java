package com.Chris__.pest_control.integration;

import com.Chris__.pest_control.Tier;
import com.Chris__.pest_control.arena.ArenaService;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.testutil.TestArenaFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SimpleClaimsGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void strictModeFailsWhenSimpleClaimsUnavailable() {
        ConfigRepository configRepository = new ConfigRepository(tempDir, null);

        SimpleClaimsGuard guard = new SimpleClaimsGuard(configRepository, null);
        ArenaService.ValidationResult result = guard.validateArenaStrict(
                TestArenaFactory.validArena("alpha", Tier.NOVICE, "test_world")
        );

        assertFalse(result.valid());
        assertTrue(result.reason().toLowerCase().contains("simpleclaims"));
    }
}
