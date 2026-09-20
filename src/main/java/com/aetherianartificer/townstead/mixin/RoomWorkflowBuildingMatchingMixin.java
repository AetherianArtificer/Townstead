package com.aetherianartificer.townstead.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
//? if >=1.21 {
import com.aetherianartificer.townstead.compat.mca.BuildingCandidatePolicy;
import net.conczin.mca.server.world.data.BuildingScanResult;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}

/**
 * MCA decides whether to open its polymorph screen from the immutable room scan result, not
 * from Building#getMatchingTypes(). Superseded Townstead types are removed from that result
 * so the player is never asked to choose a type the catalog has retired. MCA 7.7.37+ builds
 * the result in RoomWorkflow; Pseudo keeps this inert on the pre-floor 1.20.1 line.
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.server.world.data.RoomWorkflow", remap = false)
public abstract class RoomWorkflowBuildingMatchingMixin {
    //? if >=1.21 {
    @Inject(method = "roomResultFromGeometry", at = @At("RETURN"), cancellable = true, remap = false)
    private void townstead$removeSupersededScanCandidates(CallbackInfoReturnable<BuildingScanResult> cir) {
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
}
