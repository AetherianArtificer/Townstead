package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.LifeStageProgression;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Residency;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Defensive age gate for player-assigned employment. MCA's {@link Residency#setWorkplace} has no age
 * check of its own; it relies on the villager refusing interaction while a vanilla {@code isBaby()}.
 * {@code syncMcaAgeToStage} keeps that breeding age in step with the Townstead stage, but this also
 * refuses a pre-adult by the resolved STAGE directly, so a child can never be put to work through the
 * brief window before a freshly loaded breeding age is re-synced.
 *
 * <p>{@code remap=false}: the target lives in MCA, stable across both stonecutter branches.</p>
 */
@Mixin(value = Residency.class, remap = false)
public abstract class ResidencyWorkAgeGateMixin {

    @Shadow @Final private VillagerEntityMCA entity;

    @Inject(method = "setWorkplace", at = @At("HEAD"), cancellable = true)
    private void townstead$blockPreAdultWork(ServerPlayer player, CallbackInfo ci) {
        if (LifeStageProgression.isPreAdult(entity)) {
            entity.sendChatMessage(player, "interaction.setworkplace.failed");
            ci.cancel();
        }
    }
}
