package com.aetherianartificer.townstead.naming;

import net.conczin.mca.resources.WeightedPool;
import net.minecraft.resources.ResourceLocation;

/**
 * The family names a people uses. Surnames, house names and clan names, ungendered.
 *
 * <p>Ungendered on purpose: a family name says which family, not which person, so it carries no
 * gender of its own even in languages that inflect one onto it. Held apart from {@link GivenNames}
 * so a reference in a family rule can only ever find surnames.</p>
 */
public record FamilyNames(ResourceLocation id, WeightedPool<String> names) {

    public String pick() {
        return names == null ? "" : names.pickOne();
    }
}
