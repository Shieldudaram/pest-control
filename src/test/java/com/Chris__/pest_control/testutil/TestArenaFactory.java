package com.Chris__.pest_control.testutil;

import com.Chris__.pest_control.PestArenaDefinition;
import com.Chris__.pest_control.SideObjectiveType;
import com.Chris__.pest_control.Tier;

public final class TestArenaFactory {

    private TestArenaFactory() {
    }

    public static PestArenaDefinition validArena(String id, Tier tier, String world) {
        PestArenaDefinition arena = new PestArenaDefinition();
        arena.id = id;
        arena.tier = tier;
        arena.world = world;

        arena.battleBounds.min = new int[]{0, 0, 0};
        arena.battleBounds.max = new int[]{50, 20, 50};
        arena.lobbyBounds.min = new int[]{60, 0, 60};
        arena.lobbyBounds.max = new int[]{80, 20, 80};
        arena.joinBoatBounds.min = new int[]{90, 0, 90};
        arena.joinBoatBounds.max = new int[]{95, 5, 95};

        arena.playerSpawn.pos = new double[]{2, 1, 2};
        arena.respawnSpawn.pos = new double[]{3, 1, 3};
        arena.knightSpawn.pos = new double[]{4, 1, 4};

        for (int i = 0; i < 4; i++) {
            PestArenaDefinition.PortalDefinition portal = arena.portals.get(i);
            portal.coreSpawn.pos = new double[]{10 + i, 1, 10 + i};
            portal.enemySpawn.pos = new double[]{12 + i, 1, 12 + i};
            portal.baseHp = 1000;
        }

        for (int i = 0; i < 8; i++) {
            PestArenaDefinition.GateDefinition gate = new PestArenaDefinition.GateDefinition();
            gate.id = "gate_" + i;
            gate.spawn.pos = new double[]{i, 1, i};
            arena.gates.add(gate);
        }

        for (int i = 0; i < 8; i++) {
            PestArenaDefinition.BarricadeDefinition barricade = new PestArenaDefinition.BarricadeDefinition();
            barricade.id = "barricade_" + i;
            barricade.spawn.pos = new double[]{i + 20, 1, i};
            arena.barricades.add(barricade);
        }

        for (int i = 0; i < 2; i++) {
            PestArenaDefinition.TurretDefinition turret = new PestArenaDefinition.TurretDefinition();
            turret.id = "turret_" + i;
            turret.spawn.pos = new double[]{i + 30, 1, i};
            arena.turrets.add(turret);
        }

        for (int i = 0; i < 4; i++) {
            PestArenaDefinition.RepairStationDefinition station = new PestArenaDefinition.RepairStationDefinition();
            station.id = "repair_" + i;
            station.spawn.pos = new double[]{i + 40, 1, i};
            arena.repairStations.add(station);
        }

        SideObjectiveType[] types = new SideObjectiveType[]{
                SideObjectiveType.CAPTURE_POINT,
                SideObjectiveType.ESCORT_PAYLOAD,
                SideObjectiveType.DEFEND_NODE
        };
        for (int i = 0; i < 3; i++) {
            PestArenaDefinition.ObjectiveAnchorDefinition anchor = new PestArenaDefinition.ObjectiveAnchorDefinition();
            anchor.id = "objective_" + i;
            anchor.objectiveType = types[i];
            anchor.spawn.pos = new double[]{i + 45, 1, i};
            arena.objectiveAnchors.add(anchor);
        }

        arena.normalize();
        return arena;
    }
}
