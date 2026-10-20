package com.aetherianartificer.townstead.culture;

import com.mojang.serialization.Codec;
//? if >=1.21 {
import com.mojang.serialization.MapCodec;
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Dresses a culture village's buildings and streets in the palettes that suit it. The swaps are
 * chosen once per village, when it starts placing, and handed to this processor for the rest of
 * that placement; outside one it changes nothing. Blocks villagers work or sleep at keep their
 * block, and swapped blocks keep their facing, half and other shared properties.
 */
public final class VillagePaletteProcessor extends StructureProcessor {
    public static final VillagePaletteProcessor INSTANCE = new VillagePaletteProcessor();
    //? if >=1.21 {
    public static final MapCodec<VillagePaletteProcessor> CODEC = MapCodec.unit(INSTANCE);
    //?} else {
    /*public static final Codec<VillagePaletteProcessor> CODEC = Codec.unit(INSTANCE);
    *///?}
    private static final ThreadLocal<Map<Block, Block>> SWAPS = new ThreadLocal<>();

    private VillagePaletteProcessor() {}

    /** Swaps for the village now placing on this thread. */
    public static void begin(Map<Block, Block> swaps) {
        if (swaps.isEmpty()) SWAPS.remove();
        else SWAPS.set(swaps);
    }

    public static void end() {
        SWAPS.remove();
    }

    /** The block the village now placing on this thread uses in place of {@code state}. */
    public static BlockState dress(BlockState state) {
        Map<Block, Block> swaps = SWAPS.get();
        if (swaps == null) return state;
        Block target = swaps.get(state.getBlock());
        if (target == null || PoiTypes.forState(state).isPresent()) return state;
        BlockState swapped = target.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (swapped.hasProperty(property)) swapped = copy(state, swapped, property);
        }
        return swapped;
    }

    @Override
    public @Nullable StructureTemplate.StructureBlockInfo processBlock(
            LevelReader level, BlockPos offset, BlockPos pivot, StructureTemplate.StructureBlockInfo original,
            @Nullable StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
        if (current == null) return null;
        Map<Block, Block> swaps = SWAPS.get();
        if (swaps == null) return current;
        BlockState state = current.state();
        Block target = swaps.get(state.getBlock());
        if (target == null || PoiTypes.forState(state).isPresent()) return current;
        BlockState swapped = target.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (swapped.hasProperty(property)) swapped = copy(state, swapped, property);
        }
        return new StructureTemplate.StructureBlockInfo(current.pos(), swapped,
                swapped.hasBlockEntity() ? current.nbt() : null);
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return VillageProcessors.PALETTE.get();
    }
}
