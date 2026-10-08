package com.aetherianartificer.townstead.naming;

import net.conczin.mca.entity.VillagerEntityMCA;

/**
 * Receives a villager's composed name so something can show it.
 *
 * <p>A target is a sink rather than a reader: Townstead pushes the finished parts to it. That is
 * what lets another mod's renderer display a Townstead name without either mod knowing about the
 * other's screens. MCA Capitals is a target, because writing the family name into the identity it
 * already renders puts a culture's name on its nameplate, in its Book of Houses and in its chronicle
 * entries at once, rather than Townstead drawing a competing nameplate over the top.</p>
 *
 * <p>There is always at least one target, Townstead's own screens, so a name is never invisible
 * because a mod is absent.</p>
 */
@FunctionalInterface
public interface NameTarget {

    /** Takes the composed name. Must not throw, and must tolerate being called repeatedly. */
    void accept(VillagerEntityMCA villager, NameParts parts);
}
