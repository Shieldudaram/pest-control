package com.Chris__.pest_control;

import com.Chris__.pest_control.arena.ArenaRepository;
import com.Chris__.pest_control.arena.ArenaService;
import com.Chris__.pest_control.commands.PestControlCommand;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.data.AnalyticsLogger;
import com.Chris__.pest_control.data.MatchHistoryLogger;
import com.Chris__.pest_control.data.PestPointsRepository;
import com.Chris__.pest_control.data.PestShopRepository;
import com.Chris__.pest_control.data.PestStatsRepository;
import com.Chris__.pest_control.enemy.EnemySpawnDirector;
import com.Chris__.pest_control.integration.MultipleHudBridge;
import com.Chris__.pest_control.integration.SimpleClaimsGuard;
import com.Chris__.pest_control.interaction.InteractionService;
import com.Chris__.pest_control.match.MatchService;
import com.Chris__.pest_control.objective.SideObjectiveService;
import com.Chris__.pest_control.queue.QueueService;
import com.Chris__.pest_control.reward.PestRewardService;
import com.Chris__.pest_control.systems.PestControlTickSystem;
import com.Chris__.pest_control.ui.PestUiAssetContract;
import com.Chris__.pest_control.ui.PestUiService;
import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class PestControlPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ConfigRepository configRepository;
    private ArenaRepository arenaRepository;
    private ArenaService arenaService;

    private QueueService queueService;
    private PestPointsRepository pointsRepository;
    private PestStatsRepository statsRepository;
    private PestShopRepository shopRepository;
    private MatchHistoryLogger historyLogger;
    private AnalyticsLogger analyticsLogger;

    private SideObjectiveService sideObjectiveService;
    private EnemySpawnDirector enemySpawnDirector;
    private InteractionService interactionService;
    private PestRewardService rewardService;
    private SimpleClaimsGuard claimsGuard;

    private MatchService matchService;
    private PestUiService uiService;

    public PestControlPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Loaded %s v%s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up PestControlPlugin...");

        this.configRepository = new ConfigRepository(this.getDataDirectory(), LOGGER);
        this.arenaRepository = new ArenaRepository(this.getDataDirectory(), LOGGER);
        this.arenaService = new ArenaService(this.arenaRepository, this.configRepository, LOGGER);

        this.queueService = new QueueService();
        this.pointsRepository = new PestPointsRepository(this.getDataDirectory(), LOGGER);
        this.statsRepository = new PestStatsRepository(this.getDataDirectory(), LOGGER);
        this.shopRepository = new PestShopRepository(this.getDataDirectory(), LOGGER);
        this.historyLogger = new MatchHistoryLogger(this.getDataDirectory(), LOGGER);
        this.analyticsLogger = new AnalyticsLogger(this.getDataDirectory(), LOGGER);

        this.sideObjectiveService = new SideObjectiveService(this.configRepository);
        this.enemySpawnDirector = new EnemySpawnDirector(this.configRepository);
        this.interactionService = new InteractionService();
        this.rewardService = new PestRewardService(this.configRepository, this.pointsRepository, this.statsRepository);
        this.claimsGuard = new SimpleClaimsGuard(this.configRepository, LOGGER);

        this.matchService = new MatchService(
                this.configRepository,
                this.arenaService,
                this.queueService,
                this.sideObjectiveService,
                this.enemySpawnDirector,
                this.interactionService,
                this.rewardService,
                this.historyLogger,
                this.analyticsLogger,
                LOGGER
        );
        this.matchService.setStartGate(this.claimsGuard::validateArenaStrict);

        PestUiAssetContract.ValidationResult uiValidation =
                PestUiAssetContract.validate(this.getClass().getClassLoader());
        if (!uiValidation.manifestIncludesAssetPack()) {
            LOGGER.atWarning().log("[PestControl-UI] manifest IncludesAssetPack=false. Custom HUD cannot be loaded.");
        }
        if (!uiValidation.missingUiDocuments().isEmpty()) {
            LOGGER.atWarning().log("[PestControl-UI] Missing required UI docs in plugin assets: %s",
                    String.join(", ", uiValidation.missingUiDocuments()));
        }
        boolean customHudAllowed = uiValidation.ready();
        String customHudBlockReason = customHudAllowed
                ? ""
                : "Custom HUD disabled until IncludesAssetPack=true and required UI docs are present.";

        PestUiService.HudMode requestedHudMode = PestUiService.HudMode.parse(configRepository.get().ui.hudMode);
        PestUiService.HudMode effectiveStartupMode =
                (requestedHudMode == PestUiService.HudMode.CUSTOM && !customHudAllowed)
                        ? PestUiService.HudMode.CHAT_ONLY
                        : requestedHudMode;
        LOGGER.atInfo().log("[PestControl-UI] HUD mode requested=%s effective=%s",
                requestedHudMode.name(),
                effectiveStartupMode.name());
        if (requestedHudMode == PestUiService.HudMode.CUSTOM && effectiveStartupMode != requestedHudMode) {
            LOGGER.atWarning().log("[PestControl-UI] %s", customHudBlockReason);
        }

        MultipleHUD multipleHUD = requireMultipleHudOrThrow();
        MultipleHudBridge multipleHudBridge = new MultipleHudBridge(multipleHUD, LOGGER);
        this.uiService = new PestUiService(
                this.matchService,
                this.queueService,
                this.configRepository,
                multipleHudBridge,
                LOGGER,
                customHudAllowed,
                customHudBlockReason
        );

        this.getEntityStoreRegistry().registerSystem(new PestControlTickSystem(this.matchService));
        this.getEntityStoreRegistry().registerSystem(this.uiService.createSystem());

        this.getCommandRegistry().registerCommand(new PestControlCommand(
                this.queueService,
                this.matchService,
                this.arenaService,
                this.configRepository,
                this.pointsRepository,
                this.statsRepository,
                this.shopRepository,
                this.claimsGuard
        ));

        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onAddPlayerToWorld);
        this.getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        this.getEventRegistry().registerGlobal(PlayerInteractEvent.class, this::onPlayerInteract);

        ArenaService.ValidationResult claims = this.claimsGuard.validateAllArenas(this.arenaService.listArenas());
        if (!claims.valid()) {
            LOGGER.atWarning().log("[PestControl] Initial strict claims validation: %s", claims.reason());
        }

        LOGGER.atInfo().log("[PestControl] Plugin setup complete.");
    }

    private static MultipleHUD requireMultipleHudOrThrow() {
        try {
            PluginManager pm = PluginManager.get();
            if (pm == null) {
                throw new IllegalStateException("[PestControl] Missing required dependency Buuz135:MultipleHUD (PluginManager unavailable).");
            }

            if (pm.getPlugin(new PluginIdentifier("Buuz135", "MultipleHUD")) == null) {
                throw new IllegalStateException("[PestControl] Missing required dependency Buuz135:MultipleHUD.");
            }

            MultipleHUD instance = MultipleHUD.getInstance();
            if (instance == null) {
                throw new IllegalStateException("[PestControl] Missing required dependency Buuz135:MultipleHUD (instance unavailable).");
            }

            return instance;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("[PestControl] Missing required dependency Buuz135:MultipleHUD.", t);
        }
    }

    private void onAddPlayerToWorld(AddPlayerToWorldEvent event) {
        if (event == null || event.getHolder() == null) return;

        try {
            var holder = event.getHolder();
            Player player = holder.getComponent(Player.getComponentType());
            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
            if (player == null || playerRef == null || playerRef.getUuid() == null) return;

            String uuid = playerRef.getUuid().toString();
            String name = player.getDisplayName();
            pointsRepository.setLastKnownName(uuid, name);
            statsRepository.setLastKnownName(uuid, name);
            pointsRepository.saveCurrent();
            statsRepository.saveCurrent();
        } catch (Throwable t) {
            LOGGER.atWarning().withCause(t).log("[PestControl] onAddPlayerToWorld failed.");
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        if (event == null) return;
        try {
            PlayerRef playerRef = event.getPlayerRef();
            if (playerRef == null || playerRef.getUuid() == null) return;
            queueService.remove(playerRef.getUuid().toString());
        } catch (Throwable ignored) {
        }
    }

    private void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null) return;
        if (event.isCancelled()) return;
        if (event.getActionType() != InteractionType.Use) return;

        Player player = event.getPlayer();
        if (player == null || player.getPlayerRef() == null || player.getPlayerRef().getUuid() == null) return;

        String uuid = player.getPlayerRef().getUuid().toString();
        if (queueService.getTierForPlayer(uuid) != null) return;

        var tr = player.getPlayerRef().getTransform();
        var pos = tr.getPosition();
        int bx = (int) Math.floor(pos.getX());
        int by = (int) Math.floor(pos.getY());
        int bz = (int) Math.floor(pos.getZ());
        String world = player.getWorld() == null ? "" : player.getWorld().getName();

        Tier tier = resolveBoatTier(world, bx, by, bz);
        if (tier == null) return;

        boolean enqueued = queueService.enqueue(uuid, tier, System.currentTimeMillis());
        if (!enqueued) return;

        try {
            player.sendMessage(Message.raw("[PestControl] Joined " + tier + " queue via boat."));
        } catch (Throwable ignored) {
        }
    }

    private Tier resolveBoatTier(String world, int x, int y, int z) {
        if (world == null || world.isBlank()) return null;

        for (PestArenaDefinition arena : arenaService.listArenas()) {
            if (arena == null) continue;
            if (!world.equals(arena.world)) continue;
            if (arena.joinBoatBounds != null && arena.joinBoatBounds.containsBlock(x, y, z)) {
                return arena.tier;
            }
        }

        return null;
    }
}
