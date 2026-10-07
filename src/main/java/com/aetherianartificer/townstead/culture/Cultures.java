package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.naming.NamingTraditions;
import net.conczin.mca.resources.Names;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every culture in the world: the ones packs author, and one for each MCA name bucket.
 *
 * <p>MCA and MCA Capitals ship 63 name buckets between them. Every bucket without an authored
 * culture gets an implicit one, {@code mca:japan} for MCA's japan bucket, so the whole existing
 * corpus is addressable from day one and nobody writes 63 files to reference names that already
 * exist. An implicit culture has a naming tradition and nothing else, which is honest: MCA's
 * buckets are naming traditions, and were never cultures in the sense this system means.</p>
 *
 * <p>An authored culture with the same id replaces the implicit one, so a pack can give
 * {@code mca:japan} real values later without anything else changing.</p>
 */
public final class Cultures {

    /** Implicit cultures live under this namespace, matching their naming traditions. */
    public static final String IMPLICIT_NAMESPACE = NamingTraditions.IMPLICIT_NAMESPACE;

    /** Reference meaning "any loaded culture", for a spawn bias that should stay open-ended. */
    public static final String ANY = "any";

    private static volatile Map<ResourceLocation, Culture> authored = Map.of();
    private static volatile Map<ResourceLocation, ResourceLocation> legacyIds = Map.of();

    private Cultures() {}

    public static void replace(Map<ResourceLocation, Culture> cultures) {
        replace(cultures, Map.of());
    }

    public static void replace(Map<ResourceLocation, Culture> cultures,
                               Map<ResourceLocation, ResourceLocation> aliases) {
        authored = cultures == null ? Map.of() : Map.copyOf(cultures);
        legacyIds = aliases == null ? Map.of() : Map.copyOf(aliases);
    }

    /**
     * A culture by id, or null. Authored only: an MCA name bucket is a naming tradition and never a
     * culture, because "named in the Japanese manner" says nothing about what anybody values, and a
     * culture is where beliefs will live.
     */
    public static @Nullable Culture get(@Nullable ResourceLocation id) {
        if (id == null) return null;
        Culture direct = authored.get(id);
        if (direct != null) return direct;
        ResourceLocation canonical = legacyIds.get(id);
        return canonical == null ? null : authored.get(canonical);
    }

    /** Canonical id for a live or legacy culture reference, or the input when unresolved. */
    public static @Nullable ResourceLocation canonicalId(@Nullable ResourceLocation id) {
        if (id == null || authored.containsKey(id)) return id;
        return legacyIds.getOrDefault(id, id);
    }

    public static @Nullable Culture get(@Nullable String id) {
        return id == null || id.isBlank() ? null : get(ResourceLocation.tryParse(id.trim()));
    }

    public static boolean exists(@Nullable String id) {
        return get(id) != null;
    }

    /** Ids of authored cultures only. */
    public static Set<ResourceLocation> authoredIds() {
        return authored.keySet();
    }

    /** Every culture that can currently be assigned. */
    public static Set<ResourceLocation> allIds() {
        return new LinkedHashSet<>(authored.keySet());
    }

    /** Cultures that are not subcultures: the identities players see and the Switchboard lists. */
    public static Set<ResourceLocation> rootIds() {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (Culture culture : authored.values()) if (!culture.isSubculture()) out.add(culture.id());
        return out;
    }

    /** The culture players see for this one: itself, or the root its subculture belongs to. */
    public static @Nullable ResourceLocation rootOf(@Nullable ResourceLocation id) {
        Culture culture = get(id);
        for (int depth = 0; culture != null && culture.isSubculture() && depth < 16; depth++) {
            Culture parent = authored.get(culture.parent());
            if (parent == null) break;
            culture = parent;
        }
        return culture == null ? null : culture.id();
    }

    public static String rootOf(@Nullable String id) {
        ResourceLocation root = id == null || id.isBlank() ? null : rootOf(ResourceLocation.tryParse(id.trim()));
        return root == null ? (id == null ? "" : id) : root.toString();
    }

    /** Whether {@code id} is {@code ancestor} or one of its subcultures, at any depth. */
    public static boolean within(@Nullable ResourceLocation id, @Nullable ResourceLocation ancestor) {
        if (id == null || ancestor == null) return false;
        ResourceLocation target = canonicalId(ancestor);
        Culture culture = get(id);
        for (int depth = 0; culture != null && depth < 16; depth++) {
            if (culture.id().equals(target)) return true;
            culture = culture.parent() == null ? null : authored.get(culture.parent());
        }
        return false;
    }

    /** Whether a culture reference in data matches {@code id}: the same culture, or an ancestor of it. */
    public static boolean matches(@Nullable String id, Collection<?> references) {
        if (id == null || id.isBlank() || references.isEmpty()) return false;
        ResourceLocation self = ResourceLocation.tryParse(id.trim());
        for (Object reference : references) {
            ResourceLocation ancestor = reference instanceof ResourceLocation r ? r : ResourceLocation.tryParse(String.valueOf(reference));
            if (within(self, ancestor)) return true;
        }
        return false;
    }

    /** The subcultures directly or indirectly under {@code root}. */
    public static List<Culture> subculturesOf(@Nullable ResourceLocation root) {
        List<Culture> out = new ArrayList<>();
        if (root == null) return out;
        for (Culture culture : authored.values()) {
            if (culture.isSubculture() && !culture.id().equals(root) && within(culture.id(), root)) out.add(culture);
        }
        return out;
    }
}
