package com.aetherianartificer.townstead.mixin.compat.vampirism;

import com.aetherianartificer.townstead.compat.vampirism.VampireVillagers;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** A vampire-killer bolt kills a vampire villager as it does Vampirism's own vampires. */
@Pseudo
@Mixin(targets = "de.teamlapen.vampirism.items.CrossbowArrowItem", remap = false)
public abstract class CrossbowArrowVampireVillagerMixin {
    @ModifyExpressionValue(method = "onHitEntity", require = 0,
            at = @At(value = "CONSTANT", args = "classValue=de/teamlapen/vampirism/api/entity/vampire/IVampireMob"))
    private boolean townstead$vampireVillager(boolean isVampireMob,
                                              @Local(argsOnly = true, ordinal = 0) LivingEntity entity) {
        return isVampireMob || VampireVillagers.isVampire(entity);
    }
}
