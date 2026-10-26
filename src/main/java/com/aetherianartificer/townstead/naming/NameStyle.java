package com.aetherianartificer.townstead.naming;

/** How much of a villager's name a surface shows. */
public enum NameStyle {
    /** Given and family name, in the order the villager's tradition reads. */
    FULL,
    /** The given name alone, which is what a villager was called before any of this existed. */
    GIVEN,
    /** The family name alone, for a surface that is already talking about a household. */
    FAMILY;

    /** Applies this style to a name already taken apart. */
    public String apply(String given, String family, NamingTradition.Order order) {
        String safeGiven = given == null ? "" : given;
        String safeFamily = family == null ? "" : family;
        return switch (this) {
            // The caller may already hold a composed name, so this removes the family name rather
            // than assuming it was never added.
            case GIVEN -> NameParts.withoutFamily(safeGiven, safeFamily, order);
            case FAMILY -> safeFamily.isEmpty() ? safeGiven : safeFamily;
            case FULL -> NameParts.format(safeGiven, safeFamily, order);
        };
    }
}
