package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.profession.career.CareerProgression;
import com.aetherianartificer.townstead.profession.career.Careers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Player-side cooking attribution. The villager work engines route their own completions; these
 * hooks give players the same credit for the same acts: taking a meal from a cooking pot,
 * setting food on a skillet, and pulling cooked food from a furnace-family block. All of it
 * funnels through {@link CareerProgression#completeWork}, so XP, chronicle taps, acquisition
 * sweeps, and level-up feedback behave identically to villager work.
 */
public final class PlayerCookingHooks {

    private PlayerCookingHooks() {}

    /** A meal leaves the cooking pot's serving slot in the taker's hands. */
    public static void onDishTaken(Player player, ItemStack stack, String station) {
        if (!(player instanceof ServerPlayer sp) || stack.isEmpty()) return;
        int count = Math.max(1, stack.getCount());
        CareerProgression.completeWork(sp, Careers.COOK, count, sp.serverLevel().getGameTime(),
                "townstead:cooked", BuiltInRegistries.ITEM.getKey(stack.getItem()),
                "dish", count,
                Map.of("station", station, "amount", Integer.toString(count)));
    }

    /** Food accepted onto a skillet; the cook is whoever set it sizzling. */
    public static void onSkilletAdd(Player player, ItemStack stack, int accepted) {
        if (!(player instanceof ServerPlayer sp) || accepted <= 0) return;
        CareerProgression.completeWork(sp, Careers.COOK, accepted, sp.serverLevel().getGameTime(),
                "townstead:cooked", BuiltInRegistries.ITEM.getKey(stack.getItem()),
                "dish", accepted,
                Map.of("station", "skillet", "amount", Integer.toString(accepted)));
    }

    /** Furnace family: only food results count as cooking. */
    public static void onSmelted(Player player, ItemStack stack) {
        boolean food;
        //? if >=1.21 {
        food = stack.has(net.minecraft.core.component.DataComponents.FOOD);
        //?} else {
        /*food = stack.getItem().isEdible();
        *///?}
        if (food) onDishTaken(player, stack, "furnace");
    }
}
