package com.aetherianartificer.townstead.naming;

import net.conczin.mca.entity.VillagerEntityMCA;

/**
 * An outside opinion on which naming register a villager belongs to.
 *
 * <p>A villager who has never been recorded still often carries evidence of their naming tradition
 * somewhere: a surname another mod already gave them, a title, a record in some other register of
 * people. A source of that evidence registers itself through
 * {@link NamingRegisters#addEvidence(RegisterEvidence)} at setup, and is consulted after the
 * villager's own record and before anything inferred from their household or their surroundings,
 * because evidence about this villager beats a guess from the people around them.</p>
 *
 * <p>Evidence is advisory. Returning a register that has no loaded name pool, or one that
 * disagrees with what is already recorded, changes nothing: {@link NamingRegisters} validates
 * every answer and never overwrites a register a villager already has.</p>
 */
@FunctionalInterface
public interface RegisterEvidence {

    /**
     * The register this source believes the villager belongs to, or empty when it has nothing to
     * say. Must not throw, must not write, and must tolerate a villager it knows nothing about.
     */
    String registerFor(VillagerEntityMCA villager);
}
