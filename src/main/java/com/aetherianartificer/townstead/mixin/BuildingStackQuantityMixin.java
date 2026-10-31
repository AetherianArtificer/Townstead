package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.mca.BuildingBlockQuantity;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Preserves MCA's position map while representing stateful same-tile stacks as repeated units. */
@Mixin(Building.class)
public abstract class BuildingStackQuantityMixin {
    @Inject(method = "recordBuildingBlock", at = @At("TAIL"), remap = false, require = 0)
    private void townstead$recordStackedBuildingUnits(Level level, BlockPos pos, CallbackInfo ci) {
        if (level == null || pos == null) return;
        BlockState state = level.getBlockState(pos);
        int units = BuildingBlockQuantity.units(state);
        if (units <= 1) return;

        Building building = (Building) (Object) this;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        List<BlockPos> positions = building.getBlocks().get(blockId);
        if (positions == null) return; // MCA did not consider this block relevant to any type.

        int recorded = 0;
        for (BlockPos recordedPos : positions) {
            if (pos.equals(recordedPos)) recorded++;
        }
        for (int i = recorded; i < units; i++) positions.add(pos.immutable());
    }
}
