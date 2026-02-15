package com.Chris__.pest_control.integration;

import com.Chris__.pest_control.PestArenaDefinition;
import com.Chris__.pest_control.arena.ArenaService;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.config.PestConfig;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SimpleClaimsGuard {

    private final ConfigRepository configRepository;
    private final HytaleLogger logger;

    private volatile Object claimManagerInstance;
    private volatile Method getChunkMethod;
    private volatile boolean attempted;

    private static final int MAX_MISSING_CHUNK_DETAILS = 24;

    public SimpleClaimsGuard(ConfigRepository configRepository, HytaleLogger logger) {
        this.configRepository = configRepository;
        this.logger = logger;
    }

    public ArenaService.ValidationResult validateArenaStrict(PestArenaDefinition arena) {
        if (arena == null) return ArenaService.ValidationResult.fail("arena missing");
        PestConfig cfg = configRepository == null ? null : configRepository.get();
        String mode = (cfg == null || cfg.claims == null || cfg.claims.mode == null)
                ? "STRICT_BLOCK"
                : cfg.claims.mode.trim().toUpperCase(Locale.ROOT);

        if (!"STRICT_BLOCK".equals(mode)) {
            return ArenaService.ValidationResult.ok();
        }

        if (!ensureLoaded()) {
            return ArenaService.ValidationResult.fail("SimpleClaims unavailable (strict mode)");
        }

        List<String> detailChunks = new ArrayList<>();
        int missingBattle = countUnclaimedChunks("battle_bounds", arena.world, arena.battleBounds, detailChunks);
        int missingLobby = countUnclaimedChunks("lobby_bounds", arena.world, arena.lobbyBounds, detailChunks);
        int missingBoat = countUnclaimedChunks("join_boat_bounds", arena.world, arena.joinBoatBounds, detailChunks);

        int missing = missingBattle + missingLobby + missingBoat;
        if (missing > 0) {
            return ArenaService.ValidationResult.fail(buildMissingChunkReason(arena, missing, detailChunks));
        }

        return ArenaService.ValidationResult.ok();
    }

    public ArenaService.ValidationResult validateAllArenas(List<PestArenaDefinition> arenas) {
        if (arenas == null || arenas.isEmpty()) return ArenaService.ValidationResult.ok();
        for (PestArenaDefinition arena : arenas) {
            if (arena == null || !arena.isValidForUse()) continue;
            ArenaService.ValidationResult vr = validateArenaStrict(arena);
            if (!vr.valid()) {
                return ArenaService.ValidationResult.fail("arena " + arena.id + ": " + vr.reason());
            }
        }
        return ArenaService.ValidationResult.ok();
    }

    private int countUnclaimedChunks(String areaName, String worldName, PestArenaDefinition.Aabb bounds, List<String> detailsOut) {
        if (worldName == null || worldName.isBlank()) return 0;
        if (bounds == null || !bounds.isValid()) return 0;

        Method getChunk = getChunkMethod;
        Object manager = claimManagerInstance;
        if (getChunk == null || manager == null) return 0;

        int minChunkX = ChunkUtil.chunkCoordinate(bounds.min[0]);
        int maxChunkX = ChunkUtil.chunkCoordinate(bounds.max[0]);
        int minChunkZ = ChunkUtil.chunkCoordinate(bounds.min[2]);
        int maxChunkZ = ChunkUtil.chunkCoordinate(bounds.max[2]);

        int missing = 0;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                try {
                    Object chunk = getChunk.invoke(manager, worldName, cx, cz);
                    if (chunk == null) {
                        missing++;
                        if (detailsOut != null && detailsOut.size() < MAX_MISSING_CHUNK_DETAILS) {
                            detailsOut.add(formatMissingChunk(areaName, cx, cz));
                        }
                    }
                } catch (Throwable t) {
                    return missing;
                }
            }
        }
        return missing;
    }

    private static String buildMissingChunkReason(PestArenaDefinition arena, int missingCount, List<String> detailChunks) {
        StringBuilder reason = new StringBuilder();
        reason.append("SimpleClaims check failed: ")
                .append(missingCount)
                .append(" chunk(s) in arena are unclaimed");

        if (arena != null && arena.world != null && !arena.world.isBlank()) {
            reason.append(" in world=").append(arena.world);
        }

        if (detailChunks != null && !detailChunks.isEmpty()) {
            reason.append(" | Missing chunks: ");
            reason.append(String.join(", ", detailChunks));
            int hidden = missingCount - detailChunks.size();
            if (hidden > 0) {
                reason.append(" ... +").append(hidden).append(" more");
            }
        }

        return reason.toString();
    }

    private static String formatMissingChunk(String areaName, int chunkX, int chunkZ) {
        int minBlockX = ChunkUtil.minBlock(chunkX);
        int maxBlockX = ChunkUtil.maxBlock(chunkX);
        int minBlockZ = ChunkUtil.minBlock(chunkZ);
        int maxBlockZ = ChunkUtil.maxBlock(chunkZ);

        String area = (areaName == null || areaName.isBlank()) ? "area" : areaName;
        return area + "(" + chunkX + "," + chunkZ + ")"
                + "[x:" + minBlockX + ".." + maxBlockX
                + ",z:" + minBlockZ + ".." + maxBlockZ + "]";
    }

    private boolean ensureLoaded() {
        if (claimManagerInstance != null && getChunkMethod != null) return true;
        if (attempted) return false;
        attempted = true;

        if (tryLoadFromClassLoader(SimpleClaimsGuard.class.getClassLoader())) {
            return true;
        }

        try {
            Object pm = PluginManager.get();
            Object plugin = findPluginBestEffort(pm,
                    "Buuz135:SimpleClaims",
                    "SimpleClaims",
                    "simpleclaims",
                    "buuz135:simpleclaims");
            if (plugin != null) {
                ClassLoader cl = plugin.getClass().getClassLoader();
                if (tryLoadFromClassLoader(cl)) return true;
            }
        } catch (Throwable ignored) {
        }

        if (logger != null) {
            logger.atInfo().log("[PestControl] SimpleClaims unavailable for strict validation.");
        }
        return false;
    }

    private boolean tryLoadFromClassLoader(ClassLoader classLoader) {
        if (classLoader == null) return false;
        try {
            Class<?> claimManagerClass = Class.forName("com.buuz135.simpleclaims.claim.ClaimManager", false, classLoader);
            Method getInstance = claimManagerClass.getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            Method getChunk = claimManagerClass.getMethod("getChunk", String.class, int.class, int.class);

            claimManagerInstance = instance;
            getChunkMethod = getChunk;
            if (logger != null) {
                logger.atInfo().log("[PestControl] SimpleClaims strict validation enabled.");
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object findPluginBestEffort(Object pluginManager, String... names) {
        if (pluginManager == null || names == null) return null;

        String[] methodNames = new String[]{"getPlugin", "getPluginByName", "getPluginOrNull"};
        for (String name : names) {
            for (String methodName : methodNames) {
                try {
                    Method m = pluginManager.getClass().getMethod(methodName, String.class);
                    Object p = m.invoke(pluginManager, name);
                    if (p != null) return p;
                } catch (Throwable ignored) {
                }
            }
        }

        try {
            Method m = pluginManager.getClass().getMethod("getPlugins");
            Object plugins = m.invoke(pluginManager);
            if (plugins instanceof Iterable<?> iterable) {
                for (Object p : iterable) {
                    if (p == null) continue;
                    String asString = p.toString().toLowerCase(Locale.ROOT);
                    for (String wanted : names) {
                        if (wanted != null && asString.contains(wanted.toLowerCase(Locale.ROOT))) {
                            return p;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }
}
