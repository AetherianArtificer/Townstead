package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.profession.JobRetention;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.villageComponents.VillageGuardsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drafts guards from the unemployed only. MCA picks any novice at random, so a working farmer
 * could become an archer overnight and leave a job block claimed with nobody at it.
 */
@Mixin(VillageGuardsManager.class)
public abstract class VillageGuardsManagerDraftMixin {

    @WrapOperation(method = "spawnGuards", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/entity/VillagerEntityMCA;isProfessionImportant()Z",
            remap = false))
    private boolean townstead$leaveTheEmployedAlone(VillagerEntityMCA villager,
                                                    Operation<Boolean> original) {
        return original.call(villager)
                || JobRetention.employed(villager.getVillagerData().getProfession());
    }
}
