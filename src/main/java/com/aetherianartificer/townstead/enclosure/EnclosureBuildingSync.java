package com.aetherianartificer.townstead.enclosure;

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
 * Injects a classified {@link Enclosure} into its nearest {@link Village} as
 * a synthetic {@link Building}. Mirrors {@code DockBuildingSync} almost
 * one-for-one: stable negative-hash ID derived from the bounds min-corner,
 * blocks map populated from perimeter + interior signature blocks so MCA's
 * {@code validateBlocks} prunes correctly when the player breaks a fence or
 * removes the signature block, {@code isTypeForced = true} so
 * {@code determineType()} doesn't reassign the type on its own rescans.
 *
 * <p>The companion {@code BuildingValidateOpenAirMixin} short-circuits MCA's
 * flood-fill validation for any type registered via {@link EnclosureTypeIndex},
 * so the open-air pen isn't wiped the next time MCA revalidates.
 */
public final class EnclosureBuildingSync {
    private static final Logger LOG = LoggerFactory.getLogger(Townstead.MOD_ID + "/EnclosureBuildingSync");

    private EnclosureBuildingSync() {}

    public static boolean sync(ServerLevel level, Enclosure enclosure, String buildingType) {
        if (level == null || enclosure == null || buildingType == null) return false;
        BoundingBox bb = enclosure.bounds();
        BlockPos center = new BlockPos(
                (bb.minX() + bb.maxX()) / 2,
                bb.minY(),
                (bb.minZ() + bb.maxZ()) / 2);
        VillageManager manager = VillageManager.get(level);
        Optional<Village> villageOpt = manager.findNearestVillage(center, Village.MERGE_MARGIN);
        if (villageOpt.isEmpty()) return false;
        Village village = villageOpt.get();

        EnclosureSuppression.clearOverlapping(level, village, bb);
        if (EnclosureSuppression.isSuppressed(level, village, bb)) return false;

        int id = findOverlappingEnclosureId(village, bb).orElseGet(() -> synthIdFor(bb));
        Building existing = village.getBuildings().get(id);
        boolean purged = purgeOverlappingStaleEnclosures(village, bb, id);
        if (existing != null && buildingType.equals(existing.getType()) && !purged) {
            return false;
        }

        CompoundTag nbt = buildingNbt(level, id, buildingType, enclosure);
        Building building = new Building(nbt);
        village.getBuildings().put(id, building);
        village.calculateDimensions();
        village.markDirty();
        LOG.info("[EnclosureSync] {} {} at [{},{},{}]..[{},{},{}] id={}",
                existing == null ? "injected" : "updated", buildingType,
                bb.minX(), bb.minY(), bb.minZ(),
                bb.maxX(), bb.maxY(), bb.maxZ(), id);
        BuildingRecognitionTracker.reconcile(level, village);
        SpiritReconciler.reconcileVillage(level, village);
        return true;
    }

    private static Optional<Integer> findOverlappingEnclosureId(Village village, BoundingBox bb) {
        for (Map.Entry<Integer, Building> e : village.getBuildings().entrySet()) {
            Building other = e.getValue();
            String t = other.getType();
            if (t == null || !EnclosureTypeIndex.isEnclosureType(t)) continue;
            if (boundsIntersect(other, bb)) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }

    private static boolean purgeOverlappingStaleEnclosures(Village village, BoundingBox bb, int selfId) {
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, Building> e : village.getBuildings().entrySet()) {
            Integer otherId = e.getKey();
            if (otherId == selfId) continue;
            Building other = e.getValue();
            String t = other.getType();
            if (t == null || !EnclosureTypeIndex.isEnclosureType(t)) continue;
            if (boundsIntersect(other, bb)) toRemove.add(otherId);
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

    private static CompoundTag buildingNbt(ServerLevel level, int id, String type, Enclosure enclosure) {
        BoundingBox bb = enclosure.bounds();
        CompoundTag v = new CompoundTag();
        v.putInt("id", id);
        v.putInt("size", enclosure.interiorSize() + enclosure.perimeter().size());
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
        v.put("blocks2", collectEnclosureBlocksNbt(level, enclosure));
        return v;
    }

    /**
     * Scan the enclosure footprint for perimeter (fences/gates/walls) and
     * interior signature blocks (anything non-air in the interior column),
     * grouped by specific block id. Populates the Building's blocks map so
     * MCA's {@code validateBlocks} prunes entries as the player breaks them
     * and eventually returns the building to {@code TOO_SMALL}.
     */
    private static CompoundTag collectEnclosureBlocksNbt(ServerLevel level, Enclosure enclosure) {
        Map<String, List<BlockPos>> byId = new HashMap<>();
        for (BlockPos p : enclosure.perimeter()) {
            BlockState s = level.getBlockState(p);
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(s.getBlock());
            byId.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(p.immutable());
        }
        // Scan dy=-1..3, but at dy=-1 only include blocks registered as a
        // signature (blood_grate placed flush with the ground, etc.) —
        // the floor itself (grass/dirt/paths) must not enter the tracked
        // map, or MCA's validateBlocks would keep the building alive off
        // the ground long after the fences are gone.
        for (BlockPos p : enclosure.interior()) {
            for (int dy = -1; dy <= 3; dy++) {
                BlockPos q = p.offset(0, dy, 0);
                BlockState s = level.getBlockState(q);
                if (s.isAir() || s.canBeReplaced()) continue;
                if (dy < 0 && !EnclosureTypeIndex.anySpecRequires(s)) continue;
                ResourceLocation key = BuiltInRegistries.BLOCK.getKey(s.getBlock());
                byId.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(q.immutable());
            }
        }
        CompoundTag blocks2 = new CompoundTag();
        for (Map.Entry<String, List<BlockPos>> entry : byId.entrySet()) {
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

    private static int synthIdFor(BoundingBox bb) {
        int h = (bb.minX() * 31 + bb.minY()) * 31 + bb.minZ();
        return h | Integer.MIN_VALUE;
    }
}
