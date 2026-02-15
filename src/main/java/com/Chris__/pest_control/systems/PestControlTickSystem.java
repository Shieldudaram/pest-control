package com.Chris__.pest_control.systems;

import com.Chris__.pest_control.match.MatchService;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.RunWhenPausedSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.concurrent.atomic.AtomicLong;

public final class PestControlTickSystem extends TickingSystem<EntityStore> implements RunWhenPausedSystem<EntityStore> {

    private static final long STEP_NANOS = 100_000_000L; // 10hz

    private final MatchService matchService;
    private final AtomicLong nextTickAtNanos = new AtomicLong(0L);

    public PestControlTickSystem(MatchService matchService) {
        this.matchService = matchService;
    }

    @Override
    public void tick(float deltaTimeSeconds, int index, Store<EntityStore> store) {
        if (matchService == null) return;

        long now = System.nanoTime();
        long nextTick = nextTickAtNanos.get();
        if (nextTick == 0L) {
            if (nextTickAtNanos.compareAndSet(0L, now + STEP_NANOS)) {
                safeTick();
            }
            return;
        }

        if (now < nextTick) {
            return;
        }

        if (nextTickAtNanos.compareAndSet(nextTick, now + STEP_NANOS)) {
            safeTick();
        }
    }

    private void safeTick() {
        try {
            matchService.tick(System.currentTimeMillis());
        } catch (Throwable ignored) {
        }
    }
}
