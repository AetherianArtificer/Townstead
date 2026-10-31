package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.hunger.VillagerConsumptionManager;
import com.aetherianartificer.townstead.villager.WornItems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCA builds that lend inventory items as equipment override setItemSlot to file a displaced item
 * into the villager's inventory, and empty every slot after loading. Older builds do neither, so
 * every injector here is optional.
 */
@Mixin(value = VillagerEntityMCA.class, remap = false)
public abstract class VillagerEquipmentFilingMixin {
    // A save holds only real worn items (never props or lent items), so there is nothing to rebuild:
    // player-assigned armor, dressed clothing and starting gear stay worn.
    //? if neoforge {
    @WrapOperation(method = "readAdditionalSaveData", at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/entity/VillagerEntityMCA;clearEquipment()V"), require = 0)
    //?} else {
    /*@WrapOperation(method = "m_7378_", at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/entity/VillagerEntityMCA;clearEquipment()V"), require = 0)
    *///?}
    private void townstead$keepWornEquipment(VillagerEntityMCA self, Operation<Void> original) {
    }

    // WornItems hands the displaced item to its caller, so MCA must not file it as well.
    //? if neoforge {
    @WrapOperation(method = "setItemSlot", at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/util/InventoryUtils;isTemporary(Lnet/minecraft/world/item/ItemStack;)Z"), require = 0)
    //?} else {
    /*@WrapOperation(method = "m_8061_", at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/util/InventoryUtils;isTemporary(Lnet/minecraft/world/item/ItemStack;)Z"), require = 0)
    *///?}
    private boolean townstead$holdFiling(ItemStack previous, Operation<Boolean> original) {
        return WornItems.holdingFiling() || original.call(previous);
    }

    // The Mob-level guard only stops the vanilla half; MCA's own bookkeeping runs before it.
    //? if neoforge {
    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true, require = 0)
    //?} else {
    /*@Inject(method = "m_8061_", at = @At("HEAD"), cancellable = true, require = 0)
    *///?}
    private void townstead$reserveServingHand(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (self.level().isClientSide() || slot != EquipmentSlot.MAINHAND) return;
        if (!VillagerConsumptionManager.permitsMainHandChange(self, stack)) ci.cancel();
    }
}
