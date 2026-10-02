package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.rebirth.Rebirth;
import com.aetherianartificer.townstead.root.RootDiscovery;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A villager's memories of each player. Every hearts change passes through {@code updateMemories},
 * which is where befriending a villager discovers its Root; every lookup passes through
 * {@code getMemoriesForPlayer}, which is where a villager first meets a reborn player as a stranger.
 */
@Mixin(VillagerBrain.class)
public abstract class VillagerBrainMemoriesMixin {
    @Shadow(remap = false) @Final private Mob entity;

    @Inject(method = "updateMemories", remap = false, at = @At("TAIL"))
    private void townstead$discoverRoot(Memories memories, CallbackInfo ci) {
        if (entity.level().isClientSide()) return;
        try {
            RootDiscovery.onHearts(entity, memories.getPlayerUUID(), memories.getHearts());
        } catch (RuntimeException e) {
            com.aetherianartificer.townstead.Townstead.LOGGER.debug("[Roots] Discovery check failed", e);
        }
    }

    @Inject(method = "getMemoriesForPlayer", remap = false, at = @At("RETURN"))
    private void townstead$forgetPastLife(Player player, CallbackInfoReturnable<Memories> cir) {
        if (entity.level().isClientSide() || cir.getReturnValue() == null) return;
        try {
            Rebirth.onMemoriesLookup(entity, player, cir.getReturnValue());
        } catch (RuntimeException e) {
            com.aetherianartificer.townstead.Townstead.LOGGER.debug("[Rebirth] Memory reset failed", e);
        }
    }
}
