package com.Chris__.pest_control.data;

import com.Chris__.pest_control.Tier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class PestStatsRepository {

    private static final String FILE_NAME = "pest_stats.json";

    public static final class StatsFile {
        public int version = 1;
        public Map<String, PlayerStats> players = new HashMap<>();
    }

    public static final class PlayerStats {
        public String lastKnownName = "";

        public int totalPointsEarned;
        public int matchesPlayed;
        public int mvpCount;

        public int noviceWins;
        public int intermediateWins;
        public int veteranWins;

        public int noviceLosses;
        public int intermediateLosses;
        public int veteranLosses;
    }

    private final Object lock = new Object();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HytaleLogger logger;
    private final Path filePath;

    private volatile StatsFile cached;

    public PestStatsRepository(Path dataDirectory, HytaleLogger logger) {
        this.logger = logger;
        this.filePath = (dataDirectory == null) ? null : dataDirectory.resolve(FILE_NAME);
        reload();
    }

    public void reload() {
        synchronized (lock) {
            if (filePath == null) {
                cached = normalize(new StatsFile());
                return;
            }
            try {
                Files.createDirectories(filePath.getParent());
                if (!Files.exists(filePath)) {
                    StatsFile file = normalize(new StatsFile());
                    cached = file;
                    save(file);
                    return;
                }
                try (Reader reader = Files.newBufferedReader(filePath)) {
                    StatsFile loaded = gson.fromJson(reader, StatsFile.class);
                    cached = normalize(loaded == null ? new StatsFile() : loaded);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.atWarning().withCause(t).log("[PestControl] Failed to load pest_stats.json; using defaults.");
                }
                cached = normalize(new StatsFile());
            }
        }
    }

    public void saveCurrent() {
        save(cached);
    }

    public void setLastKnownName(String uuid, String name) {
        if (uuid == null || uuid.isBlank()) return;
        synchronized (lock) {
            PlayerStats stats = getOrCreate(uuid);
            stats.lastKnownName = name == null ? "" : name;
        }
    }

    public PlayerStats getCopy(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        synchronized (lock) {
            PlayerStats src = getOrCreate(uuid);
            PlayerStats dst = new PlayerStats();
            dst.lastKnownName = src.lastKnownName;
            dst.totalPointsEarned = src.totalPointsEarned;
            dst.matchesPlayed = src.matchesPlayed;
            dst.mvpCount = src.mvpCount;
            dst.noviceWins = src.noviceWins;
            dst.intermediateWins = src.intermediateWins;
            dst.veteranWins = src.veteranWins;
            dst.noviceLosses = src.noviceLosses;
            dst.intermediateLosses = src.intermediateLosses;
            dst.veteranLosses = src.veteranLosses;
            return dst;
        }
    }

    public void recordMatch(String uuid, Tier tier, boolean won, int earnedPoints, boolean mvp) {
        if (uuid == null || uuid.isBlank()) return;
        synchronized (lock) {
            PlayerStats stats = getOrCreate(uuid);
            stats.matchesPlayed++;
            stats.totalPointsEarned += Math.max(0, earnedPoints);
            if (mvp) stats.mvpCount++;

            Tier t = tier == null ? Tier.NOVICE : tier;
            if (won) {
                if (t == Tier.NOVICE) stats.noviceWins++;
                if (t == Tier.INTERMEDIATE) stats.intermediateWins++;
                if (t == Tier.VETERAN) stats.veteranWins++;
            } else {
                if (t == Tier.NOVICE) stats.noviceLosses++;
                if (t == Tier.INTERMEDIATE) stats.intermediateLosses++;
                if (t == Tier.VETERAN) stats.veteranLosses++;
            }
        }
    }

    private PlayerStats getOrCreate(String uuid) {
        StatsFile file = cached;
        if (file == null) {
            file = normalize(new StatsFile());
            cached = file;
        }

        PlayerStats stats = file.players.get(uuid);
        if (stats != null) return stats;

        stats = new PlayerStats();
        file.players.put(uuid, stats);
        return stats;
    }

    private void save(StatsFile file) {
        if (filePath == null || file == null) return;
        synchronized (lock) {
            try {
                Files.createDirectories(filePath.getParent());
                try (Writer writer = Files.newBufferedWriter(filePath)) {
                    gson.toJson(file, writer);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.atWarning().withCause(t).log("[PestControl] Failed to save pest_stats.json.");
                }
            }
        }
    }

    private static StatsFile normalize(StatsFile file) {
        if (file == null) file = new StatsFile();
        if (file.players == null) file.players = new HashMap<>();

        for (Map.Entry<String, PlayerStats> e : file.players.entrySet()) {
            if (e.getValue() == null) {
                e.setValue(new PlayerStats());
                continue;
            }
            if (e.getValue().lastKnownName == null) e.getValue().lastKnownName = "";
            if (e.getValue().totalPointsEarned < 0) e.getValue().totalPointsEarned = 0;
            if (e.getValue().matchesPlayed < 0) e.getValue().matchesPlayed = 0;
            if (e.getValue().mvpCount < 0) e.getValue().mvpCount = 0;
            if (e.getValue().noviceWins < 0) e.getValue().noviceWins = 0;
            if (e.getValue().intermediateWins < 0) e.getValue().intermediateWins = 0;
            if (e.getValue().veteranWins < 0) e.getValue().veteranWins = 0;
            if (e.getValue().noviceLosses < 0) e.getValue().noviceLosses = 0;
            if (e.getValue().intermediateLosses < 0) e.getValue().intermediateLosses = 0;
            if (e.getValue().veteranLosses < 0) e.getValue().veteranLosses = 0;
        }
        return file;
    }
}
