package com.Chris__.pest_control.commands;

import com.Chris__.pest_control.PestArenaDefinition;
import com.Chris__.pest_control.PestMatchState;
import com.Chris__.pest_control.SideObjectiveType;
import com.Chris__.pest_control.Tier;
import com.Chris__.pest_control.arena.ArenaService;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.data.PestPointsRepository;
import com.Chris__.pest_control.data.PestShopConfig;
import com.Chris__.pest_control.data.PestShopRepository;
import com.Chris__.pest_control.data.PestStatsRepository;
import com.Chris__.pest_control.integration.SimpleClaimsGuard;
import com.Chris__.pest_control.match.MatchService;
import com.Chris__.pest_control.queue.QueueService;
import com.Chris__.pest_control.util.InventoryUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Locale;

public final class PestControlCommand extends CommandBase {

    private static final Message MSG_USAGE = Message.raw("Usage: /pc <help|join|leave|queue|status|points|stats|shop|admin>");

    private final QueueService queueService;
    private final MatchService matchService;
    private final ArenaService arenaService;
    private final ConfigRepository configRepository;
    private final PestPointsRepository pointsRepository;
    private final PestStatsRepository statsRepository;
    private final PestShopRepository shopRepository;
    private final SimpleClaimsGuard claimsGuard;

    public PestControlCommand(QueueService queueService,
                              MatchService matchService,
                              ArenaService arenaService,
                              ConfigRepository configRepository,
                              PestPointsRepository pointsRepository,
                              PestStatsRepository statsRepository,
                              PestShopRepository shopRepository,
                              SimpleClaimsGuard claimsGuard) {
        super("pc", "Pest Control commands.");
        this.addAliases("pestcontrol");
        this.setAllowsExtraArguments(true);
        this.setPermissionGroup(GameMode.Adventure);
        this.queueService = queueService;
        this.matchService = matchService;
        this.arenaService = arenaService;
        this.configRepository = configRepository;
        this.pointsRepository = pointsRepository;
        this.statsRepository = statsRepository;
        this.shopRepository = shopRepository;
        this.claimsGuard = claimsGuard;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        String[] args = splitArgs(ctx.getInputString());
        if (args.length < 2) {
            printHelp(ctx);
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        if (!"help".equals(sub) && !"admin".equals(sub) && !hasPermissionOrOperator(ctx, "pestcontrol.use")) {
            ctx.sendMessage(Message.raw("[PestControl] Missing permission: pestcontrol.use"));
            return;
        }
        switch (sub) {
            case "help" -> printHelp(ctx);
            case "join" -> cmdJoin(ctx, args);
            case "leave" -> cmdLeave(ctx);
            case "queue" -> cmdQueue(ctx);
            case "status" -> cmdStatus(ctx);
            case "points" -> cmdPoints(ctx);
            case "stats" -> cmdStats(ctx);
            case "shop" -> cmdShop(ctx, args);
            case "admin" -> cmdAdmin(ctx, args);
            default -> ctx.sendMessage(MSG_USAGE);
        }
    }

    private void cmdJoin(CommandContext ctx, String[] args) {
        String uuid = senderUuid(ctx);
        if (uuid == null) {
            ctx.sendMessage(Message.raw("[PestControl] Players only."));
            return;
        }
        if (args.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /pc join <novice|intermediate|veteran>"));
            return;
        }

        Tier tier = Tier.parse(args[2]);
        if (tier == null) {
            ctx.sendMessage(Message.raw("[PestControl] Unknown tier: " + args[2]));
            return;
        }

        MatchService.MatchSnapshot snapshot = matchService.snapshot();
        PestMatchState state = snapshot.state();
        if (state != null && state.playerUuids.contains(uuid)) {
            ctx.sendMessage(Message.raw("[PestControl] You are already in the active match."));
            return;
        }

        boolean ok = queueService.enqueue(uuid, tier, System.currentTimeMillis());
        if (!ok) {
            ctx.sendMessage(Message.raw("[PestControl] You are already queued."));
            return;
        }

        ctx.sendMessage(Message.raw("[PestControl] Joined " + tier + " queue."));
    }

    private void cmdLeave(CommandContext ctx) {
        String uuid = senderUuid(ctx);
        if (uuid == null) {
            ctx.sendMessage(Message.raw("[PestControl] Players only."));
            return;
        }

        boolean removed = queueService.remove(uuid);
        if (removed) {
            ctx.sendMessage(Message.raw("[PestControl] You left the queue."));
        } else {
            ctx.sendMessage(Message.raw("[PestControl] You are not in a queue."));
        }
    }

    private void cmdQueue(CommandContext ctx) {
        QueueService.QueueSnapshot snapshot = queueService.snapshot();
        ctx.sendMessage(Message.raw("[PestControl] Queue: N=" + snapshot.novice() + " I=" + snapshot.intermediate() + " V=" + snapshot.veteran()));
    }

    private void cmdStatus(CommandContext ctx) {
        MatchService.MatchSnapshot snapshot = matchService.snapshot();
        PestMatchState state = snapshot.state();
        if (state == null) {
            ctx.sendMessage(Message.raw("[PestControl] No active match."));
            cmdQueue(ctx);
            return;
        }

        int portalsRemaining = 0;
        for (int i = 0; i < 4; i++) {
            PestMatchState.PortalRuntime portal = state.portals.get(i);
            if (portal != null && portal.state != com.Chris__.pest_control.PortalState.DESTROYED) portalsRemaining++;
        }

        ctx.sendMessage(Message.raw("[PestControl] Match " + state.matchId + " | " + state.tier + " | " + state.phase));
        ctx.sendMessage(Message.raw("[PestControl] Knight HP: " + state.voidKnightHp + "/" + state.voidKnightMaxHp + " | Remaining portals: " + portalsRemaining));
        if (state.activeObjective != null) {
            ctx.sendMessage(Message.raw("[PestControl] Side objective: " + state.activeObjective.type + (state.activeObjective.completed ? " (completed)" : " (active)")));
        }
    }

    private void cmdPoints(CommandContext ctx) {
        String uuid = senderUuid(ctx);
        if (uuid == null) {
            ctx.sendMessage(Message.raw("[PestControl] Players only."));
            return;
        }

        int points = pointsRepository.getPoints(uuid);
        ctx.sendMessage(Message.raw("[PestControl] Pest points: " + points));
    }

    private void cmdStats(CommandContext ctx) {
        String uuid = senderUuid(ctx);
        if (uuid == null) {
            ctx.sendMessage(Message.raw("[PestControl] Players only."));
            return;
        }

        PestStatsRepository.PlayerStats stats = statsRepository.getCopy(uuid);
        if (stats == null) {
            ctx.sendMessage(Message.raw("[PestControl] No stats yet."));
            return;
        }

        ctx.sendMessage(Message.raw("[PestControl] Stats: matches=" + stats.matchesPlayed + " mvp=" + stats.mvpCount + " pointsEarned=" + stats.totalPointsEarned));
        ctx.sendMessage(Message.raw("[PestControl] W/L Novice=" + stats.noviceWins + "/" + stats.noviceLosses
                + " Intermediate=" + stats.intermediateWins + "/" + stats.intermediateLosses
                + " Veteran=" + stats.veteranWins + "/" + stats.veteranLosses));
    }

    private void cmdShop(CommandContext ctx, String[] args) {
        if (args.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /pc shop <list|buy id [qty]>"));
            return;
        }

        String action = args[2].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            shopRepository.reload();
            PestShopConfig cfg = shopRepository.get();
            if (cfg.items == null || cfg.items.isEmpty()) {
                ctx.sendMessage(Message.raw("[PestControl] Shop is empty."));
                return;
            }
            ctx.sendMessage(Message.raw("[PestControl] Shop items:"));
            for (PestShopConfig.ShopItem item : cfg.items) {
                if (item == null || !item.enabled) continue;
                ctx.sendMessage(Message.raw(" - " + item.name + " (" + item.id + "): " + item.cost + " pts"));
            }
            return;
        }

        if (!"buy".equals(action)) {
            ctx.sendMessage(Message.raw("Usage: /pc shop <list|buy id [qty]>"));
            return;
        }

        String uuid = senderUuid(ctx);
        Player player = ctx.senderAs(Player.class);
        if (uuid == null || player == null) {
            ctx.sendMessage(Message.raw("[PestControl] Players only."));
            return;
        }

        if (args.length < 4) {
            ctx.sendMessage(Message.raw("Usage: /pc shop buy <id> [qty]"));
            return;
        }

        String id = args[3];
        int qty = 1;
        if (args.length >= 5) {
            try {
                qty = Math.max(1, Integer.parseInt(args[4]));
            } catch (Throwable ignored) {
                qty = 1;
            }
        }

        shopRepository.reload();
        PestShopConfig.ShopItem item = shopRepository.find(id);
        if (item == null || !item.enabled) {
            ctx.sendMessage(Message.raw("[PestControl] Unknown shop item: " + id));
            return;
        }

        long totalCostL = (long) Math.max(0, item.cost) * qty;
        if (totalCostL > Integer.MAX_VALUE) {
            ctx.sendMessage(Message.raw("[PestControl] Quantity too high."));
            return;
        }
        int totalCost = (int) totalCostL;

        if (!pointsRepository.spendPoints(uuid, totalCost)) {
            ctx.sendMessage(Message.raw("[PestControl] Not enough points."));
            return;
        }

        int amountPerItem = Math.max(1, item.amount);
        long totalAmountL = (long) amountPerItem * qty;
        if (totalAmountL > Integer.MAX_VALUE) {
            pointsRepository.addPoints(uuid, totalCost);
            ctx.sendMessage(Message.raw("[PestControl] Quantity too high."));
            return;
        }

        ItemStack stack;
        try {
            stack = new ItemStack(item.itemId, (int) totalAmountL);
        } catch (Throwable t) {
            stack = null;
        }
        if (stack == null || stack.isEmpty()) {
            pointsRepository.addPoints(uuid, totalCost);
            ctx.sendMessage(Message.raw("[PestControl] Failed to create item from id: " + item.itemId));
            return;
        }

        if (!InventoryUtil.tryGive(player, stack)) {
            pointsRepository.addPoints(uuid, totalCost);
            ctx.sendMessage(Message.raw("[PestControl] Inventory full."));
            return;
        }

        try {
            player.sendInventory();
        } catch (Throwable ignored) {
        }

        pointsRepository.saveCurrent();
        ctx.sendMessage(Message.raw("[PestControl] Purchased " + item.name + " x" + qty + " for " + totalCost + " points."));
    }

    private void cmdAdmin(CommandContext ctx, String[] args) {
        if (!isAdmin(ctx)) {
            ctx.sendMessage(Message.raw("[PestControl] Missing permission: pestcontrol.admin"));
            return;
        }

        if (args.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /pc admin <reload|start|stop|queue|arena|mark1|mark2|seed> ..."));
            return;
        }

        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "reload" -> adminReload(ctx);
            case "start" -> adminStart(ctx, args);
            case "stop" -> adminStop(ctx);
            case "queue" -> adminQueue(ctx, args);
            case "arena" -> adminArena(ctx, args);
            case "mark1" -> adminMark(ctx, true);
            case "mark2" -> adminMark(ctx, false);
            case "seed" -> adminSeed(ctx, args);
            default -> ctx.sendMessage(Message.raw("Usage: /pc admin <reload|start|stop|queue|arena|mark1|mark2|seed> ..."));
        }
    }

    private void adminReload(CommandContext ctx) {
        configRepository.reload();
        arenaService.reloadFromRepo();
        shopRepository.reload();

        ArenaService.ValidationResult claimsCheck = claimsGuard.validateAllArenas(arenaService.listArenas());
        if (!claimsCheck.valid()) {
            ctx.sendMessage(Message.raw("[PestControl] Reloaded with strict-claims warning: " + claimsCheck.reason()));
            return;
        }

        ctx.sendMessage(Message.raw("[PestControl] Reloaded config/arenas/shop."));
    }

    private void adminStart(CommandContext ctx, String[] args) {
        Tier tier = null;
        if (args.length >= 4) tier = Tier.parse(args[3]);

        MatchService.StartResult result = matchService.forceStart(tier, System.currentTimeMillis());
        if (result.started()) {
            ctx.sendMessage(Message.raw("[PestControl] Match start requested."));
        } else {
            ctx.sendMessage(Message.raw("[PestControl] Start blocked: " + result.reason()));
        }
    }

    private void adminStop(CommandContext ctx) {
        boolean stopped = matchService.forceStop("admin_stop", System.currentTimeMillis());
        if (stopped) {
            ctx.sendMessage(Message.raw("[PestControl] Match stop requested."));
        } else {
            ctx.sendMessage(Message.raw("[PestControl] No active match."));
        }
    }

    private void adminQueue(CommandContext ctx, String[] args) {
        if (args.length < 4) {
            ctx.sendMessage(Message.raw("Usage: /pc admin queue <list|clear <all|tier>>"));
            return;
        }

        String action = args[3].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            cmdQueue(ctx);
            return;
        }

        if (!"clear".equals(action)) {
            ctx.sendMessage(Message.raw("Usage: /pc admin queue <list|clear <all|tier>>"));
            return;
        }

        if (args.length < 5) {
            ctx.sendMessage(Message.raw("Usage: /pc admin queue clear <all|tier>"));
            return;
        }

        String target = args[4].toLowerCase(Locale.ROOT);
        if ("all".equals(target)) {
            queueService.clearAll();
            ctx.sendMessage(Message.raw("[PestControl] Cleared all queues."));
            return;
        }

        Tier tier = Tier.parse(target);
        if (tier == null) {
            ctx.sendMessage(Message.raw("[PestControl] Unknown tier: " + target));
            return;
        }

        queueService.clearTier(tier);
        ctx.sendMessage(Message.raw("[PestControl] Cleared queue: " + tier));
    }

    private void adminMark(CommandContext ctx, boolean mark1) {
        Player sender = ctx.senderAs(Player.class);
        String uuid = senderUuid(ctx);
        if (sender == null || uuid == null) {
            ctx.sendMessage(Message.raw("[PestControl] Players only."));
            return;
        }

        var pos = sender.getPlayerRef().getTransform().getPosition();
        int x = (int) Math.floor(pos.getX());
        int y = (int) Math.floor(pos.getY());
        int z = (int) Math.floor(pos.getZ());

        if (mark1) {
            arenaService.setMark1(uuid, new ArenaService.Mark(x, y, z));
            ctx.sendMessage(Message.raw("[PestControl] mark1 set to " + x + "," + y + "," + z));
        } else {
            arenaService.setMark2(uuid, new ArenaService.Mark(x, y, z));
            ctx.sendMessage(Message.raw("[PestControl] mark2 set to " + x + "," + y + "," + z));
        }
    }

    private void adminSeed(CommandContext ctx, String[] args) {
        if (args.length < 4) {
            ctx.sendMessage(Message.raw("Usage: /pc admin seed <long|reset>"));
            return;
        }

        String raw = args[3];
        if ("reset".equalsIgnoreCase(raw)) {
            matchService.setForcedSeed(null);
            ctx.sendMessage(Message.raw("[PestControl] Seed override cleared."));
            return;
        }

        try {
            long seed = Long.parseLong(raw);
            matchService.setForcedSeed(seed);
            ctx.sendMessage(Message.raw("[PestControl] Seed override set: " + seed));
        } catch (Throwable t) {
            ctx.sendMessage(Message.raw("[PestControl] Invalid seed."));
        }
    }

    private void adminArena(CommandContext ctx, String[] args) {
        if (args.length < 4) {
            ctx.sendMessage(Message.raw("Usage: /pc admin arena <list|create|delete|select|set|portal|gate|barricade|turret|repair|objective|save|validate> ..."));
            return;
        }

        String action = args[3].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> arenaList(ctx);
            case "create" -> arenaCreate(ctx, args);
            case "delete" -> arenaDelete(ctx, args);
            case "select" -> arenaSelect(ctx, args);
            case "set" -> arenaSet(ctx, args);
            case "portal" -> arenaPortalSet(ctx, args);
            case "gate" -> arenaAddNode(ctx, args, "gate");
            case "barricade" -> arenaAddNode(ctx, args, "barricade");
            case "turret" -> arenaAddNode(ctx, args, "turret");
            case "repair", "repair_station" -> arenaAddNode(ctx, args, "repair");
            case "objective" -> arenaObjectiveAdd(ctx, args);
            case "save" -> arenaSave(ctx);
            case "validate" -> arenaValidate(ctx);
            default -> ctx.sendMessage(Message.raw("Usage: /pc admin arena <list|create|delete|select|set|portal|gate|barricade|turret|repair|objective|save|validate> ..."));
        }
    }

    private void arenaList(CommandContext ctx) {
        arenaService.reloadFromRepo();
        var arenas = arenaService.listArenas();
        ctx.sendMessage(Message.raw("[PestControl] Arenas: " + arenas.size()));
        for (PestArenaDefinition arena : arenas) {
            if (arena == null) continue;
            ctx.sendMessage(Message.raw(" - " + arena.id + " tier=" + arena.tier + " world=" + arena.world));
        }
    }

    private void arenaCreate(CommandContext ctx, String[] args) {
        if (args.length < 7) {
            ctx.sendMessage(Message.raw("Usage: /pc admin arena create <id> <tier> <world>"));
            return;
        }
        String id = args[4];
        Tier tier = Tier.parse(args[5]);
        String world = args[6];
        if (tier == null) {
            ctx.sendMessage(Message.raw("[PestControl] Invalid tier."));
            return;
        }

        PestArenaDefinition arena = arenaService.createArena(id, tier, world);
        if (arena == null) {
            ctx.sendMessage(Message.raw("[PestControl] Failed to create arena. It may already exist."));
            return;
        }

        String uuid = senderUuid(ctx);
        if (uuid != null) arenaService.selectArenaForAdmin(uuid, arena.id);
        ctx.sendMessage(Message.raw("[PestControl] Created arena " + arena.id + " and selected it."));
    }

    private void arenaDelete(CommandContext ctx, String[] args) {
        if (args.length < 5) {
            ctx.sendMessage(Message.raw("Usage: /pc admin arena delete <id>"));
            return;
        }
        boolean ok = arenaService.deleteArena(args[4]);
        if (ok) {
            ctx.sendMessage(Message.raw("[PestControl] Arena deleted."));
        } else {
            ctx.sendMessage(Message.raw("[PestControl] Arena not found."));
        }
    }

    private void arenaSelect(CommandContext ctx, String[] args) {
        String uuid = senderUuid(ctx);
        if (uuid == null) {
            ctx.sendMessage(Message.raw("[PestControl] Players only."));
            return;
        }
        if (args.length < 5) {
            ctx.sendMessage(Message.raw("Usage: /pc admin arena select <id>"));
            return;
        }

        PestArenaDefinition arena = arenaService.getArena(args[4]);
        if (arena == null) {
            ctx.sendMessage(Message.raw("[PestControl] Arena not found."));
            return;
        }

        arenaService.selectArenaForAdmin(uuid, arena.id);
        ctx.sendMessage(Message.raw("[PestControl] Selected arena: " + arena.id));
    }

    private void arenaSet(CommandContext ctx, String[] args) {
        if (args.length < 5) {
            ctx.sendMessage(Message.raw("Usage: /pc admin arena set <battle_bounds|lobby_bounds|join_boat_bounds|player_spawn|respawn_spawn|knight_spawn>"));
            return;
        }

        Player sender = ctx.senderAs(Player.class);
        String uuid = senderUuid(ctx);
        PestArenaDefinition arena = selectedArenaForAdmin(ctx);
        if (sender == null || uuid == null || arena == null) return;

        String field = args[4].toLowerCase(Locale.ROOT);
        boolean changed = false;

        if ("battle_bounds".equals(field)) {
            changed = arenaService.applyBoundsFromMarks(uuid, arena.battleBounds);
        } else if ("lobby_bounds".equals(field)) {
            changed = arenaService.applyBoundsFromMarks(uuid, arena.lobbyBounds);
        } else if ("join_boat_bounds".equals(field)) {
            changed = arenaService.applyBoundsFromMarks(uuid, arena.joinBoatBounds);
        } else if ("player_spawn".equals(field)) {
            setSpawnFromPlayer(arena.playerSpawn, sender);
            changed = true;
        } else if ("respawn_spawn".equals(field)) {
            setSpawnFromPlayer(arena.respawnSpawn, sender);
            changed = true;
        } else if ("knight_spawn".equals(field)) {
            setSpawnFromPlayer(arena.knightSpawn, sender);
            changed = true;
        }

        if (!changed) {
            ctx.sendMessage(Message.raw("[PestControl] Failed to set field. Ensure marks are set for bounds."));
            return;
        }

        arena.normalize();
        arenaService.upsertArena(arena);
        ctx.sendMessage(Message.raw("[PestControl] Updated arena field: " + field));
    }

    private void arenaPortalSet(CommandContext ctx, String[] args) {
        if (args.length < 7) {
            ctx.sendMessage(Message.raw("Usage: /pc admin arena portal set <1|2|3|4> <core|spawn>"));
            return;
        }

        Player sender = ctx.senderAs(Player.class);
        PestArenaDefinition arena = selectedArenaForAdmin(ctx);
        if (sender == null || arena == null) return;

        String mode = args[4].toLowerCase(Locale.ROOT);
        if (!"set".equals(mode)) {
            ctx.sendMessage(Message.raw("Usage: /pc admin arena portal set <1|2|3|4> <core|spawn>"));
            return;
        }

        int idx;
        try {
            idx = Integer.parseInt(args[5]) - 1;
        } catch (Throwable t) {
            idx = -1;
        }
        if (idx < 0 || idx > 3) {
            ctx.sendMessage(Message.raw("[PestControl] Portal index must be 1..4."));
            return;
        }

        String target = args[6].toLowerCase(Locale.ROOT);
        PestArenaDefinition.PortalDefinition portal = arena.portals.get(idx);
        if (portal == null) {
            ctx.sendMessage(Message.raw("[PestControl] Portal missing at index " + (idx + 1)));
            return;
        }

        if ("core".equals(target)) {
            setSpawnFromPlayer(portal.coreSpawn, sender);
        } else if ("spawn".equals(target)) {
            setSpawnFromPlayer(portal.enemySpawn, sender);
        } else {
            ctx.sendMessage(Message.raw("[PestControl] Target must be core or spawn."));
            return;
        }

        arena.normalize();
        arenaService.upsertArena(arena);
        ctx.sendMessage(Message.raw("[PestControl] Updated portal " + (idx + 1) + " " + target + " spawn."));
    }

    private void arenaAddNode(CommandContext ctx, String[] args, String type) {
        if (args.length < 6) {
            ctx.sendMessage(Message.raw("Usage: /pc admin arena " + type + " add <id>"));
            return;
        }

        Player sender = ctx.senderAs(Player.class);
        PestArenaDefinition arena = selectedArenaForAdmin(ctx);
        if (sender == null || arena == null) return;

        String action = args[4].toLowerCase(Locale.ROOT);
        if (!"add".equals(action)) {
            ctx.sendMessage(Message.raw("Usage: /pc admin arena " + type + " add <id>"));
            return;
        }

        String id = args[5];
        if (id == null || id.isBlank()) {
            ctx.sendMessage(Message.raw("[PestControl] Missing id."));
            return;
        }

        if ("gate".equals(type)) {
            PestArenaDefinition.GateDefinition node = new PestArenaDefinition.GateDefinition();
            node.id = id;
            setSpawnFromPlayer(node.spawn, sender);
            node.normalize();
            arena.gates.add(node);
        } else if ("barricade".equals(type)) {
            PestArenaDefinition.BarricadeDefinition node = new PestArenaDefinition.BarricadeDefinition();
            node.id = id;
            setSpawnFromPlayer(node.spawn, sender);
            node.normalize();
            arena.barricades.add(node);
        } else if ("turret".equals(type)) {
            PestArenaDefinition.TurretDefinition node = new PestArenaDefinition.TurretDefinition();
            node.id = id;
            setSpawnFromPlayer(node.spawn, sender);
            node.normalize();
            arena.turrets.add(node);
        } else if ("repair".equals(type)) {
            PestArenaDefinition.RepairStationDefinition node = new PestArenaDefinition.RepairStationDefinition();
            node.id = id;
            setSpawnFromPlayer(node.spawn, sender);
            node.normalize();
            arena.repairStations.add(node);
        }

        arena.normalize();
        arenaService.upsertArena(arena);
        ctx.sendMessage(Message.raw("[PestControl] Added " + type + " node: " + id));
    }

    private void arenaObjectiveAdd(CommandContext ctx, String[] args) {
        if (args.length < 6) {
            ctx.sendMessage(Message.raw("Usage: /pc admin arena objective add <capture|escort|defend>"));
            return;
        }

        Player sender = ctx.senderAs(Player.class);
        PestArenaDefinition arena = selectedArenaForAdmin(ctx);
        if (sender == null || arena == null) return;

        String action = args[4].toLowerCase(Locale.ROOT);
        if (!"add".equals(action)) {
            ctx.sendMessage(Message.raw("Usage: /pc admin arena objective add <capture|escort|defend>"));
            return;
        }

        SideObjectiveType type = switch (args[5].toLowerCase(Locale.ROOT)) {
            case "capture" -> SideObjectiveType.CAPTURE_POINT;
            case "escort" -> SideObjectiveType.ESCORT_PAYLOAD;
            case "defend" -> SideObjectiveType.DEFEND_NODE;
            default -> null;
        };

        if (type == null) {
            ctx.sendMessage(Message.raw("[PestControl] Objective type must be capture|escort|defend."));
            return;
        }

        PestArenaDefinition.ObjectiveAnchorDefinition node = new PestArenaDefinition.ObjectiveAnchorDefinition();
        node.id = "obj_" + (arena.objectiveAnchors.size() + 1);
        node.objectiveType = type;
        setSpawnFromPlayer(node.spawn, sender);
        node.normalize();

        arena.objectiveAnchors.add(node);
        arena.normalize();
        arenaService.upsertArena(arena);

        ctx.sendMessage(Message.raw("[PestControl] Added objective anchor: " + node.id + " type=" + type));
    }

    private void arenaSave(CommandContext ctx) {
        PestArenaDefinition arena = selectedArenaForAdmin(ctx);
        if (arena == null) return;

        arena.normalize();
        arenaService.upsertArena(arena);

        ArenaService.ValidationResult arenaValidation = arenaService.validateArena(arena);
        if (!arenaValidation.valid()) {
            ctx.sendMessage(Message.raw("[PestControl] Saved, but validation failed: " + arenaValidation.reason()));
            return;
        }

        ArenaService.ValidationResult claimsValidation = claimsGuard.validateArenaStrict(arena);
        if (!claimsValidation.valid()) {
            ctx.sendMessage(Message.raw("[PestControl] Saved, but strict claims check failed: " + claimsValidation.reason()));
            return;
        }

        ctx.sendMessage(Message.raw("[PestControl] Arena saved and validated."));
    }

    private void arenaValidate(CommandContext ctx) {
        PestArenaDefinition arena = selectedArenaForAdmin(ctx);
        if (arena == null) return;

        ArenaService.ValidationResult arenaValidation = arenaService.validateArena(arena);
        if (!arenaValidation.valid()) {
            ctx.sendMessage(Message.raw("[PestControl] Arena validation failed: " + arenaValidation.reason()));
            return;
        }

        ArenaService.ValidationResult claimsValidation = claimsGuard.validateArenaStrict(arena);
        if (!claimsValidation.valid()) {
            ctx.sendMessage(Message.raw("[PestControl] Strict claims check failed: " + claimsValidation.reason()));
            return;
        }

        ctx.sendMessage(Message.raw("[PestControl] Arena validation passed."));
    }

    private PestArenaDefinition selectedArenaForAdmin(CommandContext ctx) {
        String uuid = senderUuid(ctx);
        if (uuid == null) {
            ctx.sendMessage(Message.raw("[PestControl] Players only."));
            return null;
        }
        PestArenaDefinition arena = arenaService.selectedArenaForAdmin(uuid);
        if (arena == null) {
            ctx.sendMessage(Message.raw("[PestControl] No selected arena. Use /pc admin arena select <id>."));
            return null;
        }
        return arena;
    }

    private static void setSpawnFromPlayer(PestArenaDefinition.Spawn spawn, Player player) {
        if (spawn == null || player == null || player.getPlayerRef() == null) return;
        var tr = player.getPlayerRef().getTransform();
        var pos = tr.getPosition();
        var rot = tr.getRotation();

        spawn.pos = new double[]{pos.getX(), pos.getY(), pos.getZ()};
        spawn.rot = new float[]{rot.getX(), rot.getY(), rot.getZ()};
        spawn.normalize();
    }

    private static boolean isAdmin(CommandContext ctx) {
        if (ctx == null || ctx.sender() == null) return false;
        if (!ctx.isPlayer()) return true;
        return hasPermissionOrOperator(ctx, "pestcontrol.admin");
    }

    private static boolean hasPermissionOrOperator(CommandContext ctx, String permission) {
        if (ctx == null || ctx.sender() == null) return false;
        if (!ctx.isPlayer()) return true;
        try {
            if (ctx.sender().hasPermission(permission)) return true;
        } catch (Throwable ignored) {
        }

        try {
            Method method = ctx.sender().getClass().getMethod("isOperator");
            Object value = method.invoke(ctx.sender());
            if (value instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static String senderUuid(CommandContext ctx) {
        if (ctx == null || ctx.sender() == null || ctx.sender().getUuid() == null) return null;
        return ctx.sender().getUuid().toString();
    }

    private static String[] splitArgs(String input) {
        if (input == null) return new String[0];
        String s = input.trim();
        if (s.startsWith("/")) s = s.substring(1).trim();
        return s.isEmpty() ? new String[0] : s.split("\\s+");
    }

    private static void printHelp(CommandContext ctx) {
        ctx.sendMessage(MSG_USAGE);
        ctx.sendMessage(Message.raw("/pc join <novice|intermediate|veteran>, /pc leave, /pc queue, /pc status"));
        ctx.sendMessage(Message.raw("/pc points, /pc stats, /pc shop list, /pc shop buy <id> [qty]"));
        ctx.sendMessage(Message.raw("Admin: /pc admin <reload|start|stop|queue|arena|mark1|mark2|seed>"));
    }
}
