package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.types.EdibleGeneType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side handler for {@code edible} genes: when a player right-clicks a held
 * item their origin can eat (and they have room to eat), restores food and runs the
 * gene's optional action. Returns whether the item was eaten so the interact event
 * can be consumed.
 */
public final class Edibles {

    private Edibles() {}

    public static boolean tryEat(Player player, ItemStack stack, InteractionHand hand) {
        if (stack.isEmpty() || !player.canEat(false)) return false;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (var gene : com.aetherianartificer.townstead.pheno.power.Powers.active(player)) {
            if (!(gene.component() instanceof EdibleGeneType.Instance instance)) continue;
            if (!instance.items().contains(itemId)) continue;
            player.getFoodData().eat(instance.nutrition(), instance.saturation());
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (instance.onEat() != null) instance.onEat().run(new ActionContext(player));
            player.swing(hand);
            return true;
        }
        return tryEatDietFood(player, stack, hand);
    }

    /** A diet food with authored values (a gem, a blood bottle) that vanilla would not let a player eat. */
    private static boolean tryEatDietFood(Player player, ItemStack stack, InteractionHand hand) {
        var nourishment = com.aetherianartificer.townstead.hunger.diet.Diets.nourishment(player, stack);
        if (nourishment == null || nourishment.nativeValues()) return false;
        player.getFoodData().eat(nourishment.nutrition(), nourishment.saturation());
        if (nourishment.food().effects() != null) nourishment.food().effects().run(new ActionContext(player));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            ItemStack left = com.aetherianartificer.townstead.hunger.diet.Diets.remainder(nourishment, stack);
            if (!left.isEmpty() && !player.getInventory().add(left)) player.drop(left, false);
        }
        player.swing(hand);
        return true;
    }
}
