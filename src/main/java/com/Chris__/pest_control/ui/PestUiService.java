package com.Chris__.pest_control.ui;

import com.Chris__.pest_control.MatchPhase;
import com.Chris__.pest_control.PestMatchState;
import com.Chris__.pest_control.PortalState;
import com.Chris__.pest_control.Tier;
import com.Chris__.pest_control.config.ConfigRepository;
import com.Chris__.pest_control.config.PestConfig;
import com.Chris__.pest_control.integration.MultipleHudBridge;
import com.Chris__.pest_control.match.MatchService;
import com.Chris__.pest_control.queue.QueueService;
import com.Chris__.pest_control.ui.hud.PestStatusHud;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PestUiService {

    public enum HudMode {
        CUSTOM,
        CHAT_ONLY,
        OFF;

        public static HudMode parse(String raw) {
            if (raw == null || raw.isBlank()) return CHAT_ONLY;
            String mode = raw.trim().toUpperCase();
            return switch (mode) {
                case "CUSTOM" -> CUSTOM;
                case "OFF" -> OFF;
                default -> CHAT_ONLY;
            };
        }
    }

    private static final long MIN_CUSTOM_REFRESH_MILLIS = 1_000L;

    private final MatchService matchService;
    private final QueueService queueService;
    private final ConfigRepository configRepository;
    private final MultipleHudBridge bridge;
    private final HytaleLogger logger;
    private final boolean customHudAllowed;
    private final String customHudBlockReason;

    private final Map<String, PestStatusHud> hudByUuid = new ConcurrentHashMap<>();
    private final Map<String, HudMode> modeByUuid = new ConcurrentHashMap<>();
    private final Map<String, String> customSignatureByUuid = new ConcurrentHashMap<>();
    private final Map<String, Long> nextCustomRefreshAtByUuid = new ConcurrentHashMap<>();
    private final Map<String, String> chatSignatureByUuid = new ConcurrentHashMap<>();
    private final Map<String, Long> nextChatAtByUuid = new ConcurrentHashMap<>();

    private final AtomicBoolean customHudBlockedLogSent = new AtomicBoolean(false);
    private final AtomicBoolean customHudRuntimeFallbackLogSent = new AtomicBoolean(false);

    public PestUiService(MatchService matchService,
                         QueueService queueService,
                         ConfigRepository configRepository,
                         MultipleHudBridge bridge,
                         HytaleLogger logger,
                         boolean customHudAllowed,
                         String customHudBlockReason) {
        this.matchService = matchService;
        this.queueService = queueService;
        this.configRepository = configRepository;
        this.bridge = bridge;
        this.logger = logger;
        this.customHudAllowed = customHudAllowed;
        this.customHudBlockReason = customHudBlockReason;
    }

    public EntityTickingSystem<EntityStore> createSystem() {
        return new PestUiTickSystem();
    }

    private final class PestUiTickSystem extends EntityTickingSystem<EntityStore> {
        private final Query<EntityStore> query = Query.and(Player.getComponentType(), PlayerRef.getComponentType());

        @Override
        public Query<EntityStore> getQuery() {
            return query;
        }

        @Override
        public void tick(float dt,
                         int entityId,
                         ArchetypeChunk<EntityStore> chunk,
                         Store<EntityStore> store,
                         CommandBuffer<EntityStore> commandBuffer) {
            Holder<EntityStore> holder;
            try {
                holder = EntityUtils.toHolder(entityId, chunk);
            } catch (Throwable ignored) {
                return;
            }

            Player player = holder.getComponent(Player.getComponentType());
            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
            if (player == null || playerRef == null || playerRef.getUuid() == null) return;

            String uuid = playerRef.getUuid().toString();

            MatchService.MatchSnapshot snapshot = matchService.snapshot();
            PestMatchState state = snapshot.state();
            boolean inMatch = state != null && state.playerUuids.contains(uuid);
            boolean queued = queueService.getTierForPlayer(uuid) != null;

            if (!inMatch && !queued) {
                clearForPlayer(player, uuid);
                return;
            }

            String title = "Pest Control";
            String line1;
            String line2;
            String line3;

            if (state == null) {
                Tier queuedTier = queueService.getTierForPlayer(uuid);
                line1 = "In queue";
                line2 = "Tier: " + (queuedTier == null ? "UNKNOWN" : queuedTier);
                line3 = "Waiting for round start";
            } else {
                int activePortal = state.activePortalIndex();
                int portalsRemaining = 0;
                for (int i = 0; i < 4; i++) {
                    PestMatchState.PortalRuntime portal = state.portals.get(i);
                    if (portal != null && portal.state != PortalState.DESTROYED) portalsRemaining++;
                }

                line1 = "Tier " + state.tier + " - Phase " + state.phase;
                line2 = "Knight HP " + state.voidKnightHp + "/" + state.voidKnightMaxHp;
                if (state.phase == MatchPhase.ACTIVE) {
                    line3 = "Portal " + (activePortal + 1) + " active - Remaining " + portalsRemaining;
                } else {
                    line3 = "Portals remaining " + portalsRemaining;
                }
            }

            PestConfig cfg = configRepository == null ? null : configRepository.get();
            PestConfig.Ui uiCfg = (cfg == null || cfg.ui == null) ? new PestConfig.Ui() : cfg.ui;
            HudMode requestedMode = HudMode.parse(uiCfg.hudMode);
            HudMode effectiveMode = resolveEffectiveMode(requestedMode);

            HudMode previousMode = modeByUuid.put(uuid, effectiveMode);
            if (previousMode != effectiveMode) {
                if (previousMode == HudMode.CUSTOM) {
                    hideCustom(player, uuid);
                }
                if (effectiveMode != HudMode.CHAT_ONLY) {
                    chatSignatureByUuid.remove(uuid);
                    nextChatAtByUuid.remove(uuid);
                }
                if (effectiveMode != HudMode.CUSTOM) {
                    customSignatureByUuid.remove(uuid);
                    nextCustomRefreshAtByUuid.remove(uuid);
                }
            }

            String signature = title + '\n' + line1 + '\n' + line2 + '\n' + line3;
            long nowMillis = System.currentTimeMillis();
            long chatIntervalMillis = Math.max(1L, uiCfg.chatUpdateSeconds) * 1_000L;
            String hudPath = sanitizeHudPath(uiCfg.hudPath);

            switch (effectiveMode) {
                case OFF -> {
                    hideCustom(player, uuid);
                    chatSignatureByUuid.remove(uuid);
                    nextChatAtByUuid.remove(uuid);
                }
                case CHAT_ONLY -> {
                    hideCustom(player, uuid);
                    renderChat(player, uuid, line1, line2, line3, signature, nowMillis, chatIntervalMillis);
                }
                case CUSTOM -> renderCustom(player, playerRef, uuid, hudPath, title, line1, line2, line3, signature, nowMillis, chatIntervalMillis);
            }
        }
    }

    private HudMode resolveEffectiveMode(HudMode requestedMode) {
        if (requestedMode != HudMode.CUSTOM) return requestedMode;
        if (!customHudAllowed) {
            if (logger != null && customHudBlockedLogSent.compareAndSet(false, true)) {
                String reason = (customHudBlockReason == null || customHudBlockReason.isBlank())
                        ? "Custom HUD is blocked by UI asset contract checks."
                        : customHudBlockReason;
                logger.atWarning().log("[PestControl-UI] %s Falling back to CHAT_ONLY.", reason);
            }
            return HudMode.CHAT_ONLY;
        }
        if (bridge.isRuntimeFailed()) {
            if (logger != null && customHudRuntimeFallbackLogSent.compareAndSet(false, true)) {
                logger.atWarning().log("[PestControl-UI] MultipleHUD runtime failure detected. Falling back to CHAT_ONLY.");
            }
            return HudMode.CHAT_ONLY;
        }
        return HudMode.CUSTOM;
    }

    private void renderCustom(Player player,
                              PlayerRef playerRef,
                              String uuid,
                              String hudPath,
                              String title,
                              String line1,
                              String line2,
                              String line3,
                              String signature,
                              long nowMillis,
                              long chatIntervalMillis) {
        long nextAt = nextCustomRefreshAtByUuid.getOrDefault(uuid, 0L);
        if (nowMillis < nextAt) return;

        String previous = customSignatureByUuid.get(uuid);
        if (signature.equals(previous)) {
            nextCustomRefreshAtByUuid.put(uuid, nowMillis + MIN_CUSTOM_REFRESH_MILLIS);
            return;
        }

        PestStatusHud hud = getOrCreateHud(uuid, playerRef, hudPath);
        hud.show(title, line1, line2, line3);
        if (bridge.tryShowStatusHud(player, playerRef, hud)) {
            customSignatureByUuid.put(uuid, signature);
            nextCustomRefreshAtByUuid.put(uuid, nowMillis + MIN_CUSTOM_REFRESH_MILLIS);
            return;
        }

        if (!bridge.isRuntimeFailed()) return;

        hideCustom(player, uuid);
        chatSignatureByUuid.remove(uuid);
        nextChatAtByUuid.remove(uuid);
        renderChat(player, uuid, line1, line2, line3, signature, nowMillis, chatIntervalMillis);
    }

    private void renderChat(Player player,
                            String uuid,
                            String line1,
                            String line2,
                            String line3,
                            String signature,
                            long nowMillis,
                            long chatIntervalMillis) {
        String previous = chatSignatureByUuid.get(uuid);
        if (signature.equals(previous)) return;

        long nextAt = nextChatAtByUuid.getOrDefault(uuid, 0L);
        if (nowMillis < nextAt) return;

        try {
            player.sendMessage(Message.raw("[PestControl] " + line1 + " - " + line2 + " - " + line3));
        } catch (Throwable ignored) {
            return;
        }

        chatSignatureByUuid.put(uuid, signature);
        nextChatAtByUuid.put(uuid, nowMillis + chatIntervalMillis);
    }

    private PestStatusHud getOrCreateHud(String uuid, PlayerRef playerRef, String hudPath) {
        PestStatusHud existing = hudByUuid.get(uuid);
        if (existing != null && existing.usesHudPath(hudPath)) {
            return existing;
        }
        PestStatusHud created = new PestStatusHud(playerRef, hudPath);
        hudByUuid.put(uuid, created);
        return created;
    }

    private void clearForPlayer(Player player, String uuid) {
        modeByUuid.remove(uuid);
        chatSignatureByUuid.remove(uuid);
        nextChatAtByUuid.remove(uuid);
        hideCustom(player, uuid);
        hudByUuid.remove(uuid);
    }

    private void hideCustom(Player player, String uuid) {
        boolean shouldHide = false;
        PestStatusHud hud = hudByUuid.get(uuid);
        if (hud != null && hud.isVisible()) {
            hud.hide();
            shouldHide = true;
        }
        if (customSignatureByUuid.remove(uuid) != null) {
            shouldHide = true;
        }
        nextCustomRefreshAtByUuid.remove(uuid);
        if (shouldHide) {
            bridge.tryHideStatusHud(player);
        }
    }

    private static String sanitizeHudPath(String path) {
        if (path == null || path.isBlank()) return PestUiAssetContract.HUD_STATUS;
        String trimmed = path.trim();
        if ("HUD/PestControl/Status.ui".equalsIgnoreCase(trimmed)) return PestUiAssetContract.HUD_STATUS;
        return trimmed;
    }
}
