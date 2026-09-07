package com.aetherianartificer.townstead.objectset;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.server.level.ServerLevel;
//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
//?} else if forge {
/*import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*///?}

/** Game-bus hooks for set recognition: block placement, block breaking, and the definition reload. */
//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID)
//?} else if forge {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID)
*///?}
public final class ObjectSetEvents {
    private ObjectSetEvents() {}

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new ObjectSets.Loader());
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ObjectSetRecognizer.onPlaced(level, event.getPos(), event.getPlacedBlock(), event.getEntity());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ObjectSetRecognizer.onRemoved(level, event.getPos(), event.getState(), event.getPlayer());
    }
}
