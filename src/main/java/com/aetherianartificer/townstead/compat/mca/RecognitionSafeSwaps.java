package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.recognition.OptionalBuildingRecognition;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps village palettes from changing what a building is. A swap stays only when, for every MCA and
 * Townstead building type, the new block counts exactly where the old one did: a spruce fence may
 * become a birch fence, since any fence marks a pen, but a spruce trapdoor that marks a square stays.
 * Blocks villagers work or sleep at never change.
 */
public final class RecognitionSafeSwaps {
    private RecognitionSafeSwaps() {}

    public static Map<Block, Block> filter(Map<Block, Block> swaps) {
        if (swaps.isEmpty()) return swaps;
        Collection<BuildingType> types = BuildingTypes.getInstance().getBuildingTypes().values();
        Map<Block, Block> kept = new LinkedHashMap<>();
        swaps.forEach((from, to) -> {
            if (safe(from.defaultBlockState(), to.defaultBlockState(), types)) kept.put(from, to);
        });
        return kept;
    }

    private static boolean safe(BlockState from, BlockState to, Collection<BuildingType> types) {
        if (PoiTypes.forState(from).isPresent() || PoiTypes.forState(to).isPresent()) return false;
        for (BuildingType type : types) {
            if (OptionalBuildingRecognition.matchesBlock(type, from) != OptionalBuildingRecognition.matchesBlock(type, to)) {
                return false;
            }
        }
        return true;
    }
}
