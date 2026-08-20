package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Collection;

/**
 * Applies Townstead's catalog supersession rules to MCA's polymorph candidates before
 * the screen copies, paginates, and turns them into buttons.
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.client.gui.BuildingPolymorphScreen", remap = false)
public abstract class BuildingPolymorphScreenMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Ljava/util/List;copyOf(Ljava/util/Collection;)Ljava/util/List;"),
            index = 0, remap = false, require = 0)
    private static Collection<String> townstead$hideSupersededBuildingTypes(Collection<String> matchingTypes) {
        return CatalogDataLoader.withoutActiveSupersededBuildingTypes(matchingTypes);
    }
}
