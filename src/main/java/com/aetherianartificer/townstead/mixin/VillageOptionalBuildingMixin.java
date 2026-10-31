package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.recognition.BuildingEnclosurePolicies;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Makes MCA's building queries see both physical forms of an optional building type.
 *
 * <p>Vanilla MCA branches solely on {@code BuildingType.grouped()}: false searches rooms and true
 * searches external buildings. Optional enclosure deliberately permits both, so neither branch is
 * complete on its own.</p>
 */
@Mixin(Village.class)
public abstract class VillageOptionalBuildingMixin {
    @Inject(method = "getBuildingsOfType", at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$bothBuildingForms(String type, CallbackInfoReturnable<Stream<Building>> cir) {
        if (!BuildingEnclosurePolicies.allowsOpenAir(type)) return;
        Village self = (Village) (Object) this;
        cir.setReturnValue(McaBuildings.all(self).stream()
                .filter(building -> type.equals(building.getType())));
    }

    @Inject(method = "hasBuilding", at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$hasEitherBuildingForm(String type, CallbackInfoReturnable<Boolean> cir) {
        if (!BuildingEnclosurePolicies.allowsOpenAir(type)) return;
        Village self = (Village) (Object) this;
        cir.setReturnValue(McaBuildings.all(self).stream()
                .anyMatch(building -> type.equals(building.getType()) && building.isComplete()));
    }

    /** Rank requirements read this snapshot instead of hasBuilding. */
    @Inject(method = "getPresentBuildingTypes", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void townstead$presentEitherBuildingForm(CallbackInfoReturnable<Set<String>> cir) {
        Village self = (Village) (Object) this;
        Set<String> types = new HashSet<>(cir.getReturnValue());
        boolean added = false;
        for (Building building : McaBuildings.all(self)) {
            String type = building.getType();
            if (building.isComplete() && BuildingEnclosurePolicies.allowsOpenAir(type)) added |= types.add(type);
        }
        if (added) cir.setReturnValue(Set.copyOf(types));
    }
}
