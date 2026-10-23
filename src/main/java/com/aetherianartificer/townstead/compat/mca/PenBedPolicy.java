package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.recognition.SiteRequirements;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;

import java.util.List;
import java.util.Map;

/**
 * A room with a bed in it is somewhere people sleep, never an animal pen. MCA ranks a pen above a
 * house as soon as a room holds one fence, gate or hay bale, and a house's walls and door already
 * keep an animal in, so without this a bedroom with a fence-post table became a pen. Applies to
 * every type whose site is an animal enclosure.
 */
public final class PenBedPolicy {
    private PenBedPolicy() {}

    public static List<BuildingType> filter(List<BuildingType> candidates,
                                            Map<ResourceLocation, List<BlockPos>> blocks) {
        if (candidates == null || candidates.isEmpty() || blocks == null || !hasBed(blocks)) return candidates;
        if (candidates.stream().noneMatch(type -> SiteRequirements.isEnclosed(type.name()))) return candidates;
        return candidates.stream().filter(type -> !SiteRequirements.isEnclosed(type.name())).toList();
    }

    static boolean hasBed(Map<ResourceLocation, List<BlockPos>> blocks) {
        for (Map.Entry<ResourceLocation, List<BlockPos>> entry : blocks.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            if (BuiltInRegistries.BLOCK.get(entry.getKey()).defaultBlockState().is(BlockTags.BEDS)) return true;
        }
        return false;
    }
}
