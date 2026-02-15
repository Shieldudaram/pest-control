package com.Chris__.pest_control.interaction;

import com.Chris__.pest_control.PestArenaDefinition;
import com.Chris__.pest_control.config.PestConfig;

import java.util.HashMap;
import java.util.Map;

public final class InteractionService {

    public enum NodeType {
        GATE,
        BARRICADE,
        TURRET,
        REPAIR_STATION,
        OBJECTIVE_ANCHOR
    }

    public record InteractionNodeState(NodeType type, String id, int hp, int maxHp, boolean destroyed) {
    }

    public static final class ArenaInteractionState {
        public final Map<String, MutableNode> nodesByKey = new HashMap<>();

        public int countByType(NodeType type) {
            int n = 0;
            for (MutableNode node : nodesByKey.values()) {
                if (node.type == type) n++;
            }
            return n;
        }

        public int destroyedByType(NodeType type) {
            int n = 0;
            for (MutableNode node : nodesByKey.values()) {
                if (node.type == type && node.hp <= 0) n++;
            }
            return n;
        }
    }

    private static final class MutableNode {
        private final NodeType type;
        private final String id;
        private final int maxHp;
        private int hp;

        private MutableNode(NodeType type, String id, int maxHp) {
            this.type = type;
            this.id = id;
            this.maxHp = Math.max(1, maxHp);
            this.hp = this.maxHp;
        }

        private InteractionNodeState snapshot() {
            return new InteractionNodeState(type, id, hp, maxHp, hp <= 0);
        }
    }

    public ArenaInteractionState initializeForArena(PestArenaDefinition arena, PestConfig cfg) {
        ArenaInteractionState state = new ArenaInteractionState();
        if (arena == null) return state;

        int gateHp = cfg == null || cfg.interactions == null ? 900 : cfg.interactions.defaultGateHp;
        int barricadeHp = cfg == null || cfg.interactions == null ? 500 : cfg.interactions.defaultBarricadeHp;
        int turretHp = cfg == null || cfg.interactions == null ? 1300 : cfg.interactions.defaultTurretHp;

        for (PestArenaDefinition.GateDefinition gate : arena.gates) {
            if (gate == null || gate.id == null || gate.id.isBlank()) continue;
            int hp = gate.maxHp > 0 ? gate.maxHp : gateHp;
            state.nodesByKey.put(key(NodeType.GATE, gate.id), new MutableNode(NodeType.GATE, gate.id, hp));
        }
        for (PestArenaDefinition.BarricadeDefinition barricade : arena.barricades) {
            if (barricade == null || barricade.id == null || barricade.id.isBlank()) continue;
            int hp = barricade.maxHp > 0 ? barricade.maxHp : barricadeHp;
            state.nodesByKey.put(key(NodeType.BARRICADE, barricade.id), new MutableNode(NodeType.BARRICADE, barricade.id, hp));
        }
        for (PestArenaDefinition.TurretDefinition turret : arena.turrets) {
            if (turret == null || turret.id == null || turret.id.isBlank()) continue;
            int hp = turret.maxHp > 0 ? turret.maxHp : turretHp;
            state.nodesByKey.put(key(NodeType.TURRET, turret.id), new MutableNode(NodeType.TURRET, turret.id, hp));
        }
        for (PestArenaDefinition.RepairStationDefinition station : arena.repairStations) {
            if (station == null || station.id == null || station.id.isBlank()) continue;
            state.nodesByKey.put(key(NodeType.REPAIR_STATION, station.id), new MutableNode(NodeType.REPAIR_STATION, station.id, 1));
        }
        for (PestArenaDefinition.ObjectiveAnchorDefinition anchor : arena.objectiveAnchors) {
            if (anchor == null || anchor.id == null || anchor.id.isBlank()) continue;
            state.nodesByKey.put(key(NodeType.OBJECTIVE_ANCHOR, anchor.id), new MutableNode(NodeType.OBJECTIVE_ANCHOR, anchor.id, 1));
        }

        return state;
    }

    public InteractionNodeState damage(ArenaInteractionState state, NodeType type, String id, int amount) {
        if (state == null || id == null || id.isBlank() || amount <= 0) return null;
        MutableNode node = state.nodesByKey.get(key(type, id));
        if (node == null) return null;
        if (node.hp <= 0) return node.snapshot();
        node.hp = Math.max(0, node.hp - amount);
        return node.snapshot();
    }

    public InteractionNodeState repair(ArenaInteractionState state, NodeType type, String id, int amount) {
        if (state == null || id == null || id.isBlank() || amount <= 0) return null;
        MutableNode node = state.nodesByKey.get(key(type, id));
        if (node == null) return null;
        if (node.maxHp <= 1) return node.snapshot();
        node.hp = Math.min(node.maxHp, node.hp + amount);
        return node.snapshot();
    }

    public InteractionNodeState get(ArenaInteractionState state, NodeType type, String id) {
        if (state == null || id == null || id.isBlank()) return null;
        MutableNode node = state.nodesByKey.get(key(type, id));
        if (node == null) return null;
        return node.snapshot();
    }

    private static String key(NodeType type, String id) {
        return type.name() + ':' + id.toLowerCase();
    }
}
