package com.aetherianartificer.townstead.recognition;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects building additions and tier changes across a village and fires a
 * recognition effect + announcement for each. Generic across all MCA building
 * types — docks, kitchens, cafes, houses, whatever else gets registered.
 *
 * Keeps a per-village snapshot of {@code buildingId → type}. First reconcile
 * call seeds the snapshot silently (existing buildings are "background state",
 * not news). Subsequent calls diff and emit:
 *   ESTABLISHED — a buildingId that wasn't in the previous snapshot
 *   UPGRADED    — an existing buildingId whose type string changed
 * Removals don't emit anything.
 *
 * Effect intensity scales with the new type's tier suffix: tiered buildings
 * with {@code _l3} or higher get {@code GRAND}; everything else is
 * {@code MAJOR}. The building's declared color tints a layer of dust particles
 * over the area so each building family has a distinct visual fingerprint.
 */
public final class BuildingRecognitionTracker {
    private static final Logger LOG = LoggerFactory.getLogger(Townstead.MOD_ID + "/BuildingRecognitionTracker");

    // Key: "<dim>|<villageId>". Value: Map<buildingId, typeName>.
    private static final Map<String, Map<Integer, String>> SNAPSHOT = new ConcurrentHashMap<>();
    // Auxiliary snapshot of dock_* building bounds, keyed the same way.
    // Docks can have their IDs churn (removal+resync, tier-ups that grow the
    // footprint, dedupe swaps) while the structure is physically unchanged.
    // We match by overlapping bounds across snapshots so we don't announce
    // "Pier built!" on every resync.
    private static final Map<String, Map<Integer, int[]>> DOCK_BOUNDS = new ConcurrentHashMap<>();

    private static final double ANNOUNCE_RADIUS = 64.0;

    private BuildingRecognitionTracker() {}

    public static void reconcile(ServerLevel level, Village village) {
        if (level == null || village == null) return;
        String key = keyOf(level, village);
        Map<Integer, String> current = snapshotCurrent(village);
        Map<Integer, int[]> currentDockBounds = snapshotDockBounds(village);
        Map<Integer, String> prev = SNAPSHOT.get(key);
        Map<Integer, int[]> prevDockBounds = DOCK_BOUNDS.getOrDefault(key, Map.of());
        if (prev == null) {
            SNAPSHOT.put(key, current);
            DOCK_BOUNDS.put(key, currentDockBounds);
            return;
        }
        for (Map.Entry<Integer, String> e : current.entrySet()) {
            String prevType = prev.get(e.getKey());
            String curType = e.getValue();
            if (prevType == null) {
                if (curType != null && curType.startsWith("dock_")
                        && dockBoundsOverlappedPrev(currentDockBounds.get(e.getKey()), prevDockBounds)) {
                    continue;
                }
                fireEstablished(level, village, e.getKey(), curType);
            } else if (!prevType.equals(curType)) {
                fireUpgraded(level, village, e.getKey(), prevType, curType);
            }
        }
        SNAPSHOT.put(key, current);
        DOCK_BOUNDS.put(key, currentDockBounds);
    }

    private static Map<Integer, int[]> snapshotDockBounds(Village village) {
        Map<Integer, int[]> out = new HashMap<>();
        for (Building b : village.getBuildings().values()) {
            String t = b.getType();
            if (t == null || !t.startsWith("dock_")) continue;
            BlockPos p0 = b.getPos0();
            BlockPos p1 = b.getPos1();
            out.put(b.getId(), new int[] {p0.getX(), p0.getY(), p0.getZ(), p1.getX(), p1.getY(), p1.getZ()});
        }
        return out;
    }

    private static boolean dockBoundsOverlappedPrev(int[] bb, Map<Integer, int[]> prev) {
        if (bb == null || prev.isEmpty()) return false;
        for (int[] other : prev.values()) {
            if (bb[0] <= other[3] && bb[3] >= other[0]
                    && bb[1] <= other[4] && bb[4] >= other[1]
                    && bb[2] <= other[5] && bb[5] >= other[2]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Seed the snapshot silently for a village's current state. Called from
     * the server-started hook so pre-existing buildings loaded from NBT
     * don't trigger events on the player's first interaction.
     */
    public static void seed(ServerLevel level, Village village) {
        if (level == null || village == null) return;
        String key = keyOf(level, village);
        SNAPSHOT.put(key, snapshotCurrent(village));
        DOCK_BOUNDS.put(key, snapshotDockBounds(village));
    }

    private static String keyOf(ServerLevel level, Village village) {
        return level.dimension().location().toString() + "|" + village.getId();
    }

    private static Map<Integer, String> snapshotCurrent(Village village) {
        Map<Integer, String> current = new HashMap<>();
        for (Building b : village.getBuildings().values()) {
            current.put(b.getId(), b.getType());
        }
        return current;
    }

    private static void fireEstablished(ServerLevel level, Village village, int buildingId, String typeName) {
        Building building = village.getBuildings().get(buildingId);
        if (building == null) return;
        BuildingType bt = building.getBuildingType();
        RecognitionEffects.Tier effectTier = tierFor(typeName);
        BoundingBox bounds = boundsOf(building);
        RecognitionEffects.playArea(level, bounds, effectTier, bt.getColor());
        Component displayName = Component.translatable("buildingType." + typeName);
        MutableComponent message = Component.translatable("townstead.building.established", displayName)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        RecognitionEffects.announce(level, centerOf(building), message, ANNOUNCE_RADIUS);
        LOG.info("[Recognition] established '{}' in village {}", typeName, village.getId());
    }

    private static void fireUpgraded(ServerLevel level, Village village, int buildingId,
                                     String prevType, String newType) {
        Building building = village.getBuildings().get(buildingId);
        if (building == null) return;
        BuildingType bt = building.getBuildingType();
        RecognitionEffects.Tier effectTier = tierFor(newType);
        BoundingBox bounds = boundsOf(building);
        RecognitionEffects.playArea(level, bounds, effectTier, bt.getColor());
        Component prevName = Component.translatable("buildingType." + prevType);
        Component newName = Component.translatable("buildingType." + newType);
        MutableComponent message = Component.translatable("townstead.building.upgraded", prevName, newName)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        RecognitionEffects.announce(level, centerOf(building), message, ANNOUNCE_RADIUS);
        LOG.info("[Recognition] upgraded '{}' -> '{}' in village {}", prevType, newType, village.getId());
    }

    /**
     * Pick the recognition intensity based on the tier suffix (_lN) of the
     * building type. Tier 3+ gets GRAND — the max-celebration look. Everything
     * else (lower tiers + non-tiered types) gets MAJOR.
     */
    private static RecognitionEffects.Tier tierFor(String typeName) {
        int idx = typeName.lastIndexOf("_l");
        if (idx > 0 && idx < typeName.length() - 2) {
            String suffix = typeName.substring(idx + 2);
            if (!suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)) {
                try {
                    int tier = Integer.parseInt(suffix);
                    if (tier >= 3) return RecognitionEffects.Tier.GRAND;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return RecognitionEffects.Tier.MAJOR;
    }

    private static BoundingBox boundsOf(Building b) {
        BlockPos p0 = b.getPos0();
        BlockPos p1 = b.getPos1();
        return new BoundingBox(p0.getX(), p0.getY(), p0.getZ(), p1.getX(), p1.getY(), p1.getZ());
    }

    private static Vec3 centerOf(Building b) {
        BlockPos c = b.getCenter();
        return new Vec3(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5);
    }
}
