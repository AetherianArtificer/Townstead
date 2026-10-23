package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.item.LearnedRecipes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A crafting table or inventory grid makes a learned-recipe item only for a player who has the
 * recipe in their book, the same check vanilla's limited crafting makes for every recipe.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingLearnedRecipeMixin {

    //? if neoforge {
    @WrapOperation(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/ResultContainer;setRecipeUsed(Lnet/minecraft/world/level/Level;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/RecipeHolder;)Z"))
    private static boolean townstead$learned(ResultContainer result, Level level, ServerPlayer player,
                                             net.minecraft.world.item.crafting.RecipeHolder<?> recipe, Operation<Boolean> original) {
        return LearnedRecipes.allowed(level, player, recipe) && original.call(result, level, player, recipe);
    }
    //?} else {
    /*@WrapOperation(method = "m_150546_", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/inventory/ResultContainer;m_40135_(Lnet/minecraft/world/level/Level;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/crafting/Recipe;)Z"))
    private static boolean townstead$learned(ResultContainer result, Level level, ServerPlayer player,
                                             net.minecraft.world.item.crafting.Recipe<?> recipe, Operation<Boolean> original) {
        return LearnedRecipes.allowed(level, player, recipe) && original.call(result, level, player, recipe);
    }
    *///?}
}
