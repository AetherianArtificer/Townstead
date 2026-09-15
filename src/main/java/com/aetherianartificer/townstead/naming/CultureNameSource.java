package com.aetherianartificer.townstead.naming;

import net.conczin.mca.entity.VillagerEntityMCA;

/**
 * Townstead's own contribution: the family name a villager's culture gives them.
 *
 * <p>Registered first, so anything more specific can still override it. Contributes nothing when a
 * villager's culture declares no family-name rule, which is the case for every implicit culture and
 * is what keeps a world without culture packs exactly as it was.</p>
 */
public final class CultureNameSource {

    private CultureNameSource() {}

    public static void bootstrap() {
        VillagerNames.addSource(CultureNameSource::contribute);
    }

    private static void contribute(VillagerEntityMCA villager, NameParts.Builder builder) {
        builder.family(Naming.familyName(villager));
    }
}
