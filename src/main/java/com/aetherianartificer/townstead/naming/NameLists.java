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
 * Every pool of names Townstead can draw on, whoever authored it.
 *
 * <p>Given names and family names are held in separate registers, because they are separate
 * questions. A tradition's {@code given} can only reach given names and a family rule's
 * {@code list} can only reach family names, so a surname pool can never be handed to something
 * asking for first names. The same id may appear in both: {@code townstead_classic:highhold} is
 * that people's given names and that people's surnames, one people described in two files.</p>
 *
 * <p>Every reference is namespaced, and the namespace says who supplies it:</p>
 * <ul>
 *   <li>{@code mca:elven} — a bucket in MCA's name map. <b>Given names only</b>: MCA ships no
 *       surnames at all, and it makes no distinction between the buckets MCA ships and the ones
 *       MCA Capitals adds, because Capitals puts those in MCA's own namespace</li>
 *   <li>{@code townstead_mobs:piglin} — a Townstead {@code given_name} or {@code family_name} file</li>
 *   <li>anything else — a provider registered for that namespace, which is how another mod's names
 *       reach a tradition without the schema knowing the mod exists</li>
 * </ul>
 *
 * <p>Townstead's given names are installed into {@link Names#NAMES_MAP} so MCA can pick from them,
 * and deliberately never into {@link Names#REGION_NAMES}. That second list is what MCA indexes into
 * by map position, and every entry added to it re-rolls the naming of every region in the world.
 * Staying out of it is what makes a new list safe to add to a running save. Family names are never
 * installed anywhere: MCA has no concept of one.</p>
 */
public final class NameLists {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(
            com.aetherianartificer.townstead.Townstead.MOD_ID + "/NameLists");

    private static volatile Map<ResourceLocation, GivenNames> given = Map.of();
    private static volatile Map<ResourceLocation, FamilyNames> family = Map.of();
    /** Given names written inline on a tradition or culture rather than in their own file. */
    private static volatile Map<ResourceLocation, GivenNames> inlineGiven = Map.of();
    /** Family names written inline on a family rule. */
    private static volatile Map<ResourceLocation, FamilyNames> inlineFamily = Map.of();

    private static final Map<String, GivenNameProvider> GIVEN_PROVIDERS = new ConcurrentHashMap<>();
    private static final Map<String, FamilyNameProvider> FAMILY_PROVIDERS = new ConcurrentHashMap<>();

    private NameLists() {}

    /**
     * Registers who resolves given-name references in a namespace. Call once at setup; a provider
     * backed by another mod gates itself on that mod being present rather than being gated here.
     */
    public static void addGivenProvider(String namespace, GivenNameProvider provider) {
        if (namespace == null || namespace.isBlank() || provider == null) return;
        GIVEN_PROVIDERS.put(namespace, provider);
    }

    /** Registers who resolves family-name references in a namespace. */
    public static void addFamilyProvider(String namespace, FamilyNameProvider provider) {
        if (namespace == null || namespace.isBlank() || provider == null) return;
        FAMILY_PROVIDERS.put(namespace, provider);
    }

    /** Replaces the given names loaded from {@code given_name} files. */
    public static void replaceGiven(Map<ResourceLocation, GivenNames> lists) {
        given = lists == null ? Map.of() : Map.copyOf(lists);
        install();
    }

    /** Replaces the family names loaded from {@code family_name} files. */
    public static void replaceFamily(Map<ResourceLocation, FamilyNames> lists) {
        family = lists == null ? Map.of() : Map.copyOf(lists);
    }

    /**
     * Replaces the given names written inline on traditions. Kept apart from the file-loaded ones so
     * the loaders can run in either order without clearing each other's work.
     */
    public static void replaceInlineGiven(Map<ResourceLocation, GivenNames> lists) {
        inlineGiven = lists == null ? Map.of() : Map.copyOf(lists);
        install();
    }

    /** Adds inline given names without clearing those another loader contributed. */
    public static void addInlineGiven(Map<ResourceLocation, GivenNames> lists) {
        if (lists == null || lists.isEmpty()) return;
        Map<ResourceLocation, GivenNames> merged = new LinkedHashMap<>(inlineGiven);
        merged.putAll(lists);
        inlineGiven = Map.copyOf(merged);
        install();
    }

    /** Adds inline family names without clearing those another loader contributed. */
    public static void addInlineFamily(Map<ResourceLocation, FamilyNames> lists) {
        if (lists == null || lists.isEmpty()) return;
        Map<ResourceLocation, FamilyNames> merged = new LinkedHashMap<>(inlineFamily);
        merged.putAll(lists);
        inlineFamily = Map.copyOf(merged);
    }

    /**
     * Installs Townstead's given names into MCA's name map, leaving its region list untouched.
     * Idempotent; runs after Townstead's own load and again whenever MCA rebuilds.
     */
    public static void install() {
        install(given);
        install(inlineGiven);
        warnOnShadowedInline();
    }

    /**
     * Warns when a tradition writes given names in place under an id a {@code given_name} file also
     * claims. The file wins, so the names written in place are never used, and the author has no way
     * to tell from the game: both spellings load without error and one of them quietly does nothing.
     */
    private static void warnOnShadowedInline() {
        for (ResourceLocation id : inlineGiven.keySet()) {
            if (given.containsKey(id)) {
                LOGGER.warn("Given names are written in place on {} and also in a given_name file "
                        + "under the same id; the file wins and the names written in place are "
                        + "ignored. Reference the file, or rename one of them.", id);
            }
        }
    }

    private static void install(Map<ResourceLocation, GivenNames> lists) {
        for (GivenNames names : lists.values()) {
            if (!names.isEmpty()) Names.NAMES_MAP.put(names.id().toString(), names.byGender());
        }
    }

    /** Ids of Townstead-authored given-name lists. */
    public static Set<ResourceLocation> givenIds() {
        return given.keySet();
    }

    /** Ids of Townstead-authored family-name lists. */
    public static Set<ResourceLocation> familyIds() {
        return family.keySet();
    }

    /** The given names a reference resolves to, or null when nothing supplies any. */
    public static @Nullable GivenNames resolveGiven(String reference) {
        ResourceLocation id = parse(reference);
        if (id == null) return null;

        if (NamingTraditions.IMPLICIT_NAMESPACE.equals(id.getNamespace())) return fromMcaBucket(id.getPath());

        GivenNames own = given.get(id);
        if (own == null) own = inlineGiven.get(id);
        if (own != null) return own;

        GivenNameProvider provider = GIVEN_PROVIDERS.get(id.getNamespace());
        if (provider == null) return null;
        try {
            return provider.get(id.getPath());
        } catch (Throwable ignored) {
            // A provider's silence is a missing list, never a failed naming.
            return null;
        }
    }

    /**
     * The family names a reference resolves to, or null when nothing supplies any. An {@code mca:}
     * reference always resolves to nothing: MCA has no surnames, so a family rule pointing at one of
     * its buckets is a pack asking for something that does not exist.
     */
    public static @Nullable FamilyNames resolveFamily(String reference) {
        ResourceLocation id = parse(reference);
        if (id == null) return null;
        if (NamingTraditions.IMPLICIT_NAMESPACE.equals(id.getNamespace())) return null;

        FamilyNames own = family.get(id);
        if (own == null) own = inlineFamily.get(id);
        if (own != null) return own;

        FamilyNameProvider provider = FAMILY_PROVIDERS.get(id.getNamespace());
        if (provider == null) return null;
        try {
            return provider.get(id.getPath());
        } catch (Throwable ignored) {
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
        GivenNames resolved = resolveGiven(reference);
        return resolved != null && !resolved.isEmpty() ? key : "";
    }

    /** Whether a reference can supply given names right now. */
    public static boolean hasGiven(String reference) {
        return !givenKey(reference).isEmpty();
    }

    /** Whether a reference can supply family names right now. */
    public static boolean hasFamily(String reference) {
        return resolveFamily(reference) != null;
    }

    /** The family-name pool a reference resolves to, or null when nothing supplies one. */
    public static @Nullable WeightedPool<String> family(String reference) {
        FamilyNames resolved = resolveFamily(reference);
        return resolved == null ? null : resolved.names();
    }

    /** Reads a reference, treating a bare one as {@code mca:} since MCA's keys carry no namespace. */
    private static @Nullable ResourceLocation parse(String reference) {
        if (reference == null || reference.isBlank()) return null;
        String trimmed = reference.trim();
        return ResourceLocation.tryParse(trimmed.indexOf(':') < 0
                ? NamingTraditions.IMPLICIT_NAMESPACE + ":" + trimmed
                : trimmed);
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

    /** Wraps one of MCA's buckets as given names, which is all MCA has. */
    private static @Nullable GivenNames fromMcaBucket(String bucket) {
        Map<Gender, WeightedPool<String>> byGender = Names.NAMES_MAP.get(bucket);
        if (byGender == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(NamingTraditions.IMPLICIT_NAMESPACE + ":" + bucket);
        return id == null ? null : new GivenNames(id, byGender);
    }
}
