package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.recognition.BuildingEnclosurePolicies;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Short-circuits {@link Building#validateBuilding} for the open-air form of a building type
 * whose {@code enclosure} is optional or none. MCA's flood-fill-from-a-door-plus-roof would
 * reject these as unroofed; {@code OptionalBuildingRecognition} has already decided they are
 * complete, so validation only prunes blocks that are gone.
 *
 * <p>HEAD cancellable, per Townstead's mixin policy: vanilla and MCA method call sites aren't
 * stable targets across remap configs.
 *
 * <p>Pre-v2 MCA only (gated in TownsteadMixinPlugin): floor-system v2 removed
 * {@code validateBuilding}, and open-air records live as ExternalBuildings there, which room
 * validation never touches.
 */
@Mixin(Building.class)
public abstract class BuildingValidateOpenAirMixin {
    @Inject(method = "validateBuilding", at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$openAirValidate(Level world, Set<BlockPos> blocked,
                                           CallbackInfoReturnable<Building.validationResult> cir) {
        Building self = (Building) (Object) this;
        boolean optionalOutdoorForm = BuildingEnclosurePolicies.allowsOpenAir(self.getType())
                && !self.isStrictScan();
        if (!optionalOutdoorForm) return;
        // Inlined equivalent of MCA's Building.validateBlocks (prune stored
        // positions whose world block no longer matches). Not called directly
        // because not every MCA build exposes it on Building; this mixin only
        // applies where its validateBuilding target exists (see TownsteadMixinPlugin).
        self.setLastScan(world.getGameTime());
        for (var positions : self.getBlocks().entrySet()) {
            positions.getValue().removeIf(pos -> !net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(world.getBlockState(pos).getBlock()).equals(positions.getKey()));
        }
        Building.validationResult result = self.getBlockPosStream().findAny().isEmpty()
                ? Building.validationResult.TOO_SMALL
                : Building.validationResult.SUCCESS;
        cir.setReturnValue(result);
    }
}
