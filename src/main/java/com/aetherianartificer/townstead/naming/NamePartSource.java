package com.aetherianartificer.townstead.naming;

import net.conczin.mca.entity.VillagerEntityMCA;

/**
 * Contributes a piece of a villager's name.
 *
 * <p>A source fills in only what it knows: a culture supplies a family name, a court supplies a
 * title and a courtesy style, a profession might supply an epithet. Sources run in registration
 * order and a later one overrides an earlier one part by part, so the order they are registered in
 * is the order of authority.</p>
 *
 * <p>A source that depends on another mod gates itself on that mod being present rather than being
 * gated by the registry, which is why nothing in this package names a specific mod.</p>
 */
@FunctionalInterface
public interface NamePartSource {

    /** Adds whatever this source knows. Must not throw, and may add nothing at all. */
    void contribute(VillagerEntityMCA villager, NameParts.Builder builder);
}
