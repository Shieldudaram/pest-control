package com.Chris__.pest_control.config;

import com.Chris__.pest_control.EnemyType;
import com.Chris__.pest_control.SideObjectiveType;
import com.Chris__.pest_control.TeamBuffType;
import com.Chris__.pest_control.Tier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PestConfig {

    public int version = 1;

    public Queue queue = new Queue();
    public Timers timers = new Timers();
    public Tiers tiers = new Tiers();
    public EnemyMappings enemyMappings = new EnemyMappings();
    public Interactions interactions = new Interactions();
    public SideObjectives sideObjectives = new SideObjectives();
    public Claims claims = new Claims();
    public Ui ui = new Ui();

    public static final class Queue {
        public boolean globalSingleActiveMatch = true;
        public int minPlayersToStart = 1;
        public String scheduler = "oldest_ticket";
    }

    public static final class Timers {
        public int countdownSeconds = 20;
        public int matchDurationSeconds = 20 * 60;
        public int roundEndSeconds = 12;
        public int resetSeconds = 8;
        public int sideObjectiveDurationSeconds = 60;
    }

    public static final class Tiers {
        public TierTuning novice = TierTuning.defaultFor(Tier.NOVICE);
        public TierTuning intermediate = TierTuning.defaultFor(Tier.INTERMEDIATE);
        public TierTuning veteran = TierTuning.defaultFor(Tier.VETERAN);

        public TierTuning forTier(Tier tier) {
            if (tier == null) return novice;
            return switch (tier) {
                case NOVICE -> novice;
                case INTERMEDIATE -> intermediate;
                case VETERAN -> veteran;
            };
        }
    }

    public static final class TierTuning {
        public double tierPressure = 1.0;
        public int flatWinPoints = 20;
        public int baseSpawnCount = 2;
        public int basePortalHp = 2200;
        public int baseKnightHp = 2500;

        public static TierTuning defaultFor(Tier tier) {
            TierTuning t = new TierTuning();
            if (tier == Tier.INTERMEDIATE) {
                t.tierPressure = 1.35;
                t.flatWinPoints = 35;
                t.baseSpawnCount = 3;
                t.basePortalHp = 3200;
                t.baseKnightHp = 3600;
            } else if (tier == Tier.VETERAN) {
                t.tierPressure = 1.75;
                t.flatWinPoints = 55;
                t.baseSpawnCount = 4;
                t.basePortalHp = 4600;
                t.baseKnightHp = 5000;
            }
            return t;
        }
    }

    public static final class EnemyMappings {
        public Map<EnemyType, String> ids = new EnumMap<>(EnemyType.class);

        public EnemyMappings() {
            ids.put(EnemyType.BRAWLER, "MONSTER_ZOMBIE");
            ids.put(EnemyType.DEFILER, "MONSTER_SKELETON");
            ids.put(EnemyType.RAVAGER, "MONSTER_TROLL");
            ids.put(EnemyType.SHIFTER, "MONSTER_SPIDER");
            ids.put(EnemyType.SPINNER, "MONSTER_SLIME");
            ids.put(EnemyType.SPLATTER, "MONSTER_CREEPER");
            ids.put(EnemyType.TORCHER, "MONSTER_GOBLIN");
        }
    }

    public static final class Interactions {
        public int defaultGateHp = 900;
        public int defaultBarricadeHp = 500;
        public int defaultTurretHp = 1300;
        public int repairPerSecond = 250;
        public int sabotagePerSecond = 180;
        public int turretDamagePerShot = 130;
        public int turretFireIntervalTicks = 20;
        public int minInteractablesPerArena = 20;
    }

    public static final class SideObjectives {
        public List<SideObjectiveType> enabled = new ArrayList<>(List.of(
                SideObjectiveType.CAPTURE_POINT,
                SideObjectiveType.ESCORT_PAYLOAD,
                SideObjectiveType.DEFEND_NODE
        ));
        public Buffs buffs = new Buffs();

        public static final class Buffs {
            public double battleFervorDamageMultiplier = 1.15;
            public double fieldRepairsMultiplier = 1.40;
            public double rapidResponseSpeedMultiplier = 1.20;

            public TeamBuffType[] ordered() {
                return new TeamBuffType[]{
                        TeamBuffType.BATTLE_FERVOR,
                        TeamBuffType.FIELD_REPAIRS,
                        TeamBuffType.RAPID_RESPONSE
                };
            }
        }
    }

    public static final class Claims {
        public String mode = "STRICT_BLOCK";
    }

    public static final class Ui {
        public String hudMode = "CHAT_ONLY";
        public int chatUpdateSeconds = 10;
        public String hudPath = "Hud/PestControl/Status.ui";
        public String queuePath = "Pages/PestControl/Queue.ui";
        public String shopPath = "Pages/PestControl/Shop.ui";
    }
}
