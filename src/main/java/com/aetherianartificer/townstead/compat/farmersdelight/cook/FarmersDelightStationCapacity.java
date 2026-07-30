package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.station.StationCapacities;
import com.aetherianartificer.townstead.work.station.StationCapacity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Farmer's Delight's answer to "how many jobs does this station hold". Its stove and skillet only
 * report through their own block entities, reached reflectively because the fields move between
 * FD versions, so the engine cannot count them and does not try.
 *
 * <p>The stove is also the one station that spans several blocks, so this supplies the canonical
 * anchor that collapses a row of stove cells to a single station.</p>
 */
public final class FarmersDelightStationCapacity implements StationCapacity {

    private FarmersDelightStationCapacity() {}

    public static void bootstrap() {
        StationCapacities.register(new FarmersDelightStationCapacity());
    }

    private static boolean isFdSurface(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return StationHandler.isFdStove(id) || StationHandler.isFdSkillet(id);
    }

    @Override
    public int capacity(ServerLevel level, BlockPos pos, StationType type) {
        if (type != StationType.FIRE_STATION) return -1;
        if (!isFdSurface(level.getBlockState(pos))) return -1;
        return StationHandler.surfaceFreeSlotCount(level, pos);
    }

    @Override
    public @Nullable BlockPos anchor(ServerLevel level, BlockPos pos) {
        BlockPos canonical = StationHandler.canonicalStationAnchor(level, pos);
        return canonical.equals(pos) ? null : canonical;
    }
}
