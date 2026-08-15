package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.LifeStageProgression;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.item.RelationshipItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Defensive age gate for relationship items (wedding/engagement ring). MCA rejects a marriage only
 * when the villager is a vanilla {@code isBaby()} (breeding age &lt; 0); {@code syncMcaAgeToStage}
 * keeps that in step with the Townstead stage, but this also refuses a pre-adult by the resolved
 * STAGE directly, so a villager can never be married through the brief window before a freshly
 * loaded breeding age is re-synced.
 *
 * <p>MCA renamed the check {@code handle} &rarr; {@code validate} and changed its return from
 * {@code boolean} to {@link InteractionResult}. Both are hooked at {@code require = 0} so whichever
 * one this MCA build declares is the one that binds. "Handled, with a failure message" is
 * {@code true} on the old shape and {@link InteractionResult#FAIL} on the new one; either way the
 * caller (e.g. {@code WeddingRingItem}) then does not marry.</p>
 */
@Mixin(value = RelationshipItem.class, remap = false)
public abstract class RelationshipItemAgeGateMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$blockPreAdultMarriageLegacy(ServerPlayer player, VillagerEntityMCA villager,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (townstead$refuse(player, villager)) cir.setReturnValue(true);
    }

    @Inject(method = "validate", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$blockPreAdultMarriage(ServerPlayer player, VillagerEntityMCA villager,
                                                 CallbackInfoReturnable<InteractionResult> cir) {
        if (townstead$refuse(player, villager)) cir.setReturnValue(InteractionResult.FAIL);
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean townstead$refuse(ServerPlayer player, VillagerEntityMCA villager) {
        if (!LifeStageProgression.isPreAdult(villager)) return false;
        villager.sendChatMessage(player, "interaction.relationship.fail.isbaby");
        return true;
    }
}
