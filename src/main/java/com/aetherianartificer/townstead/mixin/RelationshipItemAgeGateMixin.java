package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.LifeStageProgression;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.item.RelationshipItem;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Defensive age gate for relationship items (wedding/engagement ring). MCA's
 * {@link RelationshipItem#handle} rejects a marriage only when the villager is a vanilla
 * {@code isBaby()} (breeding age &lt; 0); {@code syncMcaAgeToStage} keeps that in step with the
 * Townstead stage, but this also refuses a pre-adult by the resolved STAGE directly, so a villager
 * can never be married through the brief window before a freshly loaded breeding age is re-synced.
 *
 * <p>{@code handle} returning {@code true} means "handled, with a failure message" (the caller, e.g.
 * {@code WeddingRingItem}, then does not marry), so we send the same "is baby" line MCA uses and
 * return true. {@code remap=false}: the target lives in MCA, stable across both stonecutter branches.</p>
 */
@Mixin(value = RelationshipItem.class, remap = false)
public abstract class RelationshipItemAgeGateMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void townstead$blockPreAdultMarriage(ServerPlayer player, VillagerEntityMCA villager,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (LifeStageProgression.isPreAdult(villager)) {
            villager.sendChatMessage(player, "interaction.relationship.fail.isbaby");
            cir.setReturnValue(true);
        }
    }
}
