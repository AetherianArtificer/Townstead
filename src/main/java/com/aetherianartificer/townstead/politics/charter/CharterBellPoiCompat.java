package com.aetherianartificer.townstead.politics.charter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BellBlock;
//? if neoforge {
import net.neoforged.neoforge.common.world.poi.ExtendPoiTypesEvent;
//?}

/** Makes optional material bells real meeting points rather than merely bell-shaped blocks. */
public final class CharterBellPoiCompat {
    private CharterBellPoiCompat() {}

    //? if neoforge {
    public static void extend(ExtendPoiTypesEvent event) {
        BuiltInRegistries.BLOCK.entrySet().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals("bellsbellsbells"))
                .map(java.util.Map.Entry::getValue)
                .filter(BellBlock.class::isInstance)
                .forEach(block -> event.addBlockToPoi(PoiTypes.MEETING, block));
    }
    //?}

    //? if forge {
    /*public static void extendForge() {
        net.minecraft.resources.ResourceLocation meetingId = new net.minecraft.resources.ResourceLocation("minecraft", "meeting");
        net.minecraft.world.entity.ai.village.poi.PoiType meeting =
                net.minecraftforge.registries.ForgeRegistries.POI_TYPES.getValue(meetingId);
        if (meeting == null) return;
        java.util.Map<net.minecraft.world.level.block.state.BlockState,
                net.minecraft.world.entity.ai.village.poi.PoiType> map =
                net.minecraftforge.registries.GameData.getBlockStatePointOfInterestTypeMap();
        BuiltInRegistries.BLOCK.entrySet().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals("bellsbellsbells"))
                .map(java.util.Map.Entry::getValue)
                .filter(BellBlock.class::isInstance)
                .flatMap(block -> block.getStateDefinition().getPossibleStates().stream())
                .forEach(state -> map.put(state, meeting));
    }
    *///?}
}
