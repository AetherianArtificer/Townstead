package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.storage.RoomOwnershipService;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Residency;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * MCA's "set home" picks the bed nearest the player. When that gives the villager a new home in a
 * place with an Ownership Plaque, the villager joins the plaque if the player may edit it, and is
 * sent back out if not.
 */
@Mixin(value = Residency.class, remap = false)
public abstract class PlaqueSetHomeMixin {
    @Shadow @Final private VillagerEntityMCA entity;
    @Unique private Optional<GlobalPos> townstead$homeBefore = Optional.empty();

    @Inject(method = "setHome(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
    private void townstead$rememberHome(ServerPlayer player, CallbackInfo ci) {
        townstead$homeBefore = entity.getBrain().getMemory(MemoryModuleType.HOME);
    }

    @Inject(method = "setHome(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
    private void townstead$applyPlaque(ServerPlayer player, CallbackInfo ci) {
        if (entity.getBrain().getMemory(MemoryModuleType.HOME).equals(townstead$homeBefore)) return;
        RoomOwnershipService.homeSetByPlayer(player, entity);
    }
}
