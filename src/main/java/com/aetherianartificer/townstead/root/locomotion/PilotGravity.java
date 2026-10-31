package com.aetherianartificer.townstead.root.locomotion;

import net.minecraft.world.entity.Mob;

/**
 * Gravity a pilot switched off, marked in persistent data so it outlives the pilot.
 * {@code NoGravity} saves with the entity but the pilot does not, so a reload or chunk
 * unload mid-flight would otherwise leave the body floating with no one to land it.
 * Gravity switched off by anything else (abilities, actions, commands) carries no mark
 * and is never touched here.
 */
public final class PilotGravity {

    private static final String MARK = "townstead:pilot_gravity";

    private PilotGravity() {}

    /** Switch gravity off for a pilot, unless something else already has it off. */
    public static void hold(Mob mob) {
        if (mob.isNoGravity()) return;
        mob.setNoGravity(true);
        mob.getPersistentData().putBoolean(MARK, true);
    }

    /** Give back gravity a pilot switched off. */
    public static void release(Mob mob) {
        if (!mob.getPersistentData().getBoolean(MARK)) return;
        mob.getPersistentData().remove(MARK);
        mob.setNoGravity(false);
    }

    /** With no pilot flying, gravity a pilot left off is stale: give it back. */
    public static void releaseOrphaned(Mob mob) {
        if (mob.isNoGravity()) release(mob);
    }
}
