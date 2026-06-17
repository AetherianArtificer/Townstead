package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.NaturalRegen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the span of {@code FoodData.tick} so the heal listener can tell vanilla's
 * natural (food-driven) regen apart from any other heal and block it for a
 * {@code disable_regen} race. HEAD/RETURN injects only (refmap-safe on 1.20.1); the
 * single {@code tick(Player)} method has the same signature on both branches, so the
 * Forge variant just swaps the SRG name.
 */
@Mixin(net.minecraft.world.food.FoodData.class)
public abstract class FoodDataNaturalRegenMixin {

    //? if neoforge {
    @Inject(method = "tick", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_38710_", at = @At("HEAD"), remap = false)
    *///?}
    private void townstead$enterFoodTick(CallbackInfo ci) {
        NaturalRegen.enter();
    }

    //? if neoforge {
    @Inject(method = "tick", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "m_38710_", at = @At("RETURN"), remap = false)
    *///?}
    private void townstead$exitFoodTick(CallbackInfo ci) {
        NaturalRegen.exit();
    }
}
