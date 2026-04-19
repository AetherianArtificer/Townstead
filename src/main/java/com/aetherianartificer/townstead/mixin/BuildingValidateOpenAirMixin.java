package com.aetherianartificer.townstead.mixin;

import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Short-circuits {@link Building#validateBuilding} for Townstead's open-air
 * building types ({@code dock_l1/l2/l3}). MCA's normal validation flood-fills
 * an interior room from a door and requires a roof — an open-air pier fails
 * that check and would be removed on every re-scan. For docks we instead
 * mirror the grouped-building path: prune stale entries from the stored
 * blocks map and return SUCCESS as long as any block remains.
 *
 * This preserves the invariant that breaking all the docks's tracked blocks
 * (e.g., all the planks being mined) still invalidates the building, matching
 * how MCA treats an emptied grouped structure.
 *
 * We use {@code @Inject(HEAD, cancellable = true)} rather than targeting an
 * {@code @At INVOKE} inside the method — per Townstead's mixin policy, vanilla
 * (and MCA) method call sites aren't stable targets across remap configs, and
 * HEAD cancellable is the safer pattern.
 */
@Mixin(Building.class)
public abstract class BuildingValidateOpenAirMixin {
    @Inject(method = "validateBuilding", at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$openAirValidate(Level world, Set<BlockPos> blocked,
                                           CallbackInfoReturnable<Building.validationResult> cir) {
        Building self = (Building) (Object) this;
        String type = self.getType();
        if (type == null || !type.startsWith("dock_")) return;
        self.validateBlocks(world);
        Building.validationResult result = self.getBlockPosStream().findAny().isEmpty()
                ? Building.validationResult.TOO_SMALL
                : Building.validationResult.SUCCESS;
        cir.setReturnValue(result);
    }
}
