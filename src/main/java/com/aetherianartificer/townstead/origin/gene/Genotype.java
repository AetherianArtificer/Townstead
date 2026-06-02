package com.aetherianartificer.townstead.origin.gene;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A villager's diploid set of discrete genes: each locus holds two {@link Allele}s
 * (one from each parent on a bred villager; two rolls from the origin on a
 * founder). Continuous body-metric genes are not stored here; they live on MCA's
 * float genetics. Expression (which allele shows) and inheritance are computed by
 * {@link Heredity}; this is the plain carried genotype, persisted with the villager.
 */
public final class Genotype {

    private final Map<ResourceLocation, Allele[]> loci = new LinkedHashMap<>();

    public Genotype() {}

    public boolean isEmpty() {
        return loci.isEmpty();
    }

    public boolean has(ResourceLocation locus) {
        return locus != null && loci.containsKey(locus);
    }

    /** The two alleles at a locus, or null when the locus is absent. */
    public Allele[] at(ResourceLocation locus) {
        Allele[] pair = loci.get(locus);
        return pair == null ? null : new Allele[]{pair[0], pair[1]};
    }

    public void set(ResourceLocation locus, Allele a, Allele b) {
        if (locus == null) return;
        loci.put(locus, new Allele[]{a == null ? Allele.WILD : a, b == null ? Allele.WILD : b});
    }

    public List<ResourceLocation> loci() {
        return new ArrayList<>(loci.keySet());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        loci.forEach((locus, pair) -> tag.putString(locus.toString(), pair[0].encode() + ";" + pair[1].encode()));
        return tag;
    }

    public static Genotype fromTag(CompoundTag tag) {
        Genotype out = new Genotype();
        if (tag == null) return out;
        for (String key : tag.getAllKeys()) {
            ResourceLocation locus = ResourceLocation.tryParse(key);
            if (locus == null) continue;
            String raw = tag.getString(key);
            int sep = raw.indexOf(';');
            if (sep < 0) {
                Allele only = Allele.decode(raw);
                out.set(locus, only, only);
            } else {
                out.set(locus, Allele.decode(raw.substring(0, sep)), Allele.decode(raw.substring(sep + 1)));
            }
        }
        return out;
    }

    public Genotype copy() {
        Genotype out = new Genotype();
        loci.forEach((locus, pair) -> out.set(locus, pair[0], pair[1]));
        return out;
    }
}
