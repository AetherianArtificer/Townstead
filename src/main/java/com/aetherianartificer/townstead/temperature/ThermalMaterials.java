package com.aetherianartificer.townstead.temperature;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import static com.aetherianartificer.townstead.temperature.ThermalConductance.Material;

/** Tag-driven wall materials shared by all room temperature backends. */
public final class ThermalMaterials {
    private static final TagKey<Block> WOOD = tag("wood_walls"), EARTH = tag("earth_walls"),
            GLASS = tag("glass_walls"), METAL = tag("metal_walls");
    private ThermalMaterials() {}
    private static TagKey<Block> tag(String path) {
        //? if >=1.21 {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("townstead", "thermal/" + path));
        //?} else {
        /*return TagKey.create(Registries.BLOCK, new ResourceLocation("townstead", "thermal/" + path));
        *///?}
    }
    public static Material material(BlockState state) {
        return state.is(ThermalBlocks.LEAKY_BLOCKS) ? Material.POROUS
                : state.is(ThermalBlocks.INSULATING_BLOCKS) ? Material.INSULATION
                : state.is(METAL) ? Material.METAL : state.is(GLASS) ? Material.GLASS
                : state.is(WOOD) ? Material.WOOD : state.is(EARTH) ? Material.EARTH
                // Sound is an explicit block property used by many furniture mods; tags always win.
                : state.getSoundType() == SoundType.WOOD || state.getSoundType() == SoundType.BAMBOO_WOOD
                    ? Material.WOOD : Material.MASONRY;
    }
    public static double surfaceCapacity(BlockState state, TemperatureSettings settings) {
        double fraction = state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof IronBarsBlock ? 0.125
                : state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE ? 0.5
                : state.getBlock() instanceof StairBlock ? 0.75 : 1;
        // Legacy face units; the network uses six shares for one physical block node.
        return settings.materialHeatCapacity(material(state)) * fraction / 6;
    }
    public static boolean openAperture(BlockState state) {
        return (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock || material(state) == Material.GLASS)
                && state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
    }
    public static double conductance(BlockState state, TemperatureSettings settings) {
        Material material = material(state);
        boolean aperture = state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || material == Material.GLASS && state.hasProperty(BlockStateProperties.OPEN);
        boolean open = aperture && state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
        boolean thin = state.getBlock() instanceof StairBlock || state.getBlock() instanceof IronBarsBlock
                || state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE;
        return ThermalConductance.rate(material, settings.roomWallConductance(), settings.roomInsulatedConductance(),
                settings.roomOpeningConductance(), thin, aperture, open);
    }
}
