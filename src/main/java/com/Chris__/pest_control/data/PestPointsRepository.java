package com.Chris__.pest_control.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class PestPointsRepository {

    private static final String FILE_NAME = "pest_points.json";

    public static final class PointsFile {
        public int version = 1;
        public Map<String, PointsEntry> players = new HashMap<>();
    }

    public static final class PointsEntry {
        public String lastKnownName = "";
        public int points = 0;
    }

    private final Object lock = new Object();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HytaleLogger logger;
    private final Path filePath;

    private volatile PointsFile cached;

    public PestPointsRepository(Path dataDirectory, HytaleLogger logger) {
        this.logger = logger;
        this.filePath = (dataDirectory == null) ? null : dataDirectory.resolve(FILE_NAME);
        reload();
    }

    public void reload() {
        synchronized (lock) {
            if (filePath == null) {
                cached = normalize(new PointsFile());
                return;
            }
            try {
                Files.createDirectories(filePath.getParent());
                if (!Files.exists(filePath)) {
                    PointsFile file = normalize(new PointsFile());
                    cached = file;
                    save(file);
                    return;
                }
                try (Reader reader = Files.newBufferedReader(filePath)) {
                    PointsFile loaded = gson.fromJson(reader, PointsFile.class);
                    cached = normalize(loaded == null ? new PointsFile() : loaded);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.atWarning().withCause(t).log("[PestControl] Failed to load pest_points.json; using defaults.");
                }
                cached = normalize(new PointsFile());
            }
        }
    }

    public void saveCurrent() {
        save(cached);
    }

    public int getPoints(String uuid) {
        if (uuid == null || uuid.isBlank()) return 0;
        synchronized (lock) {
            PointsEntry entry = getOrCreate(uuid);
            return Math.max(0, entry.points);
        }
    }

    public void setLastKnownName(String uuid, String name) {
        if (uuid == null || uuid.isBlank()) return;
        synchronized (lock) {
            PointsEntry entry = getOrCreate(uuid);
            entry.lastKnownName = name == null ? "" : name;
        }
    }

    public int addPoints(String uuid, int amount) {
        if (uuid == null || uuid.isBlank()) return 0;
        if (amount == 0) return getPoints(uuid);

        synchronized (lock) {
            PointsEntry entry = getOrCreate(uuid);
            long next = (long) entry.points + amount;
            if (next < 0) next = 0;
            if (next > Integer.MAX_VALUE) next = Integer.MAX_VALUE;
            entry.points = (int) next;
            return entry.points;
        }
    }

    public boolean spendPoints(String uuid, int amount) {
        if (uuid == null || uuid.isBlank()) return false;
        if (amount <= 0) return true;

        synchronized (lock) {
            PointsEntry entry = getOrCreate(uuid);
            if (entry.points < amount) return false;
            entry.points -= amount;
            return true;
        }
    }

    private PointsEntry getOrCreate(String uuid) {
        PointsFile file = cached;
        if (file == null) {
            file = normalize(new PointsFile());
            cached = file;
        }
        PointsEntry entry = file.players.get(uuid);
        if (entry != null) return entry;

        entry = new PointsEntry();
        file.players.put(uuid, entry);
        return entry;
    }

    private void save(PointsFile file) {
        if (filePath == null || file == null) return;
        synchronized (lock) {
            try {
                Files.createDirectories(filePath.getParent());
                try (Writer writer = Files.newBufferedWriter(filePath)) {
                    gson.toJson(file, writer);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.atWarning().withCause(t).log("[PestControl] Failed to save pest_points.json.");
                }
            }
        }
    }

    private static PointsFile normalize(PointsFile file) {
        if (file == null) file = new PointsFile();
        if (file.players == null) file.players = new HashMap<>();
        for (Map.Entry<String, PointsEntry> entry : file.players.entrySet()) {
            if (entry.getValue() == null) {
                entry.setValue(new PointsEntry());
                continue;
            }
            if (entry.getValue().lastKnownName == null) entry.getValue().lastKnownName = "";
            if (entry.getValue().points < 0) entry.getValue().points = 0;
        }
        return file;
    }
}
