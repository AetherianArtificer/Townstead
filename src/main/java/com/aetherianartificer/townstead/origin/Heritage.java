package com.aetherianartificer.townstead.origin;

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
 * <p>This is the "23andMe" identity that drives the displayed race name via the
 * {@link HeritageRegistry}; the {@link Origin} a founder is seeded from only sets
 * the initial vector.</p>
 */
public record Heritage(Map<ResourceLocation, Float> fractions) {

    public static final Heritage EMPTY = new Heritage(Map.of());

    /** Fractions below this are treated as absent (drops float drift to zero). */
    private static final float EPSILON = 0.001f;

    public Heritage {
        fractions = normalize(fractions);
    }

    public static Heritage pure(@Nullable ResourceLocation ancestry) {
        if (ancestry == null) return EMPTY;
        return new Heritage(Map.of(ancestry, 1.0f));
    }

    /** An even blend of two parents (the child of M and F is ½M + ½F). */
    public static Heritage blend(Heritage mother, Heritage father) {
        Map<ResourceLocation, Float> sum = new LinkedHashMap<>();
        if (mother != null) mother.fractions.forEach((k, v) -> sum.merge(k, v * 0.5f, Float::sum));
        if (father != null) father.fractions.forEach((k, v) -> sum.merge(k, v * 0.5f, Float::sum));
        return new Heritage(sum);
    }

    public boolean isEmpty() {
        return fractions.isEmpty();
    }

    public float fractionOf(ResourceLocation ancestry) {
        return fractions.getOrDefault(ancestry, 0f);
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
        return tag;
    }

    public static Heritage fromTag(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return EMPTY;
        Map<ResourceLocation, Float> out = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) out.put(id, tag.getFloat(key));
        }
        return new Heritage(out);
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
