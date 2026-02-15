package com.Chris__.pest_control.match;

import com.Chris__.pest_control.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScalingModelTest {

    @Test
    void matchesExpectedFormulas() {
        assertEquals(0.79, ScalingModel.playerScale(2), 1e-9);
        assertEquals(1.51, ScalingModel.playerScale(8), 1e-9);

        assertEquals(8.0 / 0.79, ScalingModel.spawnIntervalSeconds(2, 8.0), 1e-9);
        assertEquals(Math.round(3 * 0.79), ScalingModel.spawnCountPerWave(Tier.INTERMEDIATE, 2));

        assertEquals((int) Math.round(3200 * (0.75 + 0.08 * 2)), ScalingModel.portalHp(Tier.INTERMEDIATE, 2));
        assertEquals((int) Math.round(3600 * (0.80 + 0.07 * 2)), ScalingModel.knightHp(Tier.INTERMEDIATE, 2));
    }
}
