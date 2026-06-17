package com.aetherianartificer.townstead.origin.personality;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.Origin;
import com.aetherianartificer.townstead.origin.OriginRegistry;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves which personality a villager of a given origin may roll, and rolls one. Walks the origin
 * tree most-specific first (origin, lineage, ancestry, species): the nearest node with a non-empty
 * {@link Personalities} policy defines the pool; {@code inherit:true} merges the parent's beneath it
 * (more-specific weights win), and every processed node's {@code deny} subtracts. No policy anywhere
 * yields an empty pool, i.e. vanilla MCA personality assignment.
 */
public final class PersonalityResolver {

    private PersonalityResolver() {}

    /** Effective {@code ref -> weight} pool for an origin id (empty = let MCA roll a vanilla personality). */
    public static Map<String, Integer> pool(ResourceLocation originId) {
        Map<String, Integer> allow = new LinkedHashMap<>();
        Set<String> deny = new HashSet<>();
        Origin origin = OriginRegistry.byId(originId);

        for (int level = 0; level < 4; level++) {
            Personalities policy = switch (level) {
                case 0 -> PersonalityPolicyRegistry.origin(originId);
                case 1 -> origin == null ? Personalities.EMPTY : PersonalityPolicyRegistry.lineage(origin.lineage());
                case 2 -> origin == null ? Personalities.EMPTY : PersonalityPolicyRegistry.ancestry(origin.ancestry());
                default -> PersonalityPolicyRegistry.species(OriginRegistry.effectiveSpecies(originId));
            };
            if (policy.isEmpty()) continue;
            policy.allow().forEach(allow::putIfAbsent);   // more-specific weights already placed win
            deny.addAll(policy.deny());
            if (!policy.inherit()) break;                 // nearest defining node is final unless it inherits
        }
        deny.forEach(allow::remove);
        return allow;
    }

    /** Weighted-random personality ref for this origin, or {@code null} when the pool is empty. */
    @Nullable
    public static String roll(ResourceLocation originId, RandomSource random) {
        Map<String, Integer> pool = pool(originId);
        int total = 0;
        for (int w : pool.values()) total += Math.max(0, w);
        if (total <= 0) return null;
        int r = random.nextInt(total);
        for (Map.Entry<String, Integer> e : pool.entrySet()) {
            r -= Math.max(0, e.getValue());
            if (r < 0) return e.getKey();
        }
        return null;
    }

    /**
     * The ordered pool of personality refs an origin may roll. Empty when the origin has no policy.
     * For the editor's dynamic picker (the order is the resolved allow order).
     */
    public static java.util.List<String> poolRefs(ResourceLocation originId) {
        return new java.util.ArrayList<>(pool(originId).keySet());
    }

    /** The custom definition for a ref, or {@code null} if the ref is a bare base-enum name (or unknown). */
    @Nullable
    public static PersonalityDef def(@Nullable String ref) {
        if (ref == null || ref.isBlank()) return null;
        return PersonalityRegistry.byId(DataPackLang.parseId(ref));
    }

    /** The base MCA personality a ref maps to: a custom def's {@code extends}, or the ref itself if it names a base enum. */
    @Nullable
    public static Personality baseOf(@Nullable String ref) {
        if (ref == null || ref.isBlank()) return null;
        PersonalityDef def = def(ref);
        return enumByName(def != null ? def.base() : ref);
    }

    @Nullable
    private static Personality enumByName(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Personality.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
