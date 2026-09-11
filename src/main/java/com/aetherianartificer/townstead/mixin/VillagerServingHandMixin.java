package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.hunger.VillagerConsumptionManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Trade-display and work behaviors share this equipment slot with genuine consumption. */
@Mixin(Mob.class)
public abstract class VillagerServingHandMixin {
    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void townstead$reserveServingHand(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if ((Object) this instanceof VillagerEntityMCA villager && !villager.level().isClientSide()
                && slot == EquipmentSlot.MAINHAND
                && !VillagerConsumptionManager.permitsMainHandChange(villager, stack)) ci.cancel();
    }
}
