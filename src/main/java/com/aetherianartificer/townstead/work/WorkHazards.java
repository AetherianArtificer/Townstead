package com.aetherianartificer.townstead.work;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cells a working villager should not walk into or stand on, as judged by whoever knows the block.
 *
 * <p>A lit stove burns, and a cooking pot is a work surface rather than a floor. Neither fact is
 * derivable from a workstation def, and both come from blocks the engine has no business naming,
 * so the engine asks and the mod's compat answers.</p>
 *
 * <p>Consulted from pathing, which runs often, so the no-provider case is a single list read.</p>
 */
public final class WorkHazards {

    /** One source of judgements about dangerous or unstandable cells. */
    public interface WorkHazard {

        /** Standing here would hurt: an open flame, a lit stove. */
        default boolean hazardous(BlockGetter level, BlockPos pos) {
            return false;
        }

        /** This cell is a work surface rather than somewhere to put your feet. */
        default boolean unsafeSurface(BlockGetter level, BlockPos pos) {
            return false;
        }
    }

    private static final List<WorkHazard> PROVIDERS = new CopyOnWriteArrayList<>();

    private WorkHazards() {}

    public static void register(WorkHazard provider) {
        if (provider != null) PROVIDERS.add(provider);
    }

    public static boolean hazardous(BlockGetter level, BlockPos pos) {
        if (PROVIDERS.isEmpty()) return false;
        for (WorkHazard provider : PROVIDERS) {
            if (provider.hazardous(level, pos)) return true;
        }
        return false;
    }

    public static boolean unsafeSurface(BlockGetter level, BlockPos pos) {
        if (PROVIDERS.isEmpty()) return false;
        for (WorkHazard provider : PROVIDERS) {
            if (provider.unsafeSurface(level, pos)) return true;
        }
        return false;
    }
}
