package com.aetherianartificer.townstead.temperature;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static com.aetherianartificer.townstead.temperature.ThermalConductance.Material;

/** Tag-driven wall materials shared by all room temperature backends. */
public final class ThermalMaterials {
    private static final TagKey<Block> WOOD = tag("wood_walls"), EARTH = tag("earth_walls"),
            GLASS = tag("glass_walls"), METAL = tag("metal_walls");
    private static final Map<BlockState, Integer> SIGNATURES = new ConcurrentHashMap<>();
    private static TemperatureSettings tableSettings;
    private static ThermalWall.Table table;
    private ThermalMaterials() {}
    private static TagKey<Block> tag(String path) {
        //? if >=1.21 {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("townstead", "thermal/" + path));
        //?} else {
        /*return TagKey.create(Registries.BLOCK, new ResourceLocation("townstead", "thermal/" + path));
        *///?}
    }
    /** Tags and sound types are read once per state: a mod's sound hook can be far slower than the rest of a wall read. */
    public static int signature(BlockState state) {
        Integer known = SIGNATURES.get(state);
        if (known != null) return known;
        int wall = classify(state);
        SIGNATURES.put(state, wall);
        return wall;
    }
    private static int classify(BlockState state) {
        Block block = state.getBlock();
        int shape = block instanceof DoorBlock || block instanceof TrapDoorBlock ? ThermalWall.DOOR
                : block instanceof FenceGateBlock ? ThermalWall.GATE
                : block instanceof IronBarsBlock ? ThermalWall.BARS
                : state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE ? ThermalWall.SLAB
                : block instanceof StairBlock ? ThermalWall.STAIR : ThermalWall.FULL;
        boolean hasOpen = state.hasProperty(BlockStateProperties.OPEN);
        return ThermalWall.pack(material(state), shape, hasOpen, hasOpen && state.getValue(BlockStateProperties.OPEN));
    }
    private static Material material(BlockState state) {
        return state.is(ThermalBlocks.LEAKY_BLOCKS) ? Material.POROUS
                : state.is(ThermalBlocks.INSULATING_BLOCKS) ? Material.INSULATION
                : state.is(METAL) ? Material.METAL : state.is(GLASS) ? Material.GLASS
                : state.is(WOOD) ? Material.WOOD : state.is(EARTH) ? Material.EARTH
                // Sound is an explicit block property used by many furniture mods; tags always win.
                : state.getSoundType() == SoundType.WOOD || state.getSoundType() == SoundType.BAMBOO_WOOD
                    ? Material.WOOD : Material.MASONRY;
    }
    public static ThermalWall.Table table(TemperatureSettings settings) {
        if (tableSettings != settings) {
            table = new ThermalWall.Table(settings.roomWallConductance(), settings.roomInsulatedConductance(),
                    settings.roomOpeningConductance(), settings.roomHeatCapacity(), settings::materialHeatCapacity);
            tableSettings = settings;
        }
        return table;
    }
    public static void clearCache() { SIGNATURES.clear(); }
}
