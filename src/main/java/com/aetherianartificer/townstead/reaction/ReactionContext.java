package com.aetherianartificer.townstead.reaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Snapshot of the situation around a single {@code fire} call: which event
 * spawned it, who caused it (player gestures), where it's happening, the
 * context tags already resolved by the trigger source (or empty to let the
 * dispatcher run {@code ContextResolver} itself), and the current mirror
 * recursion depth.
 */
public record ReactionContext(
        TriggerSource source,
        @Nullable Player playerCause,
        @Nullable BlockPos location,
        Set<String> contextTags,
        int mirrorDepth) {

    public static ReactionContext command(@Nullable BlockPos location) {
        return new ReactionContext(TriggerSource.COMMAND, null, location, Set.of(), 0);
    }

    public ReactionContext withDepth(int newDepth) {
        return new ReactionContext(source, playerCause, location, contextTags, newDepth);
    }

    public enum TriggerSource {
        TASK,
        GESTURE,
        CONTEXT,
        MIRROR,
        IDLE_SPOT,
        TIME,
        COMMAND
    }
}
