package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
//?} else if forge {
/*import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*///?}

/**
 * Player-side fishing attribution. The fisherman engine credits a villager for every reel that
 * lands something; this hook gives a player the same XP and Chronicle activity for the same
 * catch, so a Fisherman career advances whether the rod is held by a villager or the player.
 */
//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID)
//?} else if forge {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID)
*///?}
public final class PlayerFishingEvents {
    /** XP for one reel that lands something. Shared with the villager engine. */
    public static final int XP_CATCH = 3;

    private PlayerFishingEvents() {}

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (event.getDrops().isEmpty()) return;
        CareerProgression.completeWork(player, Careers.FISHERMAN, XP_CATCH, level.getGameTime(),
                "townstead:fished", null, null, XP_CATCH);
    }
}
