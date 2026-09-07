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
    public static double conductance(BlockState state, TemperatureSettings settings) {
        Material material = state.is(ThermalBlocks.LEAKY_BLOCKS) ? Material.POROUS
                : state.is(ThermalBlocks.INSULATING_BLOCKS) ? Material.INSULATION
                : state.is(METAL) ? Material.METAL : state.is(GLASS) ? Material.GLASS
                : state.is(WOOD) ? Material.WOOD : state.is(EARTH) ? Material.EARTH : Material.MASONRY;
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
