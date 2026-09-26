package com.aetherianartificer.townstead.naming;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.WeightedPool;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * The given names a people uses, by gender. First names, and nothing else.
 *
 * <p>Held apart from {@link FamilyNames} because the two answer different questions and a naming
 * tradition asks them in different places. A reference in a tradition's {@code given} can only ever
 * find one of these, so a surname pool can never be handed to something expecting first names.</p>
 *
 * @param byGender always holding at least MALE and FEMALE when non-empty, because MCA picks by
 *                 binary gender and dereferences the result without a null check
 */
public record GivenNames(ResourceLocation id, Map<Gender, WeightedPool<String>> byGender) {

    public GivenNames {
        byGender = byGender == null ? Map.of() : Map.copyOf(byGender);
    }

    public boolean isEmpty() {
        return byGender.isEmpty();
    }
}
