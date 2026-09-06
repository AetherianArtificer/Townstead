package com.aetherianartificer.townstead.performance;

/** Idempotent cancellation handle returned by an animation/reaction provider. */
@FunctionalInterface
public interface PerformanceHandle {
    PerformanceHandle NONE = () -> {};
    void stop();
}
