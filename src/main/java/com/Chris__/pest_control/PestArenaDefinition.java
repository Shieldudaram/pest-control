package com.Chris__.pest_control;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PestArenaDefinition {

    public int version = 1;

    public String id = "";
    public Tier tier = Tier.NOVICE;
    public String world = "";

    public Aabb battleBounds = new Aabb();
    public Aabb lobbyBounds = new Aabb();
    public Aabb joinBoatBounds = new Aabb();

    public Spawn playerSpawn = new Spawn();
    public Spawn respawnSpawn = new Spawn();
    public Spawn knightSpawn = new Spawn();

    public List<PortalDefinition> portals = new ArrayList<>(List.of(
            new PortalDefinition(0),
            new PortalDefinition(1),
            new PortalDefinition(2),
            new PortalDefinition(3)
    ));

    public List<GateDefinition> gates = new ArrayList<>();
    public List<BarricadeDefinition> barricades = new ArrayList<>();
    public List<TurretDefinition> turrets = new ArrayList<>();
    public List<RepairStationDefinition> repairStations = new ArrayList<>();
    public List<ObjectiveAnchorDefinition> objectiveAnchors = new ArrayList<>();

    public void normalize() {
        if (id == null) id = "";
        id = id.trim().toLowerCase(Locale.ROOT);
        if (tier == null) tier = Tier.NOVICE;
        if (world == null) world = "";

        if (battleBounds == null) battleBounds = new Aabb();
        if (lobbyBounds == null) lobbyBounds = new Aabb();
        if (joinBoatBounds == null) joinBoatBounds = new Aabb();
        battleBounds.normalize();
        lobbyBounds.normalize();
        joinBoatBounds.normalize();

        if (playerSpawn == null) playerSpawn = new Spawn();
        if (respawnSpawn == null) respawnSpawn = new Spawn();
        if (knightSpawn == null) knightSpawn = new Spawn();
        playerSpawn.normalize();
        respawnSpawn.normalize();
        knightSpawn.normalize();

        if (portals == null) portals = new ArrayList<>();
        while (portals.size() < 4) {
            portals.add(new PortalDefinition(portals.size()));
        }
        if (portals.size() > 4) {
            portals = new ArrayList<>(portals.subList(0, 4));
        }
        for (int i = 0; i < portals.size(); i++) {
            PortalDefinition portal = portals.get(i);
            if (portal == null) portal = new PortalDefinition(i);
            portal.index = i;
            portal.normalize();
            portals.set(i, portal);
        }

        if (gates == null) gates = new ArrayList<>();
        if (barricades == null) barricades = new ArrayList<>();
        if (turrets == null) turrets = new ArrayList<>();
        if (repairStations == null) repairStations = new ArrayList<>();
        if (objectiveAnchors == null) objectiveAnchors = new ArrayList<>();

        for (GateDefinition gate : gates) {
            if (gate != null) gate.normalize();
        }
        for (BarricadeDefinition barricade : barricades) {
            if (barricade != null) barricade.normalize();
        }
        for (TurretDefinition turret : turrets) {
            if (turret != null) turret.normalize();
        }
        for (RepairStationDefinition station : repairStations) {
            if (station != null) station.normalize();
        }
        for (ObjectiveAnchorDefinition anchor : objectiveAnchors) {
            if (anchor != null) anchor.normalize();
        }
    }

    public boolean isValidForUse() {
        if (id == null || id.isBlank()) return false;
        if (world == null || world.isBlank()) return false;
        if (!battleBounds.isValid() || !lobbyBounds.isValid() || !joinBoatBounds.isValid()) return false;
        if (!playerSpawn.isValid() || !respawnSpawn.isValid() || !knightSpawn.isValid()) return false;
        if (portals == null || portals.size() != 4) return false;
        for (PortalDefinition portal : portals) {
            if (portal == null || !portal.isValid()) return false;
        }
        return true;
    }

    public static final class Aabb {
        public int[] min = new int[]{0, 0, 0};
        public int[] max = new int[]{0, 0, 0};

        public void normalize() {
            if (min == null || min.length != 3) min = new int[]{0, 0, 0};
            if (max == null || max.length != 3) max = new int[]{0, 0, 0};

            int minX = Math.min(min[0], max[0]);
            int minY = Math.min(min[1], max[1]);
            int minZ = Math.min(min[2], max[2]);
            int maxX = Math.max(min[0], max[0]);
            int maxY = Math.max(min[1], max[1]);
            int maxZ = Math.max(min[2], max[2]);

            min[0] = minX;
            min[1] = minY;
            min[2] = minZ;
            max[0] = maxX;
            max[1] = maxY;
            max[2] = maxZ;
        }

        public boolean isValid() {
            return min != null && min.length == 3 && max != null && max.length == 3;
        }

        public boolean containsBlock(int x, int y, int z) {
            if (!isValid()) return false;
            return x >= min[0] && x <= max[0]
                    && y >= min[1] && y <= max[1]
                    && z >= min[2] && z <= max[2];
        }
    }

    public static final class Spawn {
        public double[] pos = new double[]{0.5, 0.0, 0.5};
        public float[] rot = new float[]{0f, 0f, 0f};

        public void normalize() {
            if (pos == null || pos.length != 3) pos = new double[]{0.5, 0.0, 0.5};
            if (rot == null || rot.length != 3) rot = new float[]{0f, 0f, 0f};
        }

        public boolean isValid() {
            return pos != null && pos.length == 3 && rot != null && rot.length == 3;
        }
    }

    public static final class PortalDefinition {
        public int index;
        public String id = "portal";
        public Spawn coreSpawn = new Spawn();
        public Spawn enemySpawn = new Spawn();
        public int baseHp = 1000;

        public PortalDefinition() {
            this(0);
        }

        public PortalDefinition(int index) {
            this.index = index;
            this.id = "portal_" + (index + 1);
        }

        public void normalize() {
            if (id == null || id.isBlank()) {
                id = "portal_" + (index + 1);
            }
            if (coreSpawn == null) coreSpawn = new Spawn();
            if (enemySpawn == null) enemySpawn = new Spawn();
            coreSpawn.normalize();
            enemySpawn.normalize();
            if (baseHp <= 0) baseHp = 1000;
        }

        public boolean isValid() {
            return coreSpawn != null && coreSpawn.isValid() && enemySpawn != null && enemySpawn.isValid() && baseHp > 0;
        }
    }

    public static class InteractionNode {
        public String id = "";
        public Spawn spawn = new Spawn();

        public void normalize() {
            if (id == null) id = "";
            if (spawn == null) spawn = new Spawn();
            spawn.normalize();
        }
    }

    public static final class GateDefinition extends InteractionNode {
        public int maxHp = 900;

        @Override
        public void normalize() {
            super.normalize();
            if (maxHp <= 0) maxHp = 900;
        }
    }

    public static final class BarricadeDefinition extends InteractionNode {
        public int maxHp = 500;

        @Override
        public void normalize() {
            super.normalize();
            if (maxHp <= 0) maxHp = 500;
        }
    }

    public static final class TurretDefinition extends InteractionNode {
        public int maxHp = 1300;

        @Override
        public void normalize() {
            super.normalize();
            if (maxHp <= 0) maxHp = 1300;
        }
    }

    public static final class RepairStationDefinition extends InteractionNode {
        public double channelSeconds = 2.5;

        @Override
        public void normalize() {
            super.normalize();
            if (channelSeconds <= 0.1) channelSeconds = 2.5;
        }
    }

    public static final class ObjectiveAnchorDefinition extends InteractionNode {
        public SideObjectiveType objectiveType = SideObjectiveType.CAPTURE_POINT;

        @Override
        public void normalize() {
            super.normalize();
            if (objectiveType == null) objectiveType = SideObjectiveType.CAPTURE_POINT;
        }
    }
}
