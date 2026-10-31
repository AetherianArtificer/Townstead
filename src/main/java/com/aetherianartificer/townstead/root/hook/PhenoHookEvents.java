package com.aetherianartificer.townstead.root.hook;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.world.entity.LivingEntity;
//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
//?} else if forge {
/*import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*///?}

/**
 * Event-side intercepts for modifier targets whose mechanic fires an event on both loaders:
 * {@code anvil_break_chance} (the repair event, worked item as subject) and
 * {@code farmland_trample} (the trampling creature, so an aura around a bearer protects fields).
 */
//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID)
//?} else if forge {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID)
*///?}
public final class PhenoHookEvents {

    private PhenoHookEvents() {}

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        event.setBreakChance(PhenoHooks.anvilBreakChance(event.getEntity(), event.getLeft(),
                event.getBreakChance()));
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof LivingEntity living && !PhenoHooks.tramples(living)) {
            event.setCanceled(true);
        }
    }
}
