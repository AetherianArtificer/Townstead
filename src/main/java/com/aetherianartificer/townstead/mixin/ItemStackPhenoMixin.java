package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.hook.PhenoHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercept for the {@code durability_loss} modifier target; neither loader has a durability
 * event. On 1.21.1 the entity overload hands off to the {@code (ServerLevel, ServerPlayer)} one
 * with the player, or null for anyone else, and mods such as Farmer's Delight call that one
 * directly: players are resolved there, other entities at the entity overload, so nothing is
 * applied twice. On 1.20.1 the entity overload is the single path. 1.20.1 SRG: {@code m_41622_}
 * hurtAndBreak.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackPhenoMixin {

    //? if neoforge {
    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int townstead$durabilityLoss(int amount, @Local(argsOnly = true) LivingEntity entity) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer) return amount;
        return PhenoHooks.durabilityLoss(entity, (ItemStack) (Object) this, amount);
    }

    @ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int townstead$playerDurabilityLoss(int amount,
            @Local(argsOnly = true) net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return amount;
        return PhenoHooks.durabilityLoss(player, (ItemStack) (Object) this, amount);
    }
    //?} else {
    /*@ModifyVariable(method = "m_41622_(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int townstead$durabilityLoss(int amount, @Local(argsOnly = true) LivingEntity entity) {
        return PhenoHooks.durabilityLoss(entity, (ItemStack) (Object) this, amount);
    }
    *///?}
}
