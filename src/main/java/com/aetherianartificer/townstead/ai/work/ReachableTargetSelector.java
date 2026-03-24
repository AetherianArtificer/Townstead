package com.aetherianartificer.townstead.ai.work;

import com.aetherianartificer.townstead.hunger.TargetReachabilityCache;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class ReachableTargetSelector {
    private ReachableTargetSelector() {}

    public record Candidate<T>(T value, BlockPos pos) {}

    public static <T> @Nullable T chooseReachable(
            ServerLevel level,
            VillagerEntityMCA villager,
            List<Candidate<T>> candidates,
            int closeEnough,
            int maxAttempts,
            int failureTtlTicks,
            ToDoubleFunction<Candidate<T>> distanceScore
    ) {
        if (level == null || villager == null || candidates == null || candidates.isEmpty()) return null;
        List<Candidate<T>> ordered = candidates.stream()
                .sorted(Comparator.comparingDouble(distanceScore))
                .toList();
        int attempts = 0;
        for (Candidate<T> candidate : ordered) {
            if (candidate == null || candidate.pos() == null) continue;
            if (attempts >= maxAttempts) break;
            if (!TargetReachabilityCache.canAttempt(level, villager, candidate.pos())) continue;
            attempts++;
            Path path = villager.getNavigation().createPath(candidate.pos(), closeEnough);
            boolean success = path != null && path.canReach();
            WorkNavigationMetrics.recordPathAttempt(success);
            if (!success) {
                TargetReachabilityCache.recordFailure(level, villager, candidate.pos(), failureTtlTicks);
                continue;
            }
            TargetReachabilityCache.clear(level, villager, candidate.pos());
            return candidate.value();
        }
        return null;
    }
}
