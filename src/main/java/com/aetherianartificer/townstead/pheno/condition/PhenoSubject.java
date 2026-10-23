package com.aetherianartificer.townstead.pheno.condition;

import com.aetherianartificer.townstead.root.CanonicalStage;
import com.aetherianartificer.townstead.root.LifeStage;
import com.aetherianartificer.townstead.social.Bonds;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
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

    /**
     * Open semantic tags of the resolved life stage. Historical/fabricated subjects default to
     * canonical compatibility tags, while richer subjects may override this with authored tags.
     */
    default Set<ResourceLocation> lifeStageTags() {
        return LifeStage.defaultTags(lifeStage());
    }

    Bonds bonds();

    /** Known experience supplied by a simulation, or null when it cannot answer belief queries. */
    default com.aetherianartificer.townstead.social.SocialKnowledge socialKnowledge() { return null; }

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
