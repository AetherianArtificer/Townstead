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

    /**
     * A culture by id, or null. Authored only: an MCA name bucket is a naming tradition and never a
     * culture, because "named in the Japanese manner" says nothing about what anybody values, and a
     * culture is where beliefs will live.
     */
    public static @Nullable Culture get(@Nullable ResourceLocation id) {
        return id == null ? null : authored.get(id);
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
}
