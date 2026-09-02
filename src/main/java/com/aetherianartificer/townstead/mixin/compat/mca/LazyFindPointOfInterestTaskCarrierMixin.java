package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.profession.ProfessionCarriers;
import net.conczin.mca.entity.ai.brain.tasks.LazyFindPointOfInterestTask;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Keeps the job hunt away from job blocks whose hiring belongs to a Townstead-seated career.
 *
 * <p>MCA builds every villager's work acquisition from this one factory. Narrowing the predicate
 * here means a jobless villager never walks to a carrier profession's skillet in the first
 * place, so there is nothing to cancel or fight later: the seat resolver is the only thing that
 * hires for that career. On 1.20.1 the factory shares vanilla's {@code AcquirePoi.create}
 * signature and so carries an SRG name.</p>
 */
@Mixin(LazyFindPointOfInterestTask.class)
public abstract class LazyFindPointOfInterestTaskCarrierMixin {

    //? if neoforge {
    @ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true, remap = false)
    //?} else {
    /*@ModifyVariable(method = "m_258026_", at = @At("HEAD"), argsOnly = true, remap = false)
    *///?}
    private static Predicate<Holder<PoiType>> townstead$skipSeatedCareerJobSites(
            Predicate<Holder<PoiType>> poiPredicate,
            Predicate<Holder<PoiType>> ignored,
            MemoryModuleType<GlobalPos> poiPosModule,
            MemoryModuleType<GlobalPos> potentialPoiPosModule,
            boolean onlyRunIfChild,
            Optional<Byte> entityStatus) {
        if (poiPosModule != MemoryModuleType.JOB_SITE) return poiPredicate;
        return ProfessionCarriers.excludingOwned(poiPredicate);
    }
}
