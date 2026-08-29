package com.aetherianartificer.townstead.social;

import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Someone's ties, however they are stored. Live villagers answer from MCA's
 * relationship state; a fabricated past answers from the bonds the fabrication
 * has formed so far. Same question, so {@code pheno:bonds} reads the same in
 * both places.
 *
 * <p>Townstead does not own a bond store yet: this reads MCA today and is the
 * seam a Townstead-owned store would slot behind.</p>
 */
@FunctionalInterface
public interface Bonds {

    Bonds EMPTY = List::of;

    List<Bond> all();

    default int count(String kind, boolean activeOnly) {
        int count = 0;
        for (Bond bond : all()) {
            if (!bond.kind().equals(kind)) continue;
            if (activeOnly && !bond.active()) continue;
            count++;
        }
        return count;
    }

    default boolean has(String kind, boolean activeOnly) {
        return count(kind, activeOnly) > 0;
    }

    /** The feed id a bond kind declares to be filled from MCA's marriage state. */
    String SOURCE_MCA_MARRIAGE = "mca:marriage";

    static Bonds of(LivingEntity entity) {
        if (entity == null) return EMPTY;
        return () -> {
            List<BondKind> married = BondKinds.bySource(SOURCE_MCA_MARRIAGE);
            if (married.isEmpty()) return List.of();
            List<Bond> bonds = new ArrayList<>(1);
            try {
                EntityRelationship.of(entity).ifPresent(rel -> {
                    if (!rel.isMarried()) return;
                    // MCA keeps no wedding date, so the bond reads as ongoing from day zero.
                    for (BondKind kind : married) {
                        bonds.add(Bond.ongoing(kind.id().toString(),
                                rel.getPartnerUUID().orElse(null),
                                rel.getPartnerName()
                                        .map(net.minecraft.network.chat.Component::getString)
                                        .orElse(""), 0L));
                    }
                });
            } catch (Throwable ignored) {
                // A relationship API this MCA build does not have: no bonds rather than a crash.
            }
            return bonds;
        };
    }

    static Bonds of(List<Bond> bonds) {
        List<Bond> copy = List.copyOf(bonds);
        return () -> copy;
    }
}
