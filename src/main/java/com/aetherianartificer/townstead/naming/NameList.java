package com.aetherianartificer.townstead.naming;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.WeightedPool;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A bag of names, and nothing else. Given names by gender, family names ungendered.
 *
 * <p>A list carries no rules about how a name is built and no meaning about who bears it. Both of
 * those belong to a {@link NamingTradition}, which points at lists; a list is only the raw
 * material, so the same pool can serve several traditions without implying they are related.</p>
 *
 * @param given  by gender, always holding at least MALE and FEMALE when non-empty, because MCA
 *               picks by binary gender and dereferences the result without a null check
 * @param family ungendered family, clan and house names; null when the list offers none
 */
public record NameList(ResourceLocation id,
                       Map<Gender, WeightedPool<String>> given,
                       @Nullable WeightedPool<String> family) {

    public NameList {
        given = given == null ? Map.of() : Map.copyOf(given);
    }

    public boolean hasGiven() {
        return !given.isEmpty();
    }

    public boolean hasFamily() {
        return family != null;
    }

    /** A family name from this list, or empty when it offers none. */
    public String pickFamily() {
        return family == null ? "" : family.pickOne();
    }
}
