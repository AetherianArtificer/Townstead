package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.work.WorkHazards;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/** Farmer's Delight's hot and unstandable blocks, as judged for pathing. */
public final class FarmersDelightWorkHazards implements WorkHazards.WorkHazard {

    private FarmersDelightWorkHazards() {}

    public static void bootstrap() {
        WorkHazards.register(new FarmersDelightWorkHazards());
    }

    @Override
    public boolean hazardous(BlockGetter level, BlockPos pos) {
        return FarmersDelightPathingHooks.isHazardousCookware(level, pos);
    }

    @Override
    public boolean unsafeSurface(BlockGetter level, BlockPos pos) {
        return FarmersDelightPathingHooks.isUnsafeWorkSurface(level, pos);
    }
}
