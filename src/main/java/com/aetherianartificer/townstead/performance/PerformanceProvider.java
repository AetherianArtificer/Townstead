package com.aetherianartificer.townstead.performance;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Optional backend for semantic performances; implementations must not assume a specific caller. */
public interface PerformanceProvider {
    String id();
    /** Higher values are tried first; id is the deterministic tie-breaker. */
    default int priority() { return 0; }
    boolean supports(PerformanceRequest request);
    /** Explicit pack mappings may identify a backend even when the clip uses a pack-owned namespace. */
    default boolean supportsMapped(PerformanceRequest request) { return supports(request); }
    @Nullable PerformanceHandle play(ServerLevel level, PerformanceRequest request);
}
