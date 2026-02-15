package com.Chris__.pest_control.queue;

import com.Chris__.pest_control.Tier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QueueService {

    public record QueueSnapshot(
            int novice,
            int intermediate,
            int veteran,
            int total,
            Map<Tier, List<QueueTicket>> tickets
    ) {
    }

    private final Object lock = new Object();

    private final EnumMap<Tier, List<QueueTicket>> byTier = new EnumMap<>(Tier.class);
    private final Map<String, QueueTicket> byPlayer = new HashMap<>();

    public QueueService() {
        byTier.put(Tier.NOVICE, new ArrayList<>());
        byTier.put(Tier.INTERMEDIATE, new ArrayList<>());
        byTier.put(Tier.VETERAN, new ArrayList<>());
    }

    public boolean enqueue(String playerUuid, Tier tier, long nowMillis) {
        if (playerUuid == null || playerUuid.isBlank()) return false;
        if (tier == null) return false;

        synchronized (lock) {
            if (byPlayer.containsKey(playerUuid)) return false;
            QueueTicket ticket = new QueueTicket(playerUuid, tier, nowMillis);
            byTier.get(tier).add(ticket);
            byPlayer.put(playerUuid, ticket);
            return true;
        }
    }

    public boolean remove(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) return false;
        synchronized (lock) {
            QueueTicket ticket = byPlayer.remove(playerUuid);
            if (ticket == null) return false;
            return byTier.get(ticket.tier()).removeIf(t -> t.playerUuid().equals(playerUuid));
        }
    }

    public Tier getTierForPlayer(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) return null;
        synchronized (lock) {
            QueueTicket ticket = byPlayer.get(playerUuid);
            return ticket == null ? null : ticket.tier();
        }
    }

    public List<String> drainPlayersForTier(Tier tier, int maxPlayers) {
        if (tier == null || maxPlayers <= 0) return List.of();

        List<String> out = new ArrayList<>();
        synchronized (lock) {
            List<QueueTicket> queue = byTier.get(tier);
            int take = Math.min(maxPlayers, queue.size());
            for (int i = 0; i < take; i++) {
                QueueTicket ticket = queue.remove(0);
                if (ticket == null) continue;
                byPlayer.remove(ticket.playerUuid());
                out.add(ticket.playerUuid());
            }
        }
        return out;
    }

    public Tier pickTierByOldestTicket() {
        synchronized (lock) {
            QueueTicket oldest = null;
            for (Tier tier : Tier.values()) {
                List<QueueTicket> queue = byTier.get(tier);
                if (queue == null || queue.isEmpty()) continue;
                QueueTicket head = queue.get(0);
                if (oldest == null || head.queuedAtMillis() < oldest.queuedAtMillis()) {
                    oldest = head;
                }
            }
            return oldest == null ? null : oldest.tier();
        }
    }

    public Set<String> allQueuedPlayers() {
        synchronized (lock) {
            return new HashSet<>(byPlayer.keySet());
        }
    }

    public QueueSnapshot snapshot() {
        synchronized (lock) {
            Map<Tier, List<QueueTicket>> copy = new EnumMap<>(Tier.class);
            int total = 0;
            int novice = 0;
            int intermediate = 0;
            int veteran = 0;

            for (Tier tier : Tier.values()) {
                List<QueueTicket> src = byTier.get(tier);
                List<QueueTicket> dst = new ArrayList<>(src == null ? List.of() : src);
                copy.put(tier, Collections.unmodifiableList(dst));

                int count = dst.size();
                total += count;
                if (tier == Tier.NOVICE) novice = count;
                if (tier == Tier.INTERMEDIATE) intermediate = count;
                if (tier == Tier.VETERAN) veteran = count;
            }

            return new QueueSnapshot(novice, intermediate, veteran, total, Collections.unmodifiableMap(copy));
        }
    }

    public int size(Tier tier) {
        if (tier == null) return 0;
        synchronized (lock) {
            List<QueueTicket> queue = byTier.get(tier);
            return queue == null ? 0 : queue.size();
        }
    }

    public void clearAll() {
        synchronized (lock) {
            byPlayer.clear();
            for (Tier tier : Tier.values()) {
                byTier.get(tier).clear();
            }
        }
    }

    public void clearTier(Tier tier) {
        if (tier == null) return;
        synchronized (lock) {
            List<QueueTicket> queue = byTier.get(tier);
            for (QueueTicket ticket : queue) {
                if (ticket != null) byPlayer.remove(ticket.playerUuid());
            }
            queue.clear();
        }
    }
}
