package com.aetherianartificer.townstead.naming;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;

/**
 * Writes the composed name into MCA's family tree.
 *
 * <p>MCA's family tree screen does not read the villager at all. Each node keeps its own name
 * string, written once when the entry was created, so a family name never reaches the one screen
 * most obviously about families no matter what the entity reports. Nobody had solved this: MCA
 * Capitals keeps its surnames in its own data and leaves those nodes alone too.</p>
 *
 * <p>Registered as a name target, so it runs wherever a name settles and needs no schedule of its
 * own. The write goes straight to the family entry. MCA's {@code setName} is not used: on 1.20.1 it
 * also overwrites the villager's own name, which then carries the family name onto every surface.</p>
 *
 * <p>The tree is the same for every player, so a Persona who keeps their family name back is only
 * their given name here.</p>
 */
public final class FamilyTreeNameTarget {

    private FamilyTreeNameTarget() {}

    public static void bootstrap() {
        VillagerNames.addTarget(FamilyTreeNameTarget::write);
    }

    private static void write(VillagerEntityMCA villager, NameParts parts) {
        if (villager.level().isClientSide) return;

        String name = VillagerNames.familyHidden(villager, null)
                ? NameParts.withoutFamily(parts.given(), parts.family(), parts.order())
                : parts.fullName();
        if (name.isEmpty()) return;
        try {
            EntityRelationship.of(villager).ifPresent(relationship -> relationship.getFamilyEntry().setName(name));
        } catch (Throwable ignored) {
            // A villager with no family entry yet is one whose node is written when it is created.
        }
    }
}
