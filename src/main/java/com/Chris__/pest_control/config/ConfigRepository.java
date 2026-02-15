package com.Chris__.pest_control.config;

import com.Chris__.pest_control.EnemyType;
import com.Chris__.pest_control.SideObjectiveType;
import com.Chris__.pest_control.Tier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ConfigRepository {

    private static final String FILE_NAME = "pest_config.json";

    private final Object lock = new Object();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HytaleLogger logger;
    private final Path filePath;

    private volatile PestConfig cached;

    public ConfigRepository(Path dataDirectory, HytaleLogger logger) {
        this.logger = logger;
        this.filePath = (dataDirectory == null) ? null : dataDirectory.resolve(FILE_NAME);
        reload();
    }

    public PestConfig get() {
        PestConfig c = cached;
        if (c != null) return c;
        c = normalize(new PestConfig());
        cached = c;
        return c;
    }

    public void reload() {
        synchronized (lock) {
            if (filePath == null) {
                cached = normalize(new PestConfig());
                return;
            }
            try {
                Files.createDirectories(filePath.getParent());
                if (!Files.exists(filePath)) {
                    PestConfig cfg = normalize(new PestConfig());
                    cached = cfg;
                    save(cfg);
                    return;
                }
                try (Reader reader = Files.newBufferedReader(filePath)) {
                    PestConfig loaded = gson.fromJson(reader, PestConfig.class);
                    cached = normalize(loaded == null ? new PestConfig() : loaded);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.atWarning().withCause(t).log("[PestControl] Failed to load pest_config.json; using defaults.");
                }
                cached = normalize(new PestConfig());
            }
        }
    }

    public void saveCurrent() {
        save(get());
    }

    public void save(PestConfig cfg) {
        if (filePath == null || cfg == null) return;
        synchronized (lock) {
            try {
                Files.createDirectories(filePath.getParent());
                try (Writer writer = Files.newBufferedWriter(filePath)) {
                    gson.toJson(cfg, writer);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.atWarning().withCause(t).log("[PestControl] Failed to save pest_config.json.");
                }
            }
        }
    }

    private static PestConfig normalize(PestConfig cfg) {
        if (cfg == null) cfg = new PestConfig();

        if (cfg.queue == null) cfg.queue = new PestConfig.Queue();
        if (cfg.timers == null) cfg.timers = new PestConfig.Timers();
        if (cfg.tiers == null) cfg.tiers = new PestConfig.Tiers();
        if (cfg.tiers.novice == null) cfg.tiers.novice = PestConfig.TierTuning.defaultFor(Tier.NOVICE);
        if (cfg.tiers.intermediate == null) cfg.tiers.intermediate = PestConfig.TierTuning.defaultFor(Tier.INTERMEDIATE);
        if (cfg.tiers.veteran == null) cfg.tiers.veteran = PestConfig.TierTuning.defaultFor(Tier.VETERAN);
        if (cfg.enemyMappings == null) cfg.enemyMappings = new PestConfig.EnemyMappings();
        if (cfg.enemyMappings.ids == null) cfg.enemyMappings.ids = new EnumMap<>(EnemyType.class);
        if (cfg.interactions == null) cfg.interactions = new PestConfig.Interactions();
        if (cfg.sideObjectives == null) cfg.sideObjectives = new PestConfig.SideObjectives();
        if (cfg.sideObjectives.enabled == null) cfg.sideObjectives.enabled = new ArrayList<>();
        if (cfg.sideObjectives.buffs == null) cfg.sideObjectives.buffs = new PestConfig.SideObjectives.Buffs();
        if (cfg.claims == null) cfg.claims = new PestConfig.Claims();
        if (cfg.ui == null) cfg.ui = new PestConfig.Ui();

        if (cfg.queue.minPlayersToStart < 1) cfg.queue.minPlayersToStart = 1;
        if (cfg.timers.countdownSeconds < 0) cfg.timers.countdownSeconds = 20;
        if (cfg.timers.matchDurationSeconds <= 0) cfg.timers.matchDurationSeconds = 20 * 60;
        if (cfg.timers.roundEndSeconds < 0) cfg.timers.roundEndSeconds = 12;
        if (cfg.timers.resetSeconds < 0) cfg.timers.resetSeconds = 8;
        if (cfg.timers.sideObjectiveDurationSeconds <= 0) cfg.timers.sideObjectiveDurationSeconds = 60;

        normalizeTier(cfg.tiers.novice, Tier.NOVICE);
        normalizeTier(cfg.tiers.intermediate, Tier.INTERMEDIATE);
        normalizeTier(cfg.tiers.veteran, Tier.VETERAN);

        for (EnemyType type : EnemyType.values()) {
            String id = cfg.enemyMappings.ids.get(type);
            if (id == null || id.isBlank()) {
                cfg.enemyMappings.ids.put(type, new PestConfig.EnemyMappings().ids.get(type));
            }
        }

        if (cfg.interactions.defaultGateHp <= 0) cfg.interactions.defaultGateHp = 900;
        if (cfg.interactions.defaultBarricadeHp <= 0) cfg.interactions.defaultBarricadeHp = 500;
        if (cfg.interactions.defaultTurretHp <= 0) cfg.interactions.defaultTurretHp = 1300;
        if (cfg.interactions.repairPerSecond <= 0) cfg.interactions.repairPerSecond = 250;
        if (cfg.interactions.sabotagePerSecond <= 0) cfg.interactions.sabotagePerSecond = 180;
        if (cfg.interactions.turretDamagePerShot <= 0) cfg.interactions.turretDamagePerShot = 130;
        if (cfg.interactions.turretFireIntervalTicks <= 0) cfg.interactions.turretFireIntervalTicks = 20;
        if (cfg.interactions.minInteractablesPerArena < 1) cfg.interactions.minInteractablesPerArena = 20;

        List<SideObjectiveType> filtered = new ArrayList<>();
        for (SideObjectiveType type : cfg.sideObjectives.enabled) {
            if (type != null && !filtered.contains(type)) filtered.add(type);
        }
        if (filtered.isEmpty()) {
            filtered.add(SideObjectiveType.CAPTURE_POINT);
            filtered.add(SideObjectiveType.ESCORT_PAYLOAD);
            filtered.add(SideObjectiveType.DEFEND_NODE);
        }
        cfg.sideObjectives.enabled = filtered;

        if (cfg.sideObjectives.buffs.battleFervorDamageMultiplier <= 1.0) {
            cfg.sideObjectives.buffs.battleFervorDamageMultiplier = 1.15;
        }
        if (cfg.sideObjectives.buffs.fieldRepairsMultiplier <= 1.0) {
            cfg.sideObjectives.buffs.fieldRepairsMultiplier = 1.40;
        }
        if (cfg.sideObjectives.buffs.rapidResponseSpeedMultiplier <= 1.0) {
            cfg.sideObjectives.buffs.rapidResponseSpeedMultiplier = 1.20;
        }

        if (cfg.claims.mode == null || cfg.claims.mode.isBlank()) cfg.claims.mode = "STRICT_BLOCK";

        cfg.ui.hudMode = normalizeHudMode(cfg.ui.hudMode);
        if (cfg.ui.chatUpdateSeconds <= 0) cfg.ui.chatUpdateSeconds = 10;
        cfg.ui.hudPath = normalizeHudPath(cfg.ui.hudPath);
        if (cfg.ui.queuePath == null || cfg.ui.queuePath.isBlank()) cfg.ui.queuePath = "Pages/PestControl/Queue.ui";
        if (cfg.ui.shopPath == null || cfg.ui.shopPath.isBlank()) cfg.ui.shopPath = "Pages/PestControl/Shop.ui";

        return cfg;
    }

    private static void normalizeTier(PestConfig.TierTuning tuning, Tier tier) {
        PestConfig.TierTuning defaults = PestConfig.TierTuning.defaultFor(tier);
        if (tuning.tierPressure <= 0.0) tuning.tierPressure = defaults.tierPressure;
        if (tuning.flatWinPoints < 0) tuning.flatWinPoints = defaults.flatWinPoints;
        if (tuning.baseSpawnCount <= 0) tuning.baseSpawnCount = defaults.baseSpawnCount;
        if (tuning.basePortalHp <= 0) tuning.basePortalHp = defaults.basePortalHp;
        if (tuning.baseKnightHp <= 0) tuning.baseKnightHp = defaults.baseKnightHp;
    }

    private static String normalizeHudMode(String raw) {
        if (raw == null || raw.isBlank()) return "CHAT_ONLY";
        String mode = raw.trim().toUpperCase();
        if ("CUSTOM".equals(mode) || "CHAT_ONLY".equals(mode) || "OFF".equals(mode)) {
            return mode;
        }
        return "CHAT_ONLY";
    }

    private static String normalizeHudPath(String raw) {
        if (raw == null || raw.isBlank()) return "Hud/PestControl/Status.ui";
        String trimmed = raw.trim();
        if ("HUD/PestControl/Status.ui".equalsIgnoreCase(trimmed)) return "Hud/PestControl/Status.ui";
        return trimmed;
    }
}
