package com.aetherianartificer.townstead.naming;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.Names;
import net.conczin.mca.resources.WeightedPool;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every name list Townstead can draw on, whoever authored it.
 *
 * <p>Every reference is namespaced, and the namespace says who supplies it:</p>
 * <ul>
 *   <li>{@code mca:elven} — a bucket in MCA's name map. Given names only, and it makes no
 *       distinction between the buckets MCA ships and the eight MCA Capitals adds, because Capitals
 *       puts those in MCA's own namespace where nothing can tell them apart</li>
 *   <li>{@code townstead_mobs:piglin} — a Townstead {@code name_list} file</li>
 *   <li>anything else — a {@link NameListProvider} registered for that namespace, which is how
 *       another mod's names reach a tradition without the schema knowing the mod exists</li>
 * </ul>
 *
 * <p>A provider reference names no file: the namespace says who resolves it and the path says what,
 * which is why nothing here mentions a specific mod. A bare reference is read as {@code mca:}, since
 * MCA's own keys carry no namespace.</p>
 *
 * <p>Townstead's lists are installed into {@link Names#NAMES_MAP} so MCA can pick names from them,
 * and deliberately never into {@link Names#REGION_NAMES}. That second list is what MCA indexes into
 * by map position, and every entry added to it re-rolls the naming of every region in the world.
 * Staying out of it is what makes a new list safe to add to a running save.</p>
 */
public final class NameLists {

    private static volatile Map<ResourceLocation, NameList> loaded = Map.of();
    /** Lists written inline on a tradition or culture rather than in their own file. */
    private static volatile Map<ResourceLocation, NameList> inline = Map.of();
    private static final Map<String, NameListProvider> PROVIDERS = new ConcurrentHashMap<>();

    private NameLists() {}

    /**
     * Registers who resolves references in a namespace. Call once at setup; a provider backed by
     * another mod gates itself on that mod being present rather than being gated here.
     */
    public static void addProvider(String namespace, NameListProvider provider) {
        if (namespace == null || namespace.isBlank() || provider == null) return;
        PROVIDERS.put(namespace, provider);
    }

    /** Replaces the lists loaded from {@code name_list} files. */
    public static void replace(Map<ResourceLocation, NameList> lists) {
        loaded = lists == null ? Map.of() : Map.copyOf(lists);
        install();
    }

    /**
     * Replaces the lists written inline on traditions and cultures. Kept apart from the file-loaded
     * ones so the two loaders can run in either order without clearing each other's work.
     */
    public static void replaceInline(Map<ResourceLocation, NameList> lists) {
        inline = lists == null ? Map.of() : Map.copyOf(lists);
        install();
    }

    /** Adds more inline lists without clearing those another loader already contributed. */
    public static void addInline(Map<ResourceLocation, NameList> lists) {
        if (lists == null || lists.isEmpty()) return;
        Map<ResourceLocation, NameList> merged = new LinkedHashMap<>(inline);
        merged.putAll(lists);
        inline = Map.copyOf(merged);
        install();
    }

    /**
     * Installs Townstead's given names into MCA's name map, leaving its region list untouched.
     * Idempotent; runs after Townstead's own load and again whenever MCA rebuilds.
     */
    public static void install() {
        install(loaded);
        install(inline);
    }

    private static void install(Map<ResourceLocation, NameList> lists) {
        for (NameList list : lists.values()) {
            if (list.hasGiven()) Names.NAMES_MAP.put(list.id().toString(), list.given());
        }
    }

    /** Ids of Townstead-authored lists. */
    public static Set<ResourceLocation> ids() {
        return loaded.keySet();
    }

    /** Whatever a reference resolves to, or null when nothing supplies it. */
    public static @Nullable NameList resolve(String reference) {
        ResourceLocation id = parse(reference);
        if (id == null) return null;

        if (NamingTraditions.IMPLICIT_NAMESPACE.equals(id.getNamespace())) return fromMcaBucket(id.getPath());

        NameList own = loaded.get(id);
        if (own == null) own = inline.get(id);
        if (own != null) return own;

        NameListProvider provider = PROVIDERS.get(id.getNamespace());
        if (provider == null) return null;
        try {
            return provider.get(id.getPath());
        } catch (Throwable ignored) {
            // A provider's silence is a missing list, never a failed naming.
            return null;
        }
    }

    /**
     * The key MCA's name map holds this reference under, or empty when it supplies no given names.
     * MCA's own buckets are keyed bare, Townstead's by full id, so this is what turns an authored
     * reference into the value {@code Names.getCitizenNation} has to return.
     */
    public static String givenKey(String reference) {
        ResourceLocation id = parse(reference);
        if (id == null) return "";
        String key = NamingTraditions.IMPLICIT_NAMESPACE.equals(id.getNamespace())
                ? id.getPath()
                : id.toString();
        if (Names.NAMES_MAP.containsKey(key)) return key;
        NameList resolved = resolve(reference);
        return resolved != null && resolved.hasGiven() ? key : "";
    }

    /** Whether a reference can supply given names right now. */
    public static boolean hasGiven(String reference) {
        return !givenKey(reference).isEmpty();
    }

    /** Reads a reference, treating a bare one as {@code mca:} since MCA's keys carry no namespace. */
    private static @Nullable ResourceLocation parse(String reference) {
        if (reference == null || reference.isBlank()) return null;
        String trimmed = reference.trim();
        return ResourceLocation.tryParse(trimmed.indexOf(':') < 0
                ? NamingTraditions.IMPLICIT_NAMESPACE + ":" + trimmed
                : trimmed);
    }

    /**
     * Family names for a reference, or null when nothing supplies any. MCA has no concept of a
     * family name, so a bare bucket has none of its own; a provider registered for a namespace may
     * still offer them for one, which is how another mod's surnames reach a tradition.
     */
    public static @Nullable WeightedPool<String> family(String reference) {
        NameList resolved = resolve(reference);
        return resolved == null ? null : resolved.family();
    }

    /** Builds the gender map MCA needs, filling gaps so no binary gender is ever missing. */
    public static Map<Gender, WeightedPool<String>> completeGiven(@Nullable WeightedPool<String> male,
                                                                 @Nullable WeightedPool<String> female,
                                                                 @Nullable WeightedPool<String> neutral) {
        WeightedPool<String> resolvedMale = male != null ? male : (female != null ? female : neutral);
        WeightedPool<String> resolvedFemale = female != null ? female : (male != null ? male : neutral);
        if (resolvedMale == null || resolvedFemale == null) return Map.of();

        Map<Gender, WeightedPool<String>> byGender = new LinkedHashMap<>();
        byGender.put(Gender.MALE, resolvedMale);
        byGender.put(Gender.FEMALE, resolvedFemale);
        byGender.put(Gender.NEUTRAL, neutral != null ? neutral : resolvedMale);
        return byGender;
    }

    /** Wraps one of MCA's buckets as a list: given names only, since MCA has no family names. */
    private static @Nullable NameList fromMcaBucket(String bucket) {
        Map<Gender, WeightedPool<String>> given = Names.NAMES_MAP.get(bucket);
        if (given == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(NamingTraditions.IMPLICIT_NAMESPACE + ":" + bucket);
        return id == null ? null : new NameList(id, given, null);
    }
}
