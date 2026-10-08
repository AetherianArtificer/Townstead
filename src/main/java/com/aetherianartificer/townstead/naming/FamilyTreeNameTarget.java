package com.aetherianartificer.townstead.naming;

import net.conczin.mca.entity.VillagerEntityMCA;

/**
 * Writes the composed name into MCA's family tree.
 *
 * <p>MCA's family tree screen does not read the villager at all. Each node keeps its own name
 * string, written once when the entry was created, so a family name never reaches the one screen
 * most obviously about families no matter what the entity reports. Nobody had solved this: MCA
 * Capitals keeps its surnames in its own data and leaves those nodes alone too.</p>
 *
 * <p>Registered as a name target, so it runs wherever a name settles and needs no schedule of its
 * own. The write goes through MCA's own {@code setName}, which updates the family entry and nothing
 * else, so the villager's identity and custom name are untouched.</p>
 */
public final class FamilyTreeNameTarget {

    private FamilyTreeNameTarget() {}

    public static void bootstrap() {
        VillagerNames.addTarget(FamilyTreeNameTarget::write);
    }

    private static void write(VillagerEntityMCA villager, NameParts parts) {
        if (villager.level().isClientSide) return;

        String full = parts.fullName();
        if (full.isEmpty()) return;
        try {
            villager.setName(full);
        } catch (Throwable ignored) {
            // A villager with no family entry yet is one whose node is written when it is created.
        }
    }
}
