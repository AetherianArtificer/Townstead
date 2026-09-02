package com.aetherianartificer.townstead.work.producer;

/** Prevents a one-shot tool interaction from being repeated against an already-drained station. */
final class ToolWorkActionGate {
    private ToolWorkActionGate() {}

    static boolean shouldPerform(boolean usesTool, boolean outputReady, boolean alreadyPerformed) {
        return usesTool && !outputReady && !alreadyPerformed;
    }
}
