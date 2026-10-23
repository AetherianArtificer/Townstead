package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import com.aetherianartificer.townstead.compat.BuildingIconResolver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
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
    private Collection<String> townstead$hideSupersededBuildingTypes(Collection<String> matchingTypes) {
        return CatalogDataLoader.withoutActiveSupersededBuildingTypes(matchingTypes);
    }

    /** Draws a type's Townstead item instead of the MCA sprite its old UVs point at. */
    @WrapOperation(method = "drawBuildingIcon", remap = false, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/conczin/mca/resources/data/BuildingType;hasIcon()Z", remap = false))
    private boolean townstead$itemIcon(BuildingType type, Operation<Boolean> original,
            @Local(argsOnly = true) GuiGraphics context,
            @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y) {
        ItemStack icon = BuildingIconResolver.itemIcon(type);
        if (icon.isEmpty()) return original.call(type);
        // MCA centres its 13px sprite on (x, y).
        context.renderItem(icon, x - 8, y - 8);
        return false;
    }
}
