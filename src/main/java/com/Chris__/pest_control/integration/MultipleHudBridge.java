package com.Chris__.pest_control.integration;

import com.Chris__.pest_control.ui.hud.PestStatusHud;
import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MultipleHudBridge {

    public static final String HUD_KEY = "PestControl_Status";

    private final MultipleHUD multipleHUD;
    private final HytaleLogger logger;
    private final AtomicBoolean runtimeFailed = new AtomicBoolean(false);

    public MultipleHudBridge(@Nonnull MultipleHUD multipleHUD, HytaleLogger logger) {
        this.multipleHUD = multipleHUD;
        this.logger = logger;
    }

    public boolean tryShowStatusHud(Player player, PlayerRef playerRef, PestStatusHud hud) {
        if (player == null || playerRef == null || hud == null) return false;
        if (runtimeFailed.get()) return false;
        try {
            multipleHUD.setCustomHud(player, playerRef, HUD_KEY, hud);
            return true;
        } catch (Throwable t) {
            markRuntimeFailure("Failed invoking MultipleHUD#setCustomHud for slot '" + HUD_KEY + "'.", t);
            return false;
        }
    }

    public boolean tryHideStatusHud(Player player) {
        if (player == null) return false;
        if (runtimeFailed.get()) return false;
        try {
            multipleHUD.hideCustomHud(player, HUD_KEY);
            return true;
        } catch (Throwable t) {
            markRuntimeFailure("Failed invoking MultipleHUD#hideCustomHud for slot '" + HUD_KEY + "'.", t);
            return false;
        }
    }

    public boolean isRuntimeFailed() {
        return runtimeFailed.get();
    }

    private void markRuntimeFailure(String message, Throwable cause) {
        if (!runtimeFailed.compareAndSet(false, true)) return;
        if (logger == null) return;
        if (cause != null) {
            logger.atSevere().withCause(cause).log("[PestControl-HUD] %s", message);
        } else {
            logger.atSevere().log("[PestControl-HUD] %s", message);
        }
    }
}
