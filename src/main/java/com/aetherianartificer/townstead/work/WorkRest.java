package com.aetherianartificer.townstead.work;

/**
 * The shared shape of "at ease": work that is blocked with no prospect rests on its feet.
 *
 * <p>Every work task eventually meets a state it cannot work through — the list is done and the
 * site is stood down, there is no rod anywhere, the water is gone. The principle, per the user
 * and applied to all work: release what you hold so players and other workers can use it, stop
 * asking the world the same question every tick, stay at the worksite and let the brain wander
 * you, and say why on the trade's status line. A worker who grinds their selector while
 * hopelessly blocked reads as broken and costs a hot path; one who walks home contradicts the
 * schedule. Resting is neither.</p>
 */
public final class WorkRest {

    /** How long a worker rests before glancing at their blocked question again. */
    public static final int REST_TICKS = 200;

    private WorkRest() {}
}
