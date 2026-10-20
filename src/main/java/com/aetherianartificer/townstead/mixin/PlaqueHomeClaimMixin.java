package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.storage.RoomOwnershipAccess;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedFindPointOfInterestTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiPredicate;

/**
 * MCA's home search skips beds in a home whose Ownership Plaque does not list the villager, so
 * nobody is given a bed they may not sleep in.
 */
@Mixin(value = ExtendedFindPointOfInterestTask.class, remap = false)
public abstract class PlaqueHomeClaimMixin {
    @Shadow @Final @Mutable private BiPredicate<VillagerEntityMCA, BlockPos> predicate;
    @Shadow @Final private MemoryModuleType<GlobalPos> targetMemoryModuleType;

    @Inject(method = "<init>(Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;ZLjava/util/Optional;Ljava/util/function/Consumer;Ljava/util/function/BiPredicate;)V",
            at = @At("RETURN"))
    private void townstead$respectPlaques(CallbackInfo ci) {
        if (targetMemoryModuleType != MemoryModuleType.HOME) return;
        BiPredicate<VillagerEntityMCA, BlockPos> original = predicate;
        predicate = (villager, pos) -> original.test(villager, pos)
                && (!(villager.level() instanceof ServerLevel level)
                || RoomOwnershipAccess.mayClaimHome(level, villager, pos));
    }
}
