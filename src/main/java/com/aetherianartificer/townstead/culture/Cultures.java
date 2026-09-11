package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.naming.NamingTraditions;
import net.conczin.mca.resources.Names;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
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

    private Cultures() {}

    public static void replace(Map<ResourceLocation, Culture> cultures) {
        authored = cultures == null ? Map.of() : Map.copyOf(cultures);
    }

    /** An authored culture wins; otherwise the implicit culture for a loaded MCA bucket. */
    public static @Nullable Culture get(@Nullable ResourceLocation id) {
        if (id == null) return null;
        Culture culture = authored.get(id);
        if (culture != null) return culture;
        return implicit(id);
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

    /** Every culture that can currently be assigned, authored and implicit together. */
    public static Set<ResourceLocation> allIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>(authored.keySet());
        for (String bucket : Names.NAMES_MAP.keySet()) {
            ResourceLocation id = implicitId(bucket);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    /**
     * The culture a name bucket implies, or null when the bucket is not loaded. Townstead's own
     * name lists are namespaced and get no implicit culture: a pack that authors names is expected
     * to author the culture that uses them.
     */
    private static @Nullable Culture implicit(ResourceLocation id) {
        if (!IMPLICIT_NAMESPACE.equals(id.getNamespace())) return null;
        if (!Names.NAMES_MAP.containsKey(id.getPath())) return null;
        return new Culture(
                id,
                Component.translatable("townstead.culture." + IMPLICIT_NAMESPACE + "." + id.getPath()),
                id);
    }

    private static @Nullable ResourceLocation implicitId(String bucket) {
        if (bucket == null || bucket.isBlank() || bucket.indexOf(':') >= 0) return null;
        return ResourceLocation.tryParse(IMPLICIT_NAMESPACE + ":" + bucket);
    }
}
