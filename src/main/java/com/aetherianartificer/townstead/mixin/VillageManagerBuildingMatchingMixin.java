package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import com.aetherianartificer.townstead.compat.mca.BuildingCandidatePolicy;
import net.conczin.mca.resources.data.BuildingType;
//? if >=1.21 {
import net.conczin.mca.server.world.data.BuildingScanResult;
//?}
import net.conczin.mca.server.world.data.VillageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers MCA's grouped-building fast path, which does not call Building#getMatchingTypes(). */
@Mixin(VillageManager.class)
public abstract class VillageManagerBuildingMatchingMixin {
    //? if >=1.21 {
    /**
     * MCA decides whether to open its polymorph screen from the immutable room scan result, not
     * by asking the Building again. Filtering only Building#getMatchingTypes therefore protected
     * recognition but could still leave an already-built result containing two names; the client
     * hid the superseded one and displayed a pointless one-choice modal. All floor-system room
     * scans converge through roomResultFromGeometry, including initial add, add-room, and update,
     * so filter the result there before any of those callers can branch on isAmbiguous().
     */
    @Inject(method = "roomResultFromGeometry", at = @At("RETURN"), cancellable = true,
            remap = false, require = 0)
    private void townstead$removeSupersededScanCandidates(
            CallbackInfoReturnable<BuildingScanResult> cir) {
        BuildingScanResult scan = cir.getReturnValue();
        if (scan == null || scan.matchingTypes().isEmpty()) return;

        java.util.List<String> permitted = BuildingCandidatePolicy
                .normalizeNamesForRecognition(scan.matchingTypes());
        if (permitted.equals(scan.matchingTypes())) return;

        cir.setReturnValue(new BuildingScanResult(
                scan.result(), scan.source(), scan.building(), permitted, scan.village(),
                scan.pendingStructure()));
    }
    //?}

    @Inject(method = "getGroupedBuildingType", at = @At("RETURN"), cancellable = true,
            remap = false, require = 0)
    private void townstead$rejectSupersededGroupedType(CallbackInfoReturnable<BuildingType> cir) {
        BuildingType matched = cir.getReturnValue();
        if (matched != null && CatalogDataLoader.isActiveSupersededBuildingType(matched.name())) {
            cir.setReturnValue(null);
        }
    }
}
