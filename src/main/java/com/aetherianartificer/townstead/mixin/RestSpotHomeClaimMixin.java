package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.rest.RestSpots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MCA claims a home from the nearest free home point of interest. A rest spot that does not admit
 * the villager (a coffin for a living one), or a plain bed for someone bound to coffins, counts as
 * taken for them, so they look elsewhere, as does any home ranked below a free one nearby (rest
 * spot {@code prefer} and {@code pheno:rest} preferences). Only home points are affected, never job sites.
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.entity.ai.brain.tasks.ExtendedFindPointOfInterestTask", remap = false)
public abstract class RestSpotHomeClaimMixin {
    @Inject(method = "isBedOccupiedByOthers", at = @At("RETURN"), cancellable = true, require = 0)
    private void townstead$restSpotRules(ServerLevel level, BlockPos pos, LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || RestSpots.all().isEmpty()) return;
        if (!level.getPoiManager().existsAtPosition(PoiTypes.HOME, pos)) return;
        if (!RestSpots.permits(entity, level.getBlockState(pos))) {
            cir.setReturnValue(true);
            return;
        }
        // A lesser home (a sleeping bag, or a bed for someone who seeks out coffins) is passed over while a better one is free nearby.
        int rank = RestSpots.rank(entity, level.getBlockState(pos));
        if (rank < RestSpots.bestRank(entity)
                && level.getPoiManager().findAll(type -> type.is(PoiTypes.HOME), other -> {
                    var state = level.getBlockState(other);
                    return RestSpots.rank(entity, state) > rank && RestSpots.permits(entity, state);
                }, entity.blockPosition(), 48,
                        net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE).findAny().isPresent()) {
            cir.setReturnValue(true);
        }
    }
}
