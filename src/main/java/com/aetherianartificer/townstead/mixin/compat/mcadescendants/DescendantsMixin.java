package com.aetherianartificer.townstead.mixin.compat.mcadescendants;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.mcadescendants.DescendantsBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Everything Townstead needs from MCA Descendants, applied only when it is present:
 *
 * <ul>
 *   <li>Its two swaps, so Townstead's parts come along: at death the old self becomes the soul
 *   (kept as a past life), and when a descendant is chosen the player takes on that villager's
 *   Root and name.</li>
 *   <li>Its death and respawn handlers, whose "hardcore only" check passes while Townstead runs
 *   them for a player who chose "Continue as a descendant".</li>
 * </ul>
 *
 * <p>Each injector lands only on the class that has its method; {@code require = 0} keeps a
 * Descendants update that moves something from failing the game.</p>
 */
@Pseudo
@Mixin(targets = {
        "net.dannyfather.mca_descendants.util.ModUtils",
        "net.dannyfather.mca_descendants.events.MCADescendantsEvents"
}, remap = false)
public abstract class DescendantsMixin {

    @Inject(method = "evilSwapVillagerAndPlayer", at = @At("HEAD"), require = 0)
    private static void townstead$beforeDeathSwap(LivingEntity soul, ServerPlayer player, DamageSource source, CallbackInfo ci) {
        try {
            DescendantsBridge.beforeDeathSwap(player);
        } catch (RuntimeException e) {
            Townstead.LOGGER.warn("[Rebirth] MCA Descendants death swap: could not read the old life", e);
        }
    }

    @Inject(method = "evilSwapVillagerAndPlayer", at = @At("RETURN"), require = 0)
    private static void townstead$afterDeathSwap(LivingEntity soul, ServerPlayer player, DamageSource source, CallbackInfo ci) {
        try {
            DescendantsBridge.afterDeathSwap(soul, player);
        } catch (RuntimeException e) {
            Townstead.LOGGER.warn("[Rebirth] MCA Descendants death swap: could not record the old life", e);
        }
    }

    @Inject(method = "goodSwapVillagerAndPlayer", at = @At("HEAD"), require = 0)
    private static void townstead$beforeChosenSwap(LivingEntity target, ServerPlayer player, CallbackInfo ci) {
        try {
            DescendantsBridge.beforeChosenSwap(target, player);
        } catch (RuntimeException e) {
            Townstead.LOGGER.warn("[Rebirth] MCA Descendants swap: could not read the descendant", e);
        }
    }

    @Inject(method = "goodSwapVillagerAndPlayer", at = @At("RETURN"), require = 0)
    private static void townstead$afterChosenSwap(LivingEntity target, ServerPlayer player, CallbackInfo ci) {
        try {
            DescendantsBridge.afterChosenSwap(player);
        } catch (RuntimeException e) {
            Townstead.LOGGER.warn("[Rebirth] MCA Descendants swap: could not carry the Root and name", e);
        }
    }

    //? if neoforge {
    @Redirect(method = {"onLivingDeath", "onPlayerRespawn"}, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/ModConfigSpec$ConfigValue;get()Ljava/lang/Object;"))
    private static Object townstead$handOffGate(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> value) {
        return DescendantsBridge.gateValue(value, value.get());
    }
    //?} else {
    /*@Redirect(method = {"onLivingDeath", "onPlayerRespawn"}, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/common/ForgeConfigSpec$ConfigValue;get()Ljava/lang/Object;"))
    private static Object townstead$handOffGate(net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?> value) {
        return DescendantsBridge.gateValue(value, value.get());
    }
    *///?}
}
