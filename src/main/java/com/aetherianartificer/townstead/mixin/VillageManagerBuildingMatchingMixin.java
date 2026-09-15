package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.VillageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers MCA's grouped-building fast path, which does not call Building#getMatchingTypes(). */
@Mixin(VillageManager.class)
public abstract class VillageManagerBuildingMatchingMixin {
    @Inject(method = "getGroupedBuildingType", at = @At("RETURN"), cancellable = true,
            remap = false, require = 0)
    private void townstead$rejectSupersededGroupedType(CallbackInfoReturnable<BuildingType> cir) {
        BuildingType matched = cir.getReturnValue();
        if (matched != null && CatalogDataLoader.isActiveSupersededBuildingType(matched.name())) {
            cir.setReturnValue(null);
        }
    }
}
