package com.Chris__.pest_control.arena;

import com.Chris__.pest_control.PestArenaDefinition;
import com.Chris__.pest_control.Tier;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.config.PestConfig;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaService {

    public record Mark(int x, int y, int z) {
    }

    public record ValidationResult(boolean valid, String reason) {
        public static ValidationResult ok() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult fail(String reason) {
            return new ValidationResult(false, reason == null ? "unknown" : reason);
        }
    }

    private final Object lock = new Object();

    private final ArenaRepository repository;
    private final ConfigRepository configRepository;
    private final HytaleLogger logger;

    private volatile Map<String, PestArenaDefinition> byId = Collections.emptyMap();

    private final Map<String, String> selectedArenaByAdmin = new ConcurrentHashMap<>();
    private final Map<String, Mark> mark1ByAdmin = new ConcurrentHashMap<>();
    private final Map<String, Mark> mark2ByAdmin = new ConcurrentHashMap<>();

    public ArenaService(ArenaRepository repository, ConfigRepository configRepository, HytaleLogger logger) {
        this.repository = repository;
        this.configRepository = configRepository;
        this.logger = logger;
        reloadFromRepo();
    }

    public void reloadFromRepo() {
        if (repository == null) return;

        repository.reload();
        ArenaRepository.ArenasFile file = repository.get();

        Map<String, PestArenaDefinition> map = new HashMap<>();
        if (file != null && file.arenas != null) {
            for (PestArenaDefinition arena : file.arenas) {
                if (arena == null) continue;
                arena.normalize();
                if (arena.id == null || arena.id.isBlank()) continue;
                map.put(arena.id.toLowerCase(Locale.ROOT), arena);
            }
        }

        synchronized (lock) {
            byId = map;
        }
    }

    public List<PestArenaDefinition> listArenas() {
        synchronized (lock) {
            return new ArrayList<>(byId.values());
        }
    }

    public PestArenaDefinition getArena(String id) {
        if (id == null || id.isBlank()) return null;
        synchronized (lock) {
            return byId.get(id.toLowerCase(Locale.ROOT));
        }
    }

    public PestArenaDefinition getFirstArenaForTier(Tier tier) {
        if (tier == null) return null;
        synchronized (lock) {
            for (PestArenaDefinition arena : byId.values()) {
                if (arena == null || arena.tier != tier) continue;
                if (arena.isValidForUse()) return arena;
            }
        }
        return null;
    }

    public boolean upsertArena(PestArenaDefinition arena) {
        if (arena == null) return false;
        arena.normalize();
        if (arena.id == null || arena.id.isBlank()) return false;

        ArenaRepository.ArenasFile file = repository.get();
        if (file == null) return false;

        boolean replaced = false;
        for (int i = 0; i < file.arenas.size(); i++) {
            PestArenaDefinition existing = file.arenas.get(i);
            if (existing == null || existing.id == null) continue;
            if (existing.id.equalsIgnoreCase(arena.id)) {
                file.arenas.set(i, arena);
                replaced = true;
                break;
            }
        }

        if (!replaced) {
            file.arenas.add(arena);
        }

        repository.saveCurrent();
        reloadFromRepo();
        return true;
    }

    public boolean deleteArena(String id) {
        if (id == null || id.isBlank()) return false;
        ArenaRepository.ArenasFile file = repository.get();
        if (file == null || file.arenas == null) return false;

        boolean removed = file.arenas.removeIf(a -> a != null && a.id != null && a.id.equalsIgnoreCase(id));
        if (!removed) return false;

        repository.saveCurrent();
        reloadFromRepo();
        return true;
    }

    public ValidationResult validateArena(PestArenaDefinition arena) {
        if (arena == null) return ValidationResult.fail("arena missing");
        arena.normalize();
        if (!arena.isValidForUse()) return ValidationResult.fail("arena has incomplete bounds/spawns/portals");

        PestConfig cfg = configRepository == null ? null : configRepository.get();
        int minInteractables = (cfg == null || cfg.interactions == null) ? 20 : cfg.interactions.minInteractablesPerArena;

        int interactionCount = arena.gates.size() + arena.barricades.size() + arena.turrets.size()
                + arena.repairStations.size() + arena.objectiveAnchors.size();
        if (interactionCount < minInteractables) {
            return ValidationResult.fail("arena requires at least " + minInteractables + " interactables, found " + interactionCount);
        }

        if (arena.gates.size() < 8) return ValidationResult.fail("arena requires at least 8 gates");
        if (arena.barricades.size() < 8) return ValidationResult.fail("arena requires at least 8 barricades");
        if (arena.turrets.size() < 2) return ValidationResult.fail("arena requires at least 2 turrets");
        if (arena.repairStations.size() < 4) return ValidationResult.fail("arena requires at least 4 repair stations");
        if (arena.objectiveAnchors.size() < 3) return ValidationResult.fail("arena requires at least 3 objective anchors");

        return ValidationResult.ok();
    }

    public ValidationResult validateArenaById(String arenaId) {
        PestArenaDefinition arena = getArena(arenaId);
        if (arena == null) return ValidationResult.fail("arena not found");
        return validateArena(arena);
    }

    public void selectArenaForAdmin(String adminUuid, String arenaId) {
        if (adminUuid == null || adminUuid.isBlank()) return;
        if (arenaId == null || arenaId.isBlank()) {
            selectedArenaByAdmin.remove(adminUuid);
            return;
        }
        selectedArenaByAdmin.put(adminUuid, arenaId.toLowerCase(Locale.ROOT));
    }

    public PestArenaDefinition selectedArenaForAdmin(String adminUuid) {
        if (adminUuid == null || adminUuid.isBlank()) return null;
        String arenaId = selectedArenaByAdmin.get(adminUuid);
        if (arenaId == null || arenaId.isBlank()) return null;
        return getArena(arenaId);
    }

    public void setMark1(String adminUuid, Mark mark) {
        if (adminUuid == null || adminUuid.isBlank()) return;
        if (mark == null) {
            mark1ByAdmin.remove(adminUuid);
            return;
        }
        mark1ByAdmin.put(adminUuid, mark);
    }

    public void setMark2(String adminUuid, Mark mark) {
        if (adminUuid == null || adminUuid.isBlank()) return;
        if (mark == null) {
            mark2ByAdmin.remove(adminUuid);
            return;
        }
        mark2ByAdmin.put(adminUuid, mark);
    }

    public Mark getMark1(String adminUuid) {
        if (adminUuid == null || adminUuid.isBlank()) return null;
        return mark1ByAdmin.get(adminUuid);
    }

    public Mark getMark2(String adminUuid) {
        if (adminUuid == null || adminUuid.isBlank()) return null;
        return mark2ByAdmin.get(adminUuid);
    }

    public boolean applyBoundsFromMarks(String adminUuid, PestArenaDefinition.Aabb target) {
        if (target == null) return false;
        Mark m1 = getMark1(adminUuid);
        Mark m2 = getMark2(adminUuid);
        if (m1 == null || m2 == null) return false;

        target.min = new int[]{m1.x(), m1.y(), m1.z()};
        target.max = new int[]{m2.x(), m2.y(), m2.z()};
        target.normalize();
        return true;
    }

    public PestArenaDefinition createArena(String id, Tier tier, String world) {
        if (id == null || id.isBlank()) return null;
        if (world == null || world.isBlank()) return null;
        if (getArena(id) != null) return null;

        PestArenaDefinition arena = new PestArenaDefinition();
        arena.id = id;
        arena.tier = tier == null ? Tier.NOVICE : tier;
        arena.world = world;
        arena.normalize();
        upsertArena(arena);

        if (logger != null) {
            logger.atInfo().log("[PestControl] Created arena id=%s tier=%s world=%s", arena.id, arena.tier, arena.world);
        }
        return arena;
    }
}
