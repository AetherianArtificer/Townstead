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
                              List<SourceGroup> given,
                              Family family,
                              Order order) {

    public NamingTradition {
        given = given == null ? List.of() : List.copyOf(given);
        family = family == null ? Family.NONE : family;
        order = order == null ? Order.GIVEN_FIRST : order;
    }

    /** One weighted claim on a name list. A tradition may draw on several. */
    public record GivenSource(String list, float rate) {}

    /**
     * A set of sources tried together, and the second chance when they cannot be.
     *
     * <p>Groups are tried in order and the first usable one is drawn from, which is what lets a
     * culture say "blend my names with the elven ones, and if that mod is not installed use mine
     * alone" rather than silently becoming a differently weighted blend. Within a group the pick is
     * weighted as always.</p>
     *
     * <p>{@link Requirement#ANY} is the default and keeps a group forgiving: whatever resolves is
     * used and the rest drop out. {@link Requirement#ALL} makes a group all-or-nothing, for the case
     * where a partial blend is not the thing the author asked for.</p>
     */
    public record SourceGroup(List<GivenSource> from, Requirement require) {

        public enum Requirement { ANY, ALL }

        public SourceGroup {
            from = from == null ? List.of() : List.copyOf(from);
            require = require == null ? Requirement.ANY : require;
        }

        /** A group of one, which is how a bare reference is read. */
        public static SourceGroup of(String list) {
            return new SourceGroup(List.of(new GivenSource(list, 1.0F)), Requirement.ANY);
        }

        /** Whether this group may be drawn from, given what currently resolves. */
        public boolean satisfied(java.util.function.Predicate<String> usable) {
            if (from.isEmpty()) return false;
            for (GivenSource source : from) {
                boolean ok = source.rate() > 0 && usable.test(source.list());
                if (require == Requirement.ALL) {
                    if (!ok) return false;
                } else if (ok) {
                    return true;
                }
            }
            return require == Requirement.ALL;
        }
    }

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
     * The family-name rule. {@code lists} supplies names for {@link FamilyType#INHERITED};
     * {@code affixes} shape the derived types, keyed by the child's own gender.
     *
     * <p>Several weighted lists rather than one, so a culture can be a place people arrived at from
     * more than one direction. A port town whose surnames are mostly local and sometimes foreign is
     * two entries with different rates, and the given names stay whatever that culture speaks.</p>
     */
    public record Family(FamilyType type, List<SourceGroup> lists, Descent descent,
                         Map<Gender, Affix> affixes) {

        public static final Family NONE =
                new Family(FamilyType.NONE, List.<SourceGroup>of(), Descent.EITHER, Map.of());

        public Family {
            type = type == null ? FamilyType.NONE : type;
            lists = lists == null ? List.of() : List.copyOf(lists);
            descent = descent == null ? Descent.EITHER : descent;
            affixes = affixes == null ? Map.of() : Map.copyOf(affixes);
        }

        /** Convenience for the ordinary single-list case. */
        public Family(FamilyType type, String list, Descent descent, Map<Gender, Affix> affixes) {
            this(type,
                 list == null || list.isBlank()
                         ? List.<SourceGroup>of()
                         : List.of(SourceGroup.of(list.trim())),
                 descent, affixes);
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
