package com.Chris__.pest_control.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PestUiModeConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsToChatOnlyAndNormalizedHudPath() {
        ConfigRepository repo = new ConfigRepository(tempDir, null);
        PestConfig cfg = repo.get();

        assertEquals("CHAT_ONLY", cfg.ui.hudMode);
        assertEquals(10, cfg.ui.chatUpdateSeconds);
        assertEquals("Hud/PestControl/Status.ui", cfg.ui.hudPath);
    }

    @Test
    void normalizesLegacyHudPathAndInvalidMode() throws Exception {
        String json = """
                {
                  "ui": {
                    "hudMode": "invalid",
                    "chatUpdateSeconds": 0,
                    "hudPath": "HUD/PestControl/Status.ui"
                  }
                }
                """;
        Files.writeString(tempDir.resolve("pest_config.json"), json);

        ConfigRepository repo = new ConfigRepository(tempDir, null);
        PestConfig cfg = repo.get();

        assertEquals("CHAT_ONLY", cfg.ui.hudMode);
        assertEquals(10, cfg.ui.chatUpdateSeconds);
        assertEquals("Hud/PestControl/Status.ui", cfg.ui.hudPath);
    }
}
