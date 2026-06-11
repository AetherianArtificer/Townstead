package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.origin.gene.types.EdibleGeneType;
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
        for (Gene gene : Heredity.expressedGenes(PlayerOrigin.getGenotype(player))) {
            if (!(gene.instance() instanceof EdibleGeneType.Instance instance)) continue;
            if (!instance.items().contains(itemId)) continue;
            player.getFoodData().eat(instance.nutrition(), instance.saturation());
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (instance.onEat() != null) instance.onEat().run(new ActionContext(player));
            player.swing(hand);
            return true;
        }
        return false;
    }
}
