package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.hook.PhenoHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pheno interception points on {@link FoodData} ({@code food} modifier, holder-side). FoodData has
 * no back-reference to its player, so we cache it from {@code tick(Player)} (the way Apoli's
 * HungerManagerMixin caches from update) and rescale the nutrition argument to
 * {@code eat(int, float)} ({@code m_38707_} on 1.20.1). Players only; MCA villagers eat through
 * Townstead's {@code VillagerConsumptionManager}, which applies the same modifier directly. All
 * logic is in {@link PhenoHooks}.
 */
@Mixin(FoodData.class)
public abstract class FoodDataPhenoMixin {

    @Unique private Player townstead$eater;

    //? if neoforge {
    @Inject(method = "tick", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_38710_", at = @At("HEAD"), remap = false)
    *///?}
    private void townstead$cacheEater(Player player, CallbackInfo ci) {
        townstead$eater = player;
    }

    //? if neoforge {
    @ModifyVariable(method = "eat(IF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    //?} else {
    /*@ModifyVariable(method = "m_38707_(IF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    *///?}
    private int townstead$scaleNutrition(int nutrition) {
        if (townstead$eater == null) return nutrition;
        return PhenoHooks.food(townstead$eater, nutrition);
    }
}
