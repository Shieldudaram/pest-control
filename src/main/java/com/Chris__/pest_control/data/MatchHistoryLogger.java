package com.Chris__.pest_control.data;

import com.Chris__.pest_control.PestMatchState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MatchHistoryLogger {

    private static final String FILE_NAME = "pest_match_history.ndjson";

    private final Gson gson = new GsonBuilder().create();
    private final HytaleLogger logger;
    private final Path filePath;

    public MatchHistoryLogger(Path dataDirectory, HytaleLogger logger) {
        this.logger = logger;
        this.filePath = (dataDirectory == null) ? null : dataDirectory.resolve(FILE_NAME);
    }

    public void logMatchEnd(PestMatchState state, int participants) {
        if (state == null) return;
        if (filePath == null) return;

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("matchId", state.matchId);
        entry.put("tier", String.valueOf(state.tier));
        entry.put("phase", String.valueOf(state.phase));
        entry.put("won", state.won);
        entry.put("endReason", state.endReason);
        entry.put("players", participants);
        entry.put("seed", state.seed);
        entry.put("voidKnightHp", state.voidKnightHp);

        append(entry);
    }

    private void append(Map<String, Object> payload) {
        try {
            Files.createDirectories(filePath.getParent());
            String line = gson.toJson(payload) + "\n";
            Files.writeString(filePath, line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        } catch (Throwable t) {
            if (logger != null) {
                logger.atWarning().withCause(t).log("[PestControl] Failed to append match history entry.");
            }
        }
    }
}
