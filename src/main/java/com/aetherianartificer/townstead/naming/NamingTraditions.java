package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.Cultures;
import net.conczin.mca.resources.Names;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every loaded naming tradition, plus the implicit one each MCA name bucket gets for free.
 *
 * <p>MCA and MCA Capitals between them ship 63 name buckets. Rather than make an author write 63
 * files to use any of them, every bucket that has no authored tradition gets a synthesized one that
 * draws on that single list and declares no family-name rule. That leaves Capitals' surnames
 * untouched where it owns them, and means the whole existing corpus is addressable on the day this
 * ships.</p>
 */
public final class NamingTraditions {

    /** Implicit ids live under this namespace, so {@code mca:japan} names MCA's japan bucket. */
    public static final String IMPLICIT_NAMESPACE = "mca";

    private static volatile Map<ResourceLocation, NamingTradition> authored = Map.of();

    private NamingTraditions() {}

    public static void replace(Map<ResourceLocation, NamingTradition> traditions) {
        authored = traditions == null ? Map.of() : Map.copyOf(traditions);
    }

    /** Adds traditions written in place on cultures, without clearing the authored files. */
    public static void addInline(Map<ResourceLocation, NamingTradition> traditions) {
        if (traditions == null || traditions.isEmpty()) return;
        java.util.Map<ResourceLocation, NamingTradition> merged = new java.util.LinkedHashMap<>(authored);
        merged.putAll(traditions);
        authored = Map.copyOf(merged);
    }

    /** Ids of authored traditions, not counting the implicit ones. */
    public static Set<ResourceLocation> authoredIds() {
        return authored.keySet();
    }

    /**
     * The traditions a player can pick on their own, in id order. One a culture holds is picked
     * through that culture instead, which keeps subculture and switched-off traditions off the list.
     * An MCA bucket's tradition is always here, since it belongs to no culture even when one uses it.
     */
    public static List<NamingTradition> pickable() {
        java.util.Set<ResourceLocation> ids = new java.util.TreeSet<>(authored.keySet());
        for (String bucket : Names.NAMES_MAP.keySet()) {
            ResourceLocation id = bucket.indexOf(':') < 0 ? ResourceLocation.tryParse(IMPLICIT_NAMESPACE + ":" + bucket) : null;
            if (id != null) ids.add(id);
        }
        ids.removeAll(heldByCultures(cultures()));
        List<NamingTradition> out = new java.util.ArrayList<>();
        for (ResourceLocation id : ids) {
            NamingTradition tradition = get(id);
            if (hasGivenNames(tradition)) out.add(tradition);
        }
        return out;
    }

    /** The cultures a player can pick, in id order: not subcultures, and on in this world. */
    public static List<Culture> pickableCultures() {
        java.util.Set<ResourceLocation> ids = new java.util.TreeSet<>(Cultures.rootIds());
        List<Culture> out = new java.util.ArrayList<>();
        for (ResourceLocation id : ids) {
            Culture culture = Cultures.get(id);
            if (culture != null && culturePickable(culture)) out.add(culture);
        }
        return out;
    }

    /** Whether cultures are on, this one is on, and any tradition it names can supply names. */
    public static boolean culturePickable(Culture culture) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.CULTURES)) return false;
        if (!com.aetherianartificer.townstead.culture.CultureRules.enabled(culture.id().toString())) return false;
        return !culture.hasNamingTradition() || hasGivenNames(get(culture.namingTradition()));
    }

    private static boolean hasGivenNames(@Nullable NamingTradition tradition) {
        return tradition != null && !rollGivenList(tradition, RandomSource.create()).isEmpty();
    }

    private static List<Culture> cultures() {
        List<Culture> out = new java.util.ArrayList<>();
        for (ResourceLocation id : Cultures.authoredIds()) {
            Culture culture = Cultures.get(id);
            if (culture != null) out.add(culture);
        }
        return out;
    }

    static Set<ResourceLocation> heldByCultures(java.util.Collection<Culture> cultures) {
        Set<ResourceLocation> held = new java.util.HashSet<>();
        for (Culture culture : cultures) {
            ResourceLocation tradition = culture.namingTradition();
            if (tradition != null && !IMPLICIT_NAMESPACE.equals(tradition.getNamespace())) held.add(tradition);
        }
        return held;
    }

    /**
     * A tradition by id. An authored one wins; otherwise an {@code mca:<bucket>} id resolves to the
     * implicit tradition for that bucket when the bucket is loaded. Null when nothing matches.
     */
    public static @Nullable NamingTradition get(@Nullable ResourceLocation id) {
        if (id == null) return null;
        NamingTradition tradition = authored.get(id);
        if (tradition != null) return tradition;
        return implicit(id);
    }

    /**
     * The synthesized tradition for an MCA bucket: draw given names from that one list, and leave
     * family names alone so whatever already owns them keeps owning them.
     */
    private static @Nullable NamingTradition implicit(ResourceLocation id) {
        if (!IMPLICIT_NAMESPACE.equals(id.getNamespace())) return null;
        String bucket = id.getPath();
        if (!Names.NAMES_MAP.containsKey(bucket)) return null;
        return new NamingTradition(
                id,
                List.of(NamingTradition.SourceGroup.of(id.toString())),
                NamingTradition.Family.NONE,
                NamingTradition.Order.GIVEN_FIRST);
    }

    /**
     * Rolls one of a family rule's surname lists, weighted, skipping any that supplies no family
     * names. Empty when none of them do, which is a culture referencing something absent rather
     * than a culture without surnames.
     */
    public static String rollFamilyList(NamingTradition.Family family, RandomSource random) {
        if (family == null) return "";
        return roll(family.lists(), random, NameLists::hasFamily);
    }

    /**
     * Rolls one of a tradition's given-name lists. The result is recorded on the villager rather
     * than re-rolled, so a person belongs to one naming tradition for life even when their culture
     * draws on several.
     */
    public static String rollGivenList(NamingTradition tradition, RandomSource random) {
        if (tradition == null) return "";
        return roll(tradition.given(), random, NameLists::hasGiven);
    }

    /**
     * The first group that can be drawn from, then a weighted pick inside it.
     *
     * <p>Groups are a second chance, not a blend: a group whose requirement is not met is passed
     * over whole rather than quietly shrinking to whatever part of it happens to be installed.</p>
     */
    private static String roll(List<NamingTradition.SourceGroup> groups, RandomSource random,
                               java.util.function.Predicate<String> usable) {
        for (NamingTradition.SourceGroup group : groups) {
            if (!group.satisfied(usable)) continue;
            String picked = within(group.from(), random, usable);
            if (!picked.isEmpty()) return picked;
        }
        return "";
    }

    /** Weighted pick among the sources in one group that can actually supply what is asked for. */
    private static String within(List<NamingTradition.GivenSource> sources, RandomSource random,
                                 java.util.function.Predicate<String> usable) {
        float total = 0.0F;
        for (NamingTradition.GivenSource source : sources) {
            if (source.rate() > 0 && usable.test(source.list())) total += source.rate();
        }
        if (total <= 0.0F) return "";

        float roll = random.nextFloat() * total;
        for (NamingTradition.GivenSource source : sources) {
            if (source.rate() <= 0 || !usable.test(source.list())) continue;
            roll -= source.rate();
            if (roll <= 0.0F) return source.list();
        }
        for (NamingTradition.GivenSource source : sources) {
            if (usable.test(source.list())) return source.list();
        }
        return "";
    }
}
