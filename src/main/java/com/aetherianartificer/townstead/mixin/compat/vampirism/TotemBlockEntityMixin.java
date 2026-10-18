package com.aetherianartificer.townstead.mixin.compat.vampirism;

import com.aetherianartificer.townstead.work.martial.VillageAlarm;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Vampirism village captures. When vampires raid a village, Vampirism replaces some villagers with
 * its own pitchfork villager and later with a vanilla one, copying only their NBT; an MCA villager
 * would lose its identity, so it stays as it is and defends through Townstead's call to arms, which
 * hears of the capture every second it lasts. The village area is read by reflection with no
 * {@code @Shadow}, so a reshaped class stays inert.
 */
@Pseudo
@Mixin(targets = "de.teamlapen.vampirism.blockentity.TotemBlockEntity", remap = false)
public abstract class TotemBlockEntityMixin {
    @Unique
    private static volatile Method townstead$villageArea;

    @Inject(method = "makeAgressive(Lnet/minecraft/world/entity/npc/Villager;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void townstead$keepMcaVillager(Villager villager, CallbackInfo ci) {
        if (villager instanceof VillagerEntityMCA) ci.cancel();
    }

    @Inject(method = "serverTickSecondCapture", at = @At("HEAD"), require = 0)
    private void townstead$soundAlarm(int timeInSeconds, CallbackInfo ci) {
        if (!((Object) this instanceof BlockEntity totem) || !(totem.getLevel() instanceof ServerLevel level)) return;
        try {
            Method method = townstead$villageArea;
            if (method == null) {
                method = this.getClass().getMethod("getVillageArea");
                townstead$villageArea = method;
            }
            if (method.invoke(this) instanceof AABB area) VillageAlarm.siege(level, totem.getBlockPos(), area);
        } catch (Throwable ignored) {
        }
    }
}
