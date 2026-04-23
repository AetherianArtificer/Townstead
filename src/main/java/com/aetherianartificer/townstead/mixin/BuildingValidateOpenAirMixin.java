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
 * Short-circuits {@link Building#validateBuilding} for the {@code dock_l*}
 * building types. Docks are open-air piers that would fail MCA's default
 * flood-fill-from-a-door-plus-roof validation, but unlike other open-air
 * buildings (livestock pens) docks have a companion synthesizer
 * ({@code DockBuildingSync}) that actively creates the Building instance
 * from a detected {@code Dock} shape. Using MCA's native grouped path for
 * docks would double-dip: MCA would auto-create its own Building from the
 * tracked planks in parallel with our synthetic, and every rescan would
 * multiply the instance count into a crash. So docks stay on this narrow
 * mixin, which only affects validation and lets our synthesizer own the
 * instance.
 *
 * <p>Other open-air types (pen, slaughter_pen, etc.) do not have a
 * synthesizer and use {@code "grouped": true} in their JSON to take
 * MCA's native grouped path instead.
 *
 * <p>HEAD cancellable — per Townstead's mixin policy, vanilla and MCA
 * method call sites aren't stable targets across remap configs.
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
