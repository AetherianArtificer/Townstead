package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.vampirism.VampireVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BreedableRelationship;
import net.conczin.mca.entity.ai.Memories;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * A golden apple gifted to a weakened vampire villager is taken as Vampirism's cure rather than an
 * ordinary gift. The villager lives on MCA's {@code Relationship} superclass, so it is read by
 * reflection instead of an inherited {@code @Shadow}. MCA's method, hence {@code remap=false}.
 */
@Mixin(value = BreedableRelationship.class, remap = false)
public abstract class VampireCureGiftMixin {
    @Unique
    private static volatile Field townstead$entity;

    @Inject(method = "giveGift", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$vampireCure(ServerPlayer player, Memories memory, CallbackInfo ci) {
        try {
            Field field = townstead$entity;
            if (field == null) {
                field = net.conczin.mca.entity.ai.Relationship.class.getDeclaredField("entity");
                field.setAccessible(true);
                townstead$entity = field;
            }
            if (field.get(this) instanceof VillagerEntityMCA villager && VampireVillagers.tryStartCure(villager, player)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // An MCA without this shape keeps its own gift handling.
        }
    }
}
