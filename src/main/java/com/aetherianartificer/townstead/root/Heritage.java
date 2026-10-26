package com.aetherianartificer.townstead.root;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A villager's realized ancestry composition: a normalized vector of ancestry id
 * to fraction (summing to 1). A pure-blood is a single ancestry at 1.0; a child
 * blends its parents (½/½), so a human × dwarf child is {human:.5, dwarf:.5} and
 * its child with a human is {human:.75, dwarf:.25}.
 *
 * <p>Alongside it runs the lineage composition: lineage id to the share of the
 * villager descended from that lineage, so a Fire Drake × Ice Drake child is
 * {draconic:1} by ancestry and {fire_drake:.5, ice_drake:.5} by lineage, which a
 * heritage profile can name (a Steam Drake). Lineage shares blend ½/½ like
 * ancestry but are not normalized: forebears seeded without a lineage simply
 * contribute none, so the shares can sum below 1.</p>
 *
 * <p>This is the "23andMe" identity that drives the displayed race name via the
 * {@link HeritageRegistry}; the {@link Root} a founder is seeded from only sets
 * the initial vector.</p>
 */
public record Heritage(Map<ResourceLocation, Float> fractions, Map<ResourceLocation, Float> lineages) {

    public static final Heritage EMPTY = new Heritage(Map.of(), Map.of());

    /** Fractions below this are treated as absent (drops float drift to zero). */
    private static final float EPSILON = 0.001f;

    /** NBT key of the lineage shares; '#' can't start an id, so ancestry keys never collide with it. */
    private static final String LINEAGES_TAG = "#lineages";

    public Heritage {
        fractions = normalize(fractions);
        lineages = clean(lineages);
    }

    /** An ancestry-only composition (no lineage shares). */
    public Heritage(Map<ResourceLocation, Float> fractions) {
        this(fractions, Map.of());
    }

    public static Heritage pure(@Nullable ResourceLocation ancestry) {
        return pure(ancestry, null);
    }

    /** A purebred of {@code ancestry}, descended wholly from {@code lineage} when one is given. */
    public static Heritage pure(@Nullable ResourceLocation ancestry, @Nullable ResourceLocation lineage) {
        if (ancestry == null) return EMPTY;
        return new Heritage(Map.of(ancestry, 1.0f), lineage == null ? Map.of() : Map.of(lineage, 1.0f));
    }

    /** An even blend of two parents (the child of M and F is ½M + ½F), by ancestry and by lineage. */
    public static Heritage blend(Heritage mother, Heritage father) {
        Map<ResourceLocation, Float> sum = new LinkedHashMap<>();
        Map<ResourceLocation, Float> lines = new LinkedHashMap<>();
        if (mother != null) {
            mother.fractions.forEach((k, v) -> sum.merge(k, v * 0.5f, Float::sum));
            mother.lineages.forEach((k, v) -> lines.merge(k, v * 0.5f, Float::sum));
        }
        if (father != null) {
            father.fractions.forEach((k, v) -> sum.merge(k, v * 0.5f, Float::sum));
            father.lineages.forEach((k, v) -> lines.merge(k, v * 0.5f, Float::sum));
        }
        return new Heritage(sum, lines);
    }

    /**
     * This heritage with {@code seed}'s lineage shares filled in, when it carries none and is the
     * same pure ancestry the seed is. Heals saves from before lineage shares existed; a mixed or
     * already-lineaged heritage is returned unchanged, since its lineages can't be reconstructed.
     */
    public Heritage withLineagesFrom(Heritage seed) {
        if (!lineages.isEmpty() || seed == null || seed.lineages.isEmpty()) return this;
        ResourceLocation ancestry = dominant();
        if (ancestry == null || fractionOf(ancestry) < 0.999f || !ancestry.equals(seed.dominant())) return this;
        return new Heritage(fractions, seed.lineages);
    }

    public boolean isEmpty() {
        return fractions.isEmpty();
    }

    public float fractionOf(ResourceLocation ancestry) {
        return fractions.getOrDefault(ancestry, 0f);
    }

    /** The share of this villager descended from {@code lineage} (0 when none). */
    public float lineageFractionOf(ResourceLocation lineage) {
        return lineages.getOrDefault(lineage, 0f);
    }

    /** Lineages by descending share. */
    public List<ResourceLocation> rankedLineages() {
        return new ArrayList<>(lineages.keySet());   // clean() keeps them sorted
    }

    /** The largest-share ancestry, or null when empty. */
    @Nullable
    public ResourceLocation dominant() {
        ResourceLocation best = null;
        float bestFraction = -1f;
        for (Map.Entry<ResourceLocation, Float> e : fractions.entrySet()) {
            if (e.getValue() > bestFraction) {
                bestFraction = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /** Ancestries by descending share (stable for ties, following insertion order). */
    public List<ResourceLocation> ranked() {
        List<Map.Entry<ResourceLocation, Float>> entries = new ArrayList<>(fractions.entrySet());
        entries.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        List<ResourceLocation> out = new ArrayList<>(entries.size());
        for (Map.Entry<ResourceLocation, Float> e : entries) out.add(e.getKey());
        return out;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        fractions.forEach((k, v) -> tag.putFloat(k.toString(), v));
        if (!lineages.isEmpty()) {
            CompoundTag lines = new CompoundTag();
            lineages.forEach((k, v) -> lines.putFloat(k.toString(), v));
            tag.put(LINEAGES_TAG, lines);
        }
        return tag;
    }

    public static Heritage fromTag(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return EMPTY;
        Map<ResourceLocation, Float> out = new LinkedHashMap<>();
        Map<ResourceLocation, Float> lines = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            if (key.equals(LINEAGES_TAG)) {
                CompoundTag nested = tag.getCompound(key);
                for (String lineage : nested.getAllKeys()) {
                    ResourceLocation id = ResourceLocation.tryParse(lineage);
                    if (id != null) lines.put(id, nested.getFloat(lineage));
                }
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) out.put(id, tag.getFloat(key));
        }
        return new Heritage(out, lines);
    }

    /** Lineage shares: drift dropped, each capped at 1, sorted by descending share; not normalized. */
    private static Map<ResourceLocation, Float> clean(Map<ResourceLocation, Float> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        List<Map.Entry<ResourceLocation, Float>> entries = new ArrayList<>(raw.entrySet());
        entries.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        Map<ResourceLocation, Float> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Float> e : entries) {
            if (e.getValue() >= EPSILON) out.put(e.getKey(), Math.min(1f, e.getValue()));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private static Map<ResourceLocation, Float> normalize(Map<ResourceLocation, Float> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        float total = 0f;
        for (float v : raw.values()) if (v > 0f) total += v;
        if (total <= 0f) return Map.of();
        // Descending order so dominant()/ranked() and display are stable.
        List<Map.Entry<ResourceLocation, Float>> entries = new ArrayList<>(raw.entrySet());
        entries.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        Map<ResourceLocation, Float> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Float> e : entries) {
            float share = e.getValue() / total;
            if (share >= EPSILON) out.put(e.getKey(), share);
        }
        return Map.copyOf(out);
    }
}
