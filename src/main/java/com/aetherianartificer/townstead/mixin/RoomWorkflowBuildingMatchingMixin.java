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
        permitted = townstead$passingChecks(scan, permitted);
        if (permitted.equals(scan.matchingTypes())) return;
        cir.setReturnValue(new BuildingScanResult(
                scan.result(), scan.source(), scan.building(), permitted, scan.village(),
                scan.pendingStructure()));
    }

    /**
     * A type whose Townstead checks fail (a Lord's Hall with no Throne) is not offered in the
     * polymorph choice. When every candidate fails, MCA's list is kept and the reconciler moves
     * the room to a passing type after the scan.
     */
    private static java.util.List<String> townstead$passingChecks(BuildingScanResult scan, java.util.List<String> names) {
        if (scan.village() == null || scan.building() == null) return names;
        var level = com.aetherianartificer.townstead.recognition.BuildingChecks.levelOf(
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer(), scan.village());
        if (level == null) return names;
        java.util.List<String> passing = names.stream()
                .filter(name -> com.aetherianartificer.townstead.recognition.BuildingChecks
                        .failure(level, scan.village(), scan.building(), name) == null)
                .toList();
        return passing.isEmpty() ? names : passing;
    }
    //?}
}
