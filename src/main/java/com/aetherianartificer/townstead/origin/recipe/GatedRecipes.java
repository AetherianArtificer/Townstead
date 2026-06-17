package com.aetherianartificer.townstead.origin.recipe;

import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.origin.gene.types.RecipeGeneType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Gating for {@code recipe} genes. {@link #isGated} reports whether any gene restricts a
 * crafting output; {@link #canCraft} whether a given player is allowed it. The crafting
 * result-slot mixin uses both to block taking a gated result from a player who lacks the
 * granting gene.
 */
public final class GatedRecipes {

    private GatedRecipes() {}

    public static boolean isGated(Item result) {
        for (Gene gene : GeneRegistry.all()) {
            if (gene.instance() instanceof RecipeGeneType.Instance recipe && recipe.result() == result) return true;
        }
        return false;
    }

    public static boolean canCraft(Player player, Item result) {
        for (RecipeGeneType.Instance recipe : ExpressedGenes.instancesOf(player, RecipeGeneType.Instance.class)) {
            if (recipe.result() == result) return true;
        }
        return false;
    }
}
