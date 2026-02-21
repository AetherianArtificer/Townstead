package com.aetherianartificer.townstead.upgrade;

import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class BuildingTierReconciler {
    private static final Logger LOG = LoggerFactory.getLogger("Townstead/TierReconciler");

    private BuildingTierReconciler() {}

    public static void reconcileVillage(Village village, ServerLevel level) {
        if (village == null) return;
        for (Building building : village.getBuildings().values()) {
            reconcileBuilding(building, level);
        }
    }

    private static void reconcileBuilding(Building building, ServerLevel level) {
        if (building == null) return;
        String currentType = building.getType();
        TierRef ref = parseTierRef(currentType);
        if (ref == null) return;

        LOG.info("[Reconciler] Building '{}' forced={}", currentType, building.isTypeForced());
        if (!building.isTypeForced()) return;

        String best = highestSatisfiableTierType(ref.prefix(), building, level);
        LOG.info("[Reconciler] Best tier for '{}': {}", currentType, best);
        if (best == null) {
            building.setTypeForced(false);
            building.determineType();
            LOG.info("[Reconciler] No tier satisfied, unforced -> {}", building.getType());
            return;
        }
        if (!best.equals(currentType)) {
            building.setType(best);
            LOG.info("[Reconciler] Updated {} -> {}", currentType, best);
        }
    }

    private static String highestSatisfiableTierType(String prefix, Building building, ServerLevel level) {
        String best = null;
        Map<ResourceLocation, Integer> liveCounts = collectLiveBlockCounts(building, level);
        LOG.info("[Reconciler] Live block counts ({} unique types): {}", liveCounts.size(), liveCounts);
        for (int tier = 1; tier <= 32; tier++) {
            String candidateId = prefix + "_l" + tier;
            if (!BuildingTypes.getInstance().getBuildingTypes().containsKey(candidateId)) break;
            BuildingType bt = BuildingTypes.getInstance().getBuildingType(candidateId);
            if (bt == null) break;
            boolean meets = meetsRequirements(bt, liveCounts, candidateId);
            LOG.info("[Reconciler]   {} meets={}", candidateId, meets);
            if (!meets) continue;
            best = candidateId;
        }
        return best;
    }

    private static boolean meetsRequirements(BuildingType type, Map<ResourceLocation, Integer> liveCounts, String typeId) {
        for (Map.Entry<ResourceLocation, Integer> req : type.getGroups().entrySet()) {
            int have = countMatchingBlocks(liveCounts, req.getKey());
            if (have < req.getValue()) {
                LOG.info("[Reconciler]     {} FAIL: need {} have {} for '{}'", typeId, req.getValue(), have, req.getKey());
                return false;
            }
        }
        return true;
    }

    private static Map<ResourceLocation, Integer> collectLiveBlockCounts(Building building, ServerLevel level) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        for (BlockPos pos : BlockPos.betweenClosed(p0, p1)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id == null) continue;
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }

    private static int countMatchingBlocks(Map<ResourceLocation, Integer> presentCounts, ResourceLocation requirement) {
        if (BuiltInRegistries.BLOCK.containsKey(requirement)) {
            return presentCounts.getOrDefault(requirement, 0);
        }
        TagKey<Block> blockTag = TagKey.create(Registries.BLOCK, requirement);
        int total = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : presentCounts.entrySet()) {
            ResourceLocation blockId = entry.getKey();
            if (!BuiltInRegistries.BLOCK.containsKey(blockId)) continue;
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (!block.defaultBlockState().is(blockTag)) continue;
            total += entry.getValue();
        }
        return total;
    }

    private static TierRef parseTierRef(String typeId) {
        if (typeId == null) return null;
        int idx = typeId.lastIndexOf("_l");
        if (idx <= 0 || idx >= typeId.length() - 2) return null;
        String suffix = typeId.substring(idx + 2);
        if (!suffix.chars().allMatch(Character::isDigit)) return null;
        return new TierRef(typeId.substring(0, idx), Integer.parseInt(suffix));
    }

    private record TierRef(String prefix, int tier) {}
}
