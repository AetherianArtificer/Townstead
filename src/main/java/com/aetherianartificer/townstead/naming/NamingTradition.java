package com.aetherianartificer.townstead.naming;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a people build a name: which given-name lists they draw on, and what they do about family
 * names.
 *
 * <p>A tradition belongs to a culture, never to a species. It is the difference between a bag of
 * names and a naming system: the same list of given names produces Jon Sigurdsson under one
 * tradition and Jon Ashmaw under another.</p>
 */
public record NamingTradition(ResourceLocation id,
                              List<GivenSource> given,
                              Family family,
                              Order order) {

    public NamingTradition {
        given = given == null ? List.of() : List.copyOf(given);
        family = family == null ? Family.NONE : family;
        order = order == null ? Order.GIVEN_FIRST : order;
    }

    /** One weighted claim on a name list. A tradition may draw on several. */
    public record GivenSource(String list, float rate) {}

    /** Which way round a full name reads. */
    public enum Order {
        GIVEN_FIRST,
        FAMILY_FIRST;

        public static Order byName(String name) {
            if (name == null) return GIVEN_FIRST;
            return "family_first".equals(name.trim().toLowerCase(Locale.ROOT)) ? FAMILY_FIRST : GIVEN_FIRST;
        }
    }

    /** Where a family name comes from, when there is one at all. */
    public enum FamilyType {
        NONE,
        INHERITED,
        PATRONYMIC,
        MATRONYMIC;

        public static @Nullable FamilyType byName(String name) {
            if (name == null || name.isBlank()) return null;
            String key = name.trim().toUpperCase(Locale.ROOT);
            for (FamilyType type : values()) {
                if (type.name().equals(key)) return type;
            }
            return null;
        }

        /** Whether this type builds the name from a parent's given name rather than a pool. */
        public boolean isDerived() {
            return this == PATRONYMIC || this == MATRONYMIC;
        }
    }

    /** Which parent an inherited family name follows. */
    public enum Descent {
        FATHER,
        MOTHER,
        EITHER;

        public static Descent byName(String name) {
            if (name == null || name.isBlank()) return EITHER;
            String key = name.trim().toUpperCase(Locale.ROOT);
            for (Descent descent : values()) {
                if (descent.name().equals(key)) return descent;
            }
            return EITHER;
        }
    }

    /**
     * What wraps a parent's given name to make a derived family name. One mechanism covers
     * {@code -sson}/{@code -sdottir}, {@code -ovich}/{@code -ovna}, {@code -ski}/{@code -ska} and
     * {@code ibn}/{@code bint}, because they differ only in which side the affix sits and how it
     * varies by gender.
     */
    public record Affix(String prefix, String suffix) {
        public static final Affix NONE = new Affix("", "");

        public Affix {
            prefix = prefix == null ? "" : prefix;
            suffix = suffix == null ? "" : suffix;
        }

        public String apply(String stem) {
            return prefix + stem + suffix;
        }

        public boolean isEmpty() {
            return prefix.isEmpty() && suffix.isEmpty();
        }
    }

    /**
     * The family-name rule. {@code list} supplies names for {@link FamilyType#INHERITED};
     * {@code affixes} shape the derived types, keyed by the child's own gender.
     */
    public record Family(FamilyType type, String list, Descent descent, Map<Gender, Affix> affixes) {

        public static final Family NONE = new Family(FamilyType.NONE, "", Descent.EITHER, Map.of());

        public Family {
            type = type == null ? FamilyType.NONE : type;
            list = list == null ? "" : list;
            descent = descent == null ? Descent.EITHER : descent;
            affixes = affixes == null ? Map.of() : Map.copyOf(affixes);
        }

        /** The affix for a gender, falling back through the binary form to nothing at all. */
        public Affix affix(Gender gender) {
            Gender resolved = gender == null ? Gender.MALE : gender;
            Affix exact = affixes.get(resolved);
            if (exact != null) return exact;
            Affix binary = affixes.get(resolved.binary());
            return binary != null ? binary : Affix.NONE;
        }
    }
}
