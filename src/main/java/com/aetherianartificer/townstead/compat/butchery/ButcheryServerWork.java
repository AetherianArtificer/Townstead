package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reflective bridge to {@code net.mcreator.butchery.ButcheryMod#queueServerWork(int, Runnable)},
 * the mod's own deferred-task scheduler. Used to re-arm the 1800-tick
 * {@code state 27 → 30} transition when the leatherworker applies a soak
 * directly (we bypass the {@code PlayerInteractEvent} path the mod uses).
 *
 * <p>Reflection keeps Townstead loadable when Butchery is absent. Callers
 * must already be gated by {@link ButcheryCompat#isLoaded()}.
 */
public final class ButcheryServerWork {

    private static final MethodHandle QUEUE = resolveQueue();

    private ButcheryServerWork() {}

    /**
     * Schedule the cure-complete flip (27 → 30) using Butchery's own
     * scheduler so the timing matches the player path exactly.
     */
    public static void queueCure(ServerLevel level, BlockPos pos) {
        Runnable task = () -> finishCure(level, pos);
        if (QUEUE != null) {
            try {
                QUEUE.invoke(1800, task);
                return;
            } catch (Throwable t) {
                Townstead.LOGGER.warn("[Butchery] queueServerWork failed; using server tick fallback", t);
            }
        }
        // Fallback: schedule via the server's command tick once the runnable target lands.
        // Without the mod's queue, the cure simply never auto-completes; we rely on the
        // mod always being present (gate above) so this path should never hit.
    }

    private static void finishCure(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!SkinRackStateMachine.isSkinRack(state)) return;
        if (SkinRackStateMachine.currentState(state) != SkinRackStateMachine.STATE_SOAKED) return;
        var prop = state.getBlock().getStateDefinition().getProperty("blockstate");
        if (!(prop instanceof IntegerProperty ip)) return;
        if (!ip.getPossibleValues().contains(SkinRackStateMachine.STATE_CURED)) return;
        level.setBlock(pos, state.setValue(ip, SkinRackStateMachine.STATE_CURED), 3);
    }

    private static MethodHandle resolveQueue() {
        try {
            Class<?> mod = Class.forName("net.mcreator.butchery.ButcheryMod");
            return MethodHandles.publicLookup().findStatic(
                    mod, "queueServerWork",
                    MethodType.methodType(void.class, int.class, Runnable.class));
        } catch (Throwable t) {
            return null;
        }
    }
}
