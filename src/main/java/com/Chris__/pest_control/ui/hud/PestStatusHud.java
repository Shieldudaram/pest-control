package com.Chris__.pest_control.ui.hud;

import com.Chris__.pest_control.ui.PestUiAssetContract;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class PestStatusHud extends CustomUIHud {

    private boolean visible;
    private final String hudPath;
    private String title = "";
    private String line1 = "";
    private String line2 = "";
    private String line3 = "";

    public PestStatusHud(@Nonnull PlayerRef playerRef, String hudPath) {
        super(playerRef);
        this.hudPath = sanitizePath(hudPath);
    }

    public void show(String title, String line1, String line2, String line3) {
        this.visible = true;
        this.title = safe(title);
        this.line1 = safe(line1);
        this.line2 = safe(line2);
        this.line3 = safe(line3);
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean usesHudPath(String path) {
        return hudPath.equals(sanitizePath(path));
    }

    @Override
    protected void build(@Nonnull UICommandBuilder ui) {
        if (!visible) return;
        ui.append(hudPath);
        ui.set("#PcTitleLabel.TextSpans", Message.raw(title));
        ui.set("#PcLine1Label.TextSpans", Message.raw(line1));
        ui.set("#PcLine2Label.TextSpans", Message.raw(line2));
        ui.set("#PcLine3Label.TextSpans", Message.raw(line3));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String sanitizePath(String path) {
        if (path == null || path.isBlank()) return PestUiAssetContract.HUD_STATUS;
        String trimmed = path.trim();
        if ("HUD/PestControl/Status.ui".equalsIgnoreCase(trimmed)) return PestUiAssetContract.HUD_STATUS;
        return trimmed;
    }
}
