package com.aetherianartificer.townstead.root.gene;

import com.aetherianartificer.townstead.root.LegacyNamespace;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
        return locus != null && loci.containsKey(LegacyNamespace.canonical(locus));
    }

    /** The two alleles at a locus, or null when the locus is absent. */
    public Allele[] at(ResourceLocation locus) {
        Allele[] pair = locus == null ? null : loci.get(LegacyNamespace.canonical(locus));
        return pair == null ? null : new Allele[]{pair[0], pair[1]};
    }

    /**
     * Locus keys are stored canonical: a legacy-namespace locus (an old save, or a pack still
     * declaring {@code townstead_origins:}) is the same slot as its {@code townstead_roots:} twin,
     * so both must pair — not coexist — when parents mix.
     */
    public void set(ResourceLocation locus, Allele a, Allele b) {
        if (locus == null) return;
        loci.put(LegacyNamespace.canonical(locus),
                new Allele[]{a == null ? Allele.WILD : a, b == null ? Allele.WILD : b});
    }

    /** Drop a locus entirely (a stale slot being relocated during migration). */
    public void remove(ResourceLocation locus) {
        if (locus != null) loci.remove(LegacyNamespace.canonical(locus));
    }

    public List<ResourceLocation> loci() {
        return new ArrayList<>(loci.keySet());
    }

    // The pair is stored as a two-entry string list: an allele encoding can itself contain
    // ';' (a multi-channel payload), so the old ";"-joined single string was ambiguous and
    // its first-semicolon split silently dropped every channel after the first on load.
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        loci.forEach((locus, pair) -> {
            ListTag list = new ListTag();
            list.add(StringTag.valueOf(pair[0].encode()));
            list.add(StringTag.valueOf(pair[1].encode()));
            tag.put(locus.toString(), list);
        });
        return tag;
    }

    public static Genotype fromTag(CompoundTag tag) {
        Genotype out = new Genotype();
        if (tag == null) return out;
        for (String key : tag.getAllKeys()) {
            ResourceLocation locus = ResourceLocation.tryParse(key);
            if (locus == null) continue;
            if (tag.get(key) instanceof ListTag list) {
                Allele a = list.size() > 0 ? Allele.decode(list.getString(0)) : Allele.WILD;
                Allele b = list.size() > 1 ? Allele.decode(list.getString(1)) : a;
                out.set(locus, a, b);
                continue;
            }
            // Legacy ";"-joined pair; AllelePair finds the true separator (recovering the
            // full first allele from a pair the old truncating split had mangled).
            String[] pair = AllelePair.splitLegacy(tag.getString(key));
            Allele a = Allele.decode(pair[0]);
            out.set(locus, a, pair.length > 1 ? Allele.decode(pair[1]) : a);
        }
        return out;
    }

    public Genotype copy() {
        Genotype out = new Genotype();
        loci.forEach((locus, pair) -> out.set(locus, pair[0], pair[1]));
        return out;
    }
}
