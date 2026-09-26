package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.inventory.AssignedArmor;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.tasks.EquipmentTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/** Guards and the Armor command keep managing only armor the player has not assigned. */
@Mixin(EquipmentTask.class)
public abstract class EquipmentTaskAssignedArmorMixin {
    @Shadow(remap = false) @Final private Predicate<VillagerEntityMCA> condition;
    @Unique private int townstead$armorRevision;

    // Both selecting combat armor and clearing it after duty must honor the same ownership rule.
    //? if neoforge {
    @Redirect(method = {"start", "equipBestArmor"}, remap = false, at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/entity/VillagerEntityMCA;setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"), require = 1)
    //?} else {
    /*@Redirect(method = {"start", "equipBestArmor"}, remap = false, at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/entity/VillagerEntityMCA;m_8061_(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"), require = 1)
    *///?}
    private void townstead$keepAssignedArmor(VillagerEntityMCA villager, EquipmentSlot slot, ItemStack stack) {
        if (!AssignedArmor.protects(villager, slot)) villager.setItemSlot(slot, stack);
    }

    @Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/conczin/mca/entity/VillagerEntityMCA;)Z",
            remap = false, at = @At("RETURN"), cancellable = true)
    private void townstead$refreshAfterManualChange(ServerLevel level, VillagerEntityMCA villager,
                                                    CallbackInfoReturnable<Boolean> cir) {
        int revision = AssignedArmor.revision(villager);
        if (revision == townstead$armorRevision) return;
        townstead$armorRevision = revision;
        // An active guard can refill an unassigned slot without waiting for the next duty change.
        if (villager.getVillagerBrain().getArmorWear() || condition.test(villager)) cir.setReturnValue(true);
    }
}
