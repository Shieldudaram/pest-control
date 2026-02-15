package com.Chris__.pest_control.arena;

import com.Chris__.pest_control.PestArenaDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ArenaRepository {

    private static final String FILE_NAME = "pest_arenas.json";

    public static final class ArenasFile {
        public int version = 1;
        public List<PestArenaDefinition> arenas = new ArrayList<>();
    }

    private final Object lock = new Object();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HytaleLogger logger;
    private final Path filePath;

    private volatile ArenasFile cached;

    public ArenaRepository(Path dataDirectory, HytaleLogger logger) {
        this.logger = logger;
        this.filePath = (dataDirectory == null) ? null : dataDirectory.resolve(FILE_NAME);
        reload();
    }

    public ArenasFile get() {
        ArenasFile c = cached;
        if (c != null) return c;
        c = normalize(new ArenasFile());
        cached = c;
        return c;
    }

    public void reload() {
        synchronized (lock) {
            if (filePath == null) {
                cached = normalize(new ArenasFile());
                return;
            }
            try {
                Files.createDirectories(filePath.getParent());
                if (!Files.exists(filePath)) {
                    ArenasFile cfg = normalize(new ArenasFile());
                    cached = cfg;
                    save(cfg);
                    return;
                }
                try (Reader reader = Files.newBufferedReader(filePath)) {
                    ArenasFile loaded = gson.fromJson(reader, ArenasFile.class);
                    cached = normalize(loaded == null ? new ArenasFile() : loaded);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.atWarning().withCause(t).log("[PestControl] Failed to load pest_arenas.json; using defaults.");
                }
                cached = normalize(new ArenasFile());
            }
        }
    }

    public void saveCurrent() {
        save(get());
    }

    public void save(ArenasFile cfg) {
        if (filePath == null || cfg == null) return;
        synchronized (lock) {
            try {
                Files.createDirectories(filePath.getParent());
                try (Writer writer = Files.newBufferedWriter(filePath)) {
                    gson.toJson(cfg, writer);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.atWarning().withCause(t).log("[PestControl] Failed to save pest_arenas.json.");
                }
            }
        }
    }

    private static ArenasFile normalize(ArenasFile cfg) {
        if (cfg == null) cfg = new ArenasFile();
        if (cfg.arenas == null) cfg.arenas = new ArrayList<>();

        List<PestArenaDefinition> normalized = new ArrayList<>();
        for (PestArenaDefinition arena : cfg.arenas) {
            if (arena == null) continue;
            arena.normalize();
            normalized.add(arena);
        }
        cfg.arenas = normalized;
        return cfg;
    }
}
