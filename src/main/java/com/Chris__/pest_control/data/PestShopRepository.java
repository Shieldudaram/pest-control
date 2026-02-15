package com.Chris__.pest_control.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PestShopRepository {

    private static final String FILE_NAME = "pest_shop.json";

    private final Object lock = new Object();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HytaleLogger logger;
    private final Path filePath;

    private volatile PestShopConfig cached;

    public PestShopRepository(Path dataDirectory, HytaleLogger logger) {
        this.logger = logger;
        this.filePath = (dataDirectory == null) ? null : dataDirectory.resolve(FILE_NAME);
        reload();
    }

    public PestShopConfig get() {
        PestShopConfig c = cached;
        if (c != null) return c;
        c = normalize(new PestShopConfig());
        cached = c;
        return c;
    }

    public void reload() {
        synchronized (lock) {
            if (filePath == null) {
                cached = normalize(new PestShopConfig());
                return;
            }
            try {
                Files.createDirectories(filePath.getParent());
                if (!Files.exists(filePath)) {
                    PestShopConfig cfg = normalize(new PestShopConfig());
                    cached = cfg;
                    save(cfg);
                    return;
                }
                try (Reader reader = Files.newBufferedReader(filePath)) {
                    PestShopConfig loaded = gson.fromJson(reader, PestShopConfig.class);
                    cached = normalize(loaded == null ? new PestShopConfig() : loaded);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.atWarning().withCause(t).log("[PestControl] Failed to load pest_shop.json; using defaults.");
                }
                cached = normalize(new PestShopConfig());
            }
        }
    }

    public PestShopConfig.ShopItem find(String id) {
        if (id == null || id.isBlank()) return null;
        PestShopConfig cfg = get();
        if (cfg.items == null) return null;

        for (PestShopConfig.ShopItem item : cfg.items) {
            if (item == null || item.id == null) continue;
            if (item.id.equalsIgnoreCase(id)) return item;
        }
        return null;
    }

    public void saveCurrent() {
        save(get());
    }

    private void save(PestShopConfig cfg) {
        if (filePath == null || cfg == null) return;
        synchronized (lock) {
            try {
                Files.createDirectories(filePath.getParent());
                try (Writer writer = Files.newBufferedWriter(filePath)) {
                    gson.toJson(cfg, writer);
                }
            } catch (Throwable t) {
                if (logger != null) {
                    logger.atWarning().withCause(t).log("[PestControl] Failed to save pest_shop.json.");
                }
            }
        }
    }

    private static PestShopConfig normalize(PestShopConfig cfg) {
        if (cfg == null) cfg = new PestShopConfig();
        if (cfg.items == null) cfg.items = new ArrayList<>();

        List<PestShopConfig.ShopItem> normalized = new ArrayList<>();
        for (PestShopConfig.ShopItem item : cfg.items) {
            if (item == null) continue;
            if (item.id == null || item.id.isBlank()) continue;
            if (item.name == null || item.name.isBlank()) item.name = item.id;
            if (item.itemId == null || item.itemId.isBlank()) item.itemId = "REPLACE_ME";
            if (item.cost < 0) item.cost = 0;
            if (item.amount <= 0) item.amount = 1;
            normalized.add(item);
        }

        if (normalized.isEmpty()) {
            normalized.addAll(new PestShopConfig().items);
        }

        cfg.items = normalized;
        return cfg;
    }
}
