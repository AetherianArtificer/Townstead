package com.aetherianartificer.townstead.root.personality;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat;
import com.aetherianartificer.townstead.root.Lineage;
import com.aetherianartificer.townstead.root.LineageRegistry;
import com.aetherianartificer.townstead.root.Root;
import com.aetherianartificer.townstead.root.RootRegistry;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Resolves which personality a villager of a given root may roll, and rolls one. Walks the identity
 * tree most-specific first (lineage, ancestry, species): the nearest node with a non-empty
 * {@link Personalities} policy defines the pool; {@code inherit:true} merges the parent's beneath it
 * (more-specific weights win), and every processed node's {@code deny} subtracts. No policy anywhere
 * yields an empty pool, i.e. vanilla MCA personality assignment.
 */
public final class PersonalityResolver {

    private PersonalityResolver() {}

    /** Effective {@code ref -> weight} pool for an origin id (empty = let MCA roll a vanilla personality). */
    public static Map<String, Integer> pool(ResourceLocation rootId) {
        Map<String, Integer> allow = new LinkedHashMap<>();
        Set<String> deny = new HashSet<>();
        Root origin = RootRegistry.byId(rootId);
        ResourceLocation lineageId = origin == null ? null : origin.lineage();
        ResourceLocation ancestryId = origin == null ? null : origin.ancestry();
        if (ancestryId == null && lineageId != null) {
            Lineage lineage = LineageRegistry.byId(lineageId);
            if (lineage != null) ancestryId = lineage.ancestry();
        }

        for (int level = 0; level < 3; level++) {
            Personalities policy = switch (level) {
                case 0 -> PersonalityPolicyRegistry.lineage(lineageId);
                case 1 -> PersonalityPolicyRegistry.ancestry(ancestryId);
                default -> PersonalityPolicyRegistry.species(RootRegistry.effectiveSpecies(rootId));
            };
            if (policy.isEmpty()) continue;
            policy.allow().forEach(allow::putIfAbsent);   // more-specific weights already placed win
            deny.addAll(policy.deny());
            if (!policy.inherit()) break;                 // nearest defining node is final unless it inherits
        }
        deny.forEach(allow::remove);
        // Policy files are datapack input. Never let an unknown custom id/base reach the
        // editor, spawn assignment, or MCA compatibility boundary.
        allow.entrySet().removeIf(entry -> baseOf(entry.getKey()) == null);
        return allow;
    }

    /** Weighted-random personality ref for this origin, or {@code null} when the pool is empty. */
    @Nullable
    public static String roll(ResourceLocation rootId, RandomSource random) {
        Map<String, Integer> pool = pool(rootId);
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
    public static java.util.List<String> poolRefs(ResourceLocation rootId) {
        return new java.util.ArrayList<>(pool(rootId).keySet());
    }

    /** The custom definition for a ref, or {@code null} if the ref directly names an MCA personality (or is unknown). */
    @Nullable
    public static PersonalityDef def(@Nullable String ref) {
        if (ref == null || ref.isBlank()) return null;
        return PersonalityRegistry.byId(DataPackLang.parseId(ref));
    }

    /** The MCA personality a ref maps to: a custom def's {@code extends}, or the ref itself. */
    @Nullable
    public static Personality baseOf(@Nullable String ref) {
        if (ref == null || ref.isBlank()) return null;
        PersonalityDef def = def(ref);
        return McaPersonalityCompat.resolve(def != null ? def.base() : ref).orElse(null);
    }
}
