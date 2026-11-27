package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.rest.RestSpots;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiPredicate;

/**
 * MCA claims a home from the nearest free home point of interest. A rest spot that does not admit
 * the villager (a coffin for a living one), or a plain bed for someone bound to coffins, counts as
 * taken for them, so they look elsewhere, as does any home ranked below a free one nearby (rest
 * spot {@code prefer} and {@code pheno:rest} preferences). Only home points are affected, never job sites.
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.entity.ai.brain.tasks.ExtendedFindPointOfInterestTask", remap = false)
public abstract class RestSpotHomeClaimMixin {
    @Shadow @Final @Mutable private BiPredicate<VillagerEntityMCA, BlockPos> predicate;

    @Inject(method = "<init>(Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;ZLjava/util/Optional;Ljava/util/function/Consumer;Ljava/util/function/BiPredicate;)V",
            at = @At("RETURN"))
    private void townstead$restSpotRules(CallbackInfo ci) {
        BiPredicate<VillagerEntityMCA, BlockPos> original = predicate;
        predicate = (villager, pos) -> original.test(villager, pos) && townstead$admits(villager, pos);
    }

    @Unique
    private static boolean townstead$admits(VillagerEntityMCA entity, BlockPos pos) {
        if (RestSpots.all().isEmpty() || !(entity.level() instanceof ServerLevel level)) return true;
        if (!level.getPoiManager().existsAtPosition(PoiTypes.HOME, pos)) return true;
        if (!RestSpots.permits(entity, level.getBlockState(pos))) return false;
        // A lesser home (a sleeping bag, or a bed for someone who seeks out coffins) is passed over while a better one is free nearby.
        int rank = RestSpots.rank(entity, level.getBlockState(pos));
        return rank >= RestSpots.bestRank(entity)
                || level.getPoiManager().findAll(type -> type.is(PoiTypes.HOME), other -> {
                    var state = level.getBlockState(other);
                    return RestSpots.rank(entity, state) > rank && RestSpots.permits(entity, state);
                }, entity.blockPosition(), 48,
                        net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE).findAny().isEmpty();
    }
}
