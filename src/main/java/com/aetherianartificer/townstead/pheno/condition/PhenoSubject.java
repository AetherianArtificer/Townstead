package com.aetherianartificer.townstead.pheno.condition;

import com.aetherianartificer.townstead.root.CanonicalStage;
import com.aetherianartificer.townstead.social.Bonds;

import java.util.UUID;

/**
 * Someone a condition can be asked about who is not standing in the world: a
 * villager's younger self, a person a fabricated history is about to invent.
 * Only the facts such a person can actually carry, so a condition that needs
 * live world state (light level, nearby blocks) cannot pretend to answer.
 */
public interface PhenoSubject {

    UUID uuid();

    String displayName();

    /** Namespaced profession id, empty for none. */
    String professionId();

    /** The stage they present as at the moment being asked about. */
    CanonicalStage lifeStage();

    Bonds bonds();

    /**
     * A chronicle counter for this subject. Live, this is the server's exact
     * tally; for someone being fabricated it is what their own invented history
     * has accumulated so far, so "the first meal they ever cooked" means the same
     * thing in both places.
     */
    default int counter(String key) {
        return 0;
    }
}
