package com.aetherianartificer.townstead.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.SpawnGroupData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the villager's origin at the TAIL of MCA's {@code finalizeSpawn}, which is
 * the only correct moment for it:
 *
 * <ul>
 *   <li><b>After MCA's randomize.</b> {@code finalizeSpawn} calls {@code super} (which fires the
 *       Forge/NeoForge FinalizeSpawn event) and THEN {@code initialize()}, which runs
 *       {@code getGenetics().randomize()} + {@code getTraits().randomize()}. Applying the origin
 *       in the event is clobbered by that randomize; injecting at TAIL runs after it, so the
 *       origin's size/width genes and trait grants stick.</li>
 *   <li><b>Before the first tick.</b> {@code VillagerLifeStamper.backfillIfMissing} (per-tick) stamps
 *       the default origin + seeds a genotype on any villager that has none. A next-tick deferral
 *       races that and loses (the genotype makes {@code onTrueSpawn} early-return as a "bred child").
 *       TAIL runs synchronously within the spawn, before the villager ever ticks.</li>
 * </ul>
 *
 * <p>The method name differs by branch: NeoForge keeps the Mojmap {@code finalizeSpawn} (4 args);
 * Forge 1.20.1 is the SRG {@code m_6518_} (5 args, with the extra {@code CompoundTag}). The handler
 * captures only the {@code CallbackInfoReturnable}, so one body serves both signatures.</p>
 */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerFinalizeSpawnMixin {

    //? if neoforge {
    @Inject(method = "finalizeSpawn", remap = false, at = @At("HEAD"), require = 1)
    //?} else {
    /*@Inject(method = "m_6518_", remap = false, at = @At("HEAD"), require = 1)
    *///?}
    private void townstead$deferInitialNaming(CallbackInfoReturnable<SpawnGroupData> cir) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (!self.level().isClientSide) {
            com.aetherianartificer.townstead.naming.SpawnNaming.begin(self);
        }
    }

    //? if neoforge {
    @Inject(method = "finalizeSpawn", remap = false, at = @At("TAIL"), require = 1)
    //?} else {
    /*@Inject(method = "m_6518_", remap = false, at = @At("TAIL"), require = 1)
    *///?}
    private void townstead$applyRootAfterInit(CallbackInfoReturnable<SpawnGroupData> cir) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (!self.level().isClientSide) {
            com.aetherianartificer.townstead.root.RootSpawnHandler.onTrueSpawn(self);
            com.aetherianartificer.townstead.naming.SpawnNaming.complete(self);
        }
    }
}
