package com.aetherianartificer.townstead.naming;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A villager's name broken into the pieces anything might want to show.
 *
 * <p>Kept as parts rather than a finished string on purpose. Whoever renders a name wants different
 * pieces of it: a nameplate wants the title above the name, a dialogue header wants the name alone,
 * a chronicle entry wants the style a person was addressed by at the time. Handing every one of
 * them a single rendered string forces them to take it apart again, and taking a name apart by
 * looking for a title at the front is exactly the trap MCA Capitals had to dig itself out of when
 * its titled villagers started losing their original names.</p>
 *
 * <p>Parts arrive from {@link NamePartSource}s and go out to {@link NameTarget}s, so a mod that
 * knows a villager's rank contributes it without knowing who will draw it, and a mod that draws
 * names receives them without knowing who supplied them.</p>
 */
public record NameParts(String given,
                        String family,
                        @Nullable Component title,
                        @Nullable Component style,
                        String epithet,
                        NamingTradition.Order order) {

    public NameParts {
        given = given == null ? "" : given.trim();
        family = family == null ? "" : family.trim();
        epithet = epithet == null ? "" : epithet.trim();
        order = order == null ? NamingTradition.Order.GIVEN_FIRST : order;
    }

    public static Builder builder(String given) {
        return new Builder(given);
    }

    public boolean hasFamily() {
        return !family.isEmpty();
    }

    public boolean hasTitle() {
        return title != null && !title.getString().isBlank();
    }

    /**
     * Given and family in this tradition's order, with neither repeated. A name that already ends
     * in its own family name is left alone, since some sources hand over a name that carries it.
     */
    public String fullName() {
        return format(given, family, order);
    }

    public static String format(String given, String family, NamingTradition.Order order) {
        given = given == null ? "" : given.trim();
        family = family == null ? "" : family.trim();
        if (given.isEmpty()) return family;
        if (family.isEmpty()) return given;
        if (given.equals(family) || given.endsWith(" " + family) || given.startsWith(family + " ")) return given;
        return order == NamingTradition.Order.FAMILY_FIRST
                ? family + " " + given
                : given + " " + family;
    }

    /** The full name with the title in front, for surfaces that show rank inline. */
    public Component titled() {
        Component name = Component.literal(fullName());
        return hasTitle() ? Component.translatable("townstead.name.titled", title, name) : name;
    }

    /** Mutable while sources contribute; a later source overrides an earlier one part by part. */
    public static final class Builder {
        private String given;
        private String family = "";
        private boolean familyAuthoritative;
        private Component title;
        private Component style;
        private String epithet = "";
        private NamingTradition.Order order = NamingTradition.Order.GIVEN_FIRST;

        private Builder(String given) {
            this.given = given == null ? "" : given;
        }

        public Builder given(String value) {
            if (value != null && !value.isBlank()) given = value;
            return this;
        }

        public Builder family(String value) {
            if (!familyAuthoritative && value != null && !value.isBlank()) family = value;
            return this;
        }

        /** A deliberate external rename outranks generated cultural defaults, including an empty name. */
        public Builder authoritativeFamily(String value) {
            family = value == null ? "" : value;
            familyAuthoritative = true;
            return this;
        }

        public Builder title(@Nullable Component value) {
            if (value != null && !value.getString().isBlank()) title = value;
            return this;
        }

        public Builder style(@Nullable Component value) {
            if (value != null && !value.getString().isBlank()) style = value;
            return this;
        }

        public Builder epithet(String value) {
            if (value != null && !value.isBlank()) epithet = value;
            return this;
        }

        public Builder order(@Nullable NamingTradition.Order value) {
            if (value != null) order = value;
            return this;
        }

        public NameParts build() {
            return new NameParts(given, family, title, style, epithet, order);
        }
    }
}
