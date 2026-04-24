package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.enclosure.EnclosureTypeIndex;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Short-circuits {@link Building#validateBuilding} for open-air Townstead
 * types: docks (prefix {@code dock_}) and any type registered via the
 * data-driven enclosure index ({@code pen}, {@code compat/butchery/slaughter_pen},
 * and anything else authors flag with {@code townsteadEnclosure}). These all
 * have companion synthesizers (DockBuildingSync / EnclosureBuildingSync) that
 * actively build the Building from the detected shape; MCA's default
 * flood-fill-from-a-door-plus-roof would reject them as unroofed.
 *
 * <p>Using MCA's native grouped path instead would double-dip: MCA would
 * auto-create its own Building from the tracked blocks in parallel with our
 * synthetic, and every rescan would multiply the instance count. So these
 * types stay on this mixin and the synthesizers own the instance.
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
        if (type == null) return;
        boolean isOpenAir = type.startsWith("dock_") || EnclosureTypeIndex.isEnclosureType(type);
        if (!isOpenAir) return;
        self.validateBlocks(world);
        Building.validationResult result = self.getBlockPosStream().findAny().isEmpty()
                ? Building.validationResult.TOO_SMALL
                : Building.validationResult.SUCCESS;
        cir.setReturnValue(result);
    }
}
