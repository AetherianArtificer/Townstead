package com.aetherianartificer.townstead.compat.farming;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Block;
//? if neoforge {
import net.neoforged.neoforge.common.world.poi.ExtendPoiTypesEvent;
//?}

import java.util.Optional;

/**
 * TFC disables the vanilla composter recipe, so its own composter joins the farmer's job-site
 * POI; otherwise no villager in a TFC world could take up farming.
 */
public final class TfcComposterPoiCompat {
    private TfcComposterPoiCompat() {}

    private static Optional<Block> composter() {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse("tfc:composter"));
    }

    //? if neoforge {
    public static void extend(ExtendPoiTypesEvent event) {
        composter().ifPresent(block -> event.addBlockToPoi(PoiTypes.FARMER, block));
    }
    //?}

    //? if forge {
    /*public static void extendForge() {
        net.minecraft.world.entity.ai.village.poi.PoiType farmer =
                net.minecraftforge.registries.ForgeRegistries.POI_TYPES.getValue(PoiTypes.FARMER.location());
        if (farmer == null) return;
        java.util.Map<net.minecraft.world.level.block.state.BlockState,
                net.minecraft.world.entity.ai.village.poi.PoiType> map =
                net.minecraftforge.registries.GameData.getBlockStatePointOfInterestTypeMap();
        composter().ifPresent(block -> block.getStateDefinition().getPossibleStates()
                .forEach(state -> map.put(state, farmer)));
    }
    *///?}
}
