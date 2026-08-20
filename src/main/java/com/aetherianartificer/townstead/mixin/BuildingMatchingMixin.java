package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import com.aetherianartificer.townstead.compat.mca.BuildingCandidatePolicy;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Applies catalog supersession to MCA's authoritative building recognition candidates. */
@Mixin(Building.class)
public abstract class BuildingMatchingMixin {
    @Inject(method = "matchesType", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private void townstead$rejectForcedSupersededType(
            BuildingType type, CallbackInfoReturnable<Boolean> cir) {
        if (type != null && CatalogDataLoader.isActiveSupersededBuildingType(type.name())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getMatchingTypes", at = @At("RETURN"), cancellable = true,
            remap = false, require = 0)
    private void townstead$removeSupersededRecognitionCandidates(
            CallbackInfoReturnable<List<BuildingType>> cir) {
        List<BuildingType> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        List<String> permittedNames = CatalogDataLoader
                .withoutActiveSupersededBuildingTypesForRecognition(
                        original.stream().map(BuildingType::name).toList());
        if (permittedNames.size() == original.size()) return;

        Set<String> permitted = new HashSet<>(permittedNames);
        cir.setReturnValue(original.stream()
                .filter(type -> permitted.contains(type.name()))
                .toList());
    }

    /**
     * MCA's scan, add-room, and registered-update paths all consume this list. Keep MCA's own
     * matching result and only normalize Townstead's cumulative tier families afterwards.
     */
    @Inject(method = "getVisibleMatchingTypes", at = @At("RETURN"), cancellable = true,
            remap = false, require = 0)
    private void townstead$normalizeVisibleRecognitionCandidates(
            CallbackInfoReturnable<List<BuildingType>> cir) {
        List<BuildingType> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;
        List<BuildingType> normalized = BuildingCandidatePolicy.normalizeForRecognition(original);
        if (!normalized.equals(original)) cir.setReturnValue(normalized);
    }
}
