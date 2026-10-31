package com.aetherianartificer.townstead.climate;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.server.level.ServerLevel;
//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
//?} else if forge {
/*import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*///?}

/**
 * Crops stall outside their climate class's range. A stalled crop skips its growth tick; it does
 * not die. Bone meal is untouched, and season mods keep gating their own growth.
 */
//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID)
//?} else if forge {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID)
*///?}
public final class CropClimateEvents {
    private CropClimateEvents() {}

    //? if neoforge {
    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (CropClimate.temperatureVerdict(level, event.getPos(), event.getState(), 0) != CropClimate.Verdict.GROWS)
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
    }
    //?} else if forge {
    /*@SubscribeEvent
    public static void onCropGrow(BlockEvent.CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (CropClimate.temperatureVerdict(level, event.getPos(), event.getState(), 0) != CropClimate.Verdict.GROWS)
            event.setResult(Event.Result.DENY);
    }
    *///?}
}
