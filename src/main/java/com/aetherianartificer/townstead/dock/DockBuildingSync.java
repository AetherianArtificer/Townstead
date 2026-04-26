package com.aetherianartificer.townstead.dock;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.recognition.BuildingRecognitionTracker;
import com.aetherianartificer.townstead.spirit.SpiritReconciler;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Injects a detected {@link Dock} into its nearest {@link Village} as a
 * synthetic {@link Building}, so the dock appears in MCA's blueprint UI
 * alongside normal buildings. Idempotent: repeated calls for the same dock
 * short-circuit unless the tier has changed.
 *
 * Lifecycle interaction with MCA:
 *  - Building type set to {@code dock_l{tier}} ({@link #buildingNbt}),
 *    {@code isTypeForced = true} so {@code determineType()} won't reassign
 *    the type when block counts drift.
 *  - The companion mixin {@code BuildingValidateOpenAirMixin} short-circuits
 *    {@code validateBuilding} for dock types so MCA's flood-fill doesn't
 *    wipe our open-air building when a nearby block change triggers re-scan.
 *    We use the mixin (rather than the JSON {@code grouped: true} flag)
 *    because grouped-mode would make MCA also auto-create its own Building
 *    from the tracked planks in parallel with our synthetic, causing
 *    runaway duplicate buildings on every rescan.
 *  - Building ID is a stable negative hash of the dock's bounds min-corner,
 *    keeping it out of MCA's positive-incrementing ID namespace. On world
 *    reload the Building is restored via NBT, and subsequent sync calls find
 *    it by ID and no-op unless tier has changed.
 *
 * Dock-surface positions across the footprint are seeded into the building's
 * blocks map grouped by specific block id (oak_planks, oak_slab, etc.), so
 * {@code validateBlocks} can correctly prune entries when dock blocks are
 * broken. If all tracked blocks disappear, MCA removes the building.
 */
public final class DockBuildingSync {
    private static final Logger LOG = LoggerFactory.getLogger(Townstead.MOD_ID + "/DockBuildingSync");

    private DockBuildingSync() {}

    /**
     * Ensure a Building entry representing {@code dock} exists in its nearest
     * village, at the correct tier. Returns true if the village was modified.
     */
    public static boolean sync(ServerLevel level, Dock dock) {
        return sync(level, dock, null);
    }

    /**
     * Player-triggered reports can originate from the village edge while the
     * detected dock's center sits just beyond MCA's merge margin. In that case
     * prefer the dock center, but fall back to the report anchor so a dock the
     * player adds from inside the village still attaches to that village.
     */
    public static boolean sync(ServerLevel level, Dock dock, BlockPos reportAnchor) {
        if (level == null || dock == null) return false;
        BoundingBox bb = dock.bounds();
        BlockPos center = new BlockPos(
                (bb.minX() + bb.maxX()) / 2,
                bb.minY(),
                (bb.minZ() + bb.maxZ()) / 2);
        VillageManager manager = VillageManager.get(level);
        Optional<Village> villageOpt = manager.findNearestVillage(center, Village.MERGE_MARGIN);
        if (villageOpt.isEmpty() && reportAnchor != null) {
            int reportMargin = Math.max(Village.MERGE_MARGIN, Village.PLAYER_BORDER_MARGIN);
            villageOpt = manager.findNearestVillage(reportAnchor, reportMargin);
        }
        if (villageOpt.isEmpty()) {
            // No hosting village yet. Don't fabricate one — wait for MCA to
            // establish a village via normal building reports, then the dock
            // will sync the next time the player refreshes the blueprint
            // near these planks.
            LOG.info("[DockSync] skipped dock at [{},{},{}]..[{},{},{}] — no hosting village near center {} or report anchor {}",
                    bb.minX(), bb.minY(), bb.minZ(),
                    bb.maxX(), bb.maxY(), bb.maxZ(), center, reportAnchor);
            return false;
        }
        Village village = villageOpt.get();
        // The dock's plank shape may have changed since the player last
        // dismissed it; drop any stale suppression that doesn't match the
        // current footprint exactly, and bail if an exact-match suppression
        // is still in effect.
        DockSuppression.clearOverlapping(level, village, bb);
        if (DockSuppression.isSuppressed(level, village, bb)) return false;
        String desiredType = "dock_l" + dock.tier();
        // Prefer the ID of an existing overlapping dock so a dock reshape
        // (planks added/removed, bounds shifted) updates the same Building
        // instead of creating a fresh one + orphaning the old. Stable IDs
        // keep BuildingRecognitionTracker from seeing reshapes as new docks.
        int id = findOverlappingDockId(village, bb).orElseGet(() -> synthIdFor(dock));

        Building existing = village.getBuildings().get(id);
        boolean purged = purgeOverlappingStaleDocks(village, bb, id);
        if (existing != null && desiredType.equals(existing.getType()) && !purged) {
            DockLocationIndex.rebuildVillage(level, village);
            return false;
        }
        // Don't let an auto-scan downgrade a dock. If a transient scan result
        // comes back at a lower tier than the existing entry (e.g., the player
        // is clicking Refresh just outside the deck, so some lanterns fall
        // outside the scan box), keep the existing tier. Real destruction of
        // planks pushes the dock below tier 1 and the Building validates to
        // TOO_SMALL via MCA's grouped-validation path, which removes it properly.
        if (existing != null && tierOf(existing.getType()) > dock.tier() && !purged) {
            DockLocationIndex.rebuildVillage(level, village);
            return false;
        }

        CompoundTag nbt = buildingNbt(level, id, desiredType, dock);
        Building building = new Building(nbt);
        village.getBuildings().put(id, building);
        village.calculateDimensions();
        village.markDirty();
        LOG.info("[DockSync] {} {} at [{},{},{}]..[{},{},{}] id={}",
                existing == null ? "injected" : "updated", desiredType,
                bb.minX(), bb.minY(), bb.minZ(),
                bb.maxX(), bb.maxY(), bb.maxZ(), id);
        // Let the generic tracker pick up add/tier-up events and fire the
        // recognition effects + announcement.
        DockLocationIndex.rebuildVillage(level, village);
        BuildingRecognitionTracker.reconcile(level, village);
        SpiritReconciler.reconcileVillage(level, village);
        return true;
    }

    /**
     * Extract the tier number from a {@code dock_lN} type string. Returns 0
     * for anything that doesn't fit the pattern, so the "don't downgrade"
     * guard only fires between comparable dock types.
     */
    private static int tierOf(String type) {
        if (type == null || !type.startsWith("dock_l")) return 0;
        String suffix = type.substring("dock_l".length());
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) return 0;
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Optional<Integer> findOverlappingDockId(Village village, BoundingBox bb) {
        for (Map.Entry<Integer, Building> e : village.getBuildings().entrySet()) {
            Building other = e.getValue();
            String t = other.getType();
            if (t == null || !t.startsWith("dock_")) continue;
            if (boundsIntersect(other, bb)) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }

    /**
     * Remove any other {@code dock_*} building whose bounds intersect this
     * dock's footprint. Handles (a) tier-up when a plank expansion shifted
     * the min-corner and thus the synthetic ID, and (b) dock reshape. Caller
     * provides {@code selfId} so we don't remove the entry we're about to
     * update in place.
     */
    private static boolean purgeOverlappingStaleDocks(Village village, BoundingBox bb, int selfId) {
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, Building> e : village.getBuildings().entrySet()) {
            Integer otherId = e.getKey();
            if (otherId == selfId) continue;
            Building other = e.getValue();
            String t = other.getType();
            if (t == null || !t.startsWith("dock_")) continue;
            if (boundsIntersect(other, bb)) {
                toRemove.add(otherId);
            }
        }
        for (int rid : toRemove) village.removeBuilding(rid);
        return !toRemove.isEmpty();
    }

    private static boolean boundsIntersect(Building other, BoundingBox bb) {
        BlockPos op0 = other.getPos0();
        BlockPos op1 = other.getPos1();
        return op0.getX() <= bb.maxX() && op1.getX() >= bb.minX()
                && op0.getY() <= bb.maxY() && op1.getY() >= bb.minY()
                && op0.getZ() <= bb.maxZ() && op1.getZ() >= bb.minZ();
    }

    private static CompoundTag buildingNbt(ServerLevel level, int id, String type, Dock dock) {
        BoundingBox bb = dock.bounds();
        CompoundTag v = new CompoundTag();
        v.putInt("id", id);
        v.putInt("size", dock.plankCount());
        v.putInt("pos0X", bb.minX());
        v.putInt("pos0Y", bb.minY());
        v.putInt("pos0Z", bb.minZ());
        v.putInt("pos1X", bb.maxX());
        v.putInt("pos1Y", bb.maxY());
        v.putInt("pos1Z", bb.maxZ());
        v.putInt("posX", (bb.minX() + bb.maxX()) / 2);
        v.putInt("posY", (bb.minY() + bb.maxY()) / 2);
        v.putInt("posZ", (bb.minZ() + bb.maxZ()) / 2);
        v.putBoolean("isTypeForced", true);
        v.putString("type", type);
        v.putBoolean("strictScan", false);
        v.put("blocks2", collectDockSurfaceBlocksNbt(level, bb));
        return v;
    }

    /**
     * Scan the dock bounds for dock-surface blocks and return a blocks-map NBT
     * grouped by specific block id. This populates the Building's blocks
     * map so MCA's {@code validateBlocks} can correctly prune entries when
     * dock blocks are broken (the Building is then invalidated only once ALL
     * its tracked deck blocks are gone).
     */
    private static CompoundTag collectDockSurfaceBlocksNbt(ServerLevel level, BoundingBox bb) {
        Map<String, List<BlockPos>> byBlockId = new HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(
                new BlockPos(bb.minX(), bb.minY(), bb.minZ()),
                new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ()))) {
            BlockState s = level.getBlockState(p);
            if (!DockScanner.isDockSurface(s)) continue;
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(s.getBlock());
            byBlockId.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(p.immutable());
        }
        CompoundTag blocks2 = new CompoundTag();
        for (Map.Entry<String, List<BlockPos>> entry : byBlockId.entrySet()) {
            ListTag list = new ListTag();
            for (BlockPos pos : entry.getValue()) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("x", pos.getX());
                posTag.putInt("y", pos.getY());
                posTag.putInt("z", pos.getZ());
                list.add(posTag);
            }
            blocks2.put(entry.getKey(), list);
        }
        return blocks2;
    }

    /**
     * Stable negative ID derived from the dock's bounds min-corner. Positive
     * IDs belong to MCA's auto-increment namespace; sign-bit-flipped keeps us
     * disjoint. Min-corner is stable as long as the plank footprint is, which
     * is the common case — tier-ups that grow the footprint shift the ID, and
     * we rely on {@link #purgeOverlappingStaleDocks} to evict the old entry.
     */
    private static int synthIdFor(Dock dock) {
        BoundingBox bb = dock.bounds();
        int h = (bb.minX() * 31 + bb.minY()) * 31 + bb.minZ();
        return h | Integer.MIN_VALUE;
    }
}
