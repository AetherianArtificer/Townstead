package com.aetherianartificer.townstead.performance;

import com.aetherianartificer.townstead.reaction.ReactionContext;
import com.aetherianartificer.townstead.reaction.ReactionDispatcher;
import com.aetherianartificer.townstead.reaction.ReactionRegistry;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Makes the existing reaction backend stack available to semantic performances. */
public final class ReactionPerformanceProvider implements PerformanceProvider {
    public static final String ID = "townstead:reaction";

    @Override public String id() { return ID; }
    @Override public int priority() { return 100; }

    @Override
    public boolean supports(PerformanceRequest request) {
        return ReactionRegistry.get(request.performance()).isPresent();
    }

    @Override
    public @Nullable PerformanceHandle play(ServerLevel level, PerformanceRequest request) {
        boolean fired = ReactionDispatcher.fire(level, request.actor(), request.performance(),
                new ReactionContext(ReactionContext.TriggerSource.TASK, null,
                        request.actor().blockPosition(), Set.of("townstead:performance"), 0));
        return fired ? PerformanceHandle.NONE : null;
    }
}
