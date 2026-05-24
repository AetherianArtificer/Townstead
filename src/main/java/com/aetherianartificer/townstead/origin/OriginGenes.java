package com.aetherianartificer.townstead.origin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bridge between genome gene keys (JSON) and MCA's float {@link Genetics.GeneType}s.
 *
 * <p>MCA's {@code GeneType.key()} returns CamelCase ({@code "Size"},
 * {@code "VoiceTone"}, …). Authors reference genes by a forgiving normalized
 * form (lowercase, optional {@code mca:} prefix, underscores ignored), so
 * {@code "VoiceTone"}, {@code "voice_tone"}, and {@code "mca:voicetone"} all map
 * to the same gene.</p>
 *
 * <p>The only origin class that touches MCA's gene API. Genes live on MCA's own
 * {@code Genetics} (saved/synced by MCA), independent of Townstead's snapshot.</p>
 */
public final class OriginGenes {

    private static final Map<String, Genetics.GeneType> BY_KEY = buildIndex();

    private OriginGenes() {}

    private static Map<String, Genetics.GeneType> buildIndex() {
        Genetics.GeneType[] all = {
                Genetics.SIZE, Genetics.WIDTH, Genetics.BREAST,
                Genetics.MELANIN, Genetics.HEMOGLOBIN, Genetics.EUMELANIN, Genetics.PHEOMELANIN,
                Genetics.SKIN, Genetics.FACE, Genetics.VOICE, Genetics.VOICE_TONE
        };
        Map<String, Genetics.GeneType> index = new LinkedHashMap<>();
        for (Genetics.GeneType type : all) {
            index.put(normalizeKey(type.key()), type);
        }
        return Map.copyOf(index);
    }

    /** Normalize an author-supplied gene key to the form used as a map key. */
    public static String normalizeKey(String raw) {
        if (raw == null) return "";
        String k = raw.trim().toLowerCase(Locale.ROOT);
        int colon = k.indexOf(':');
        if (colon >= 0) k = k.substring(colon + 1);
        return k.replace("_", "");
    }

    public static boolean isKnown(String normalizedKey) {
        return BY_KEY.containsKey(normalizedKey);
    }

    /**
     * Roll each gene the genome constrains into its range and write it onto the
     * villager's genetics; genes the genome does not mention are left as MCA
     * rolled them. A full {@code [0,1]} range therefore reproduces MCA's own
     * uniform roll (Overworlder's deliberate no-op).
     */
    public static void apply(VillagerEntityMCA villager, Genome genome, RandomSource random) {
        if (genome == null || genome.genes().isEmpty()) return;
        Genetics genetics = villager.getGenetics();
        for (Map.Entry<String, GeneRange> entry : genome.genes().entrySet()) {
            Genetics.GeneType type = BY_KEY.get(entry.getKey());
            if (type != null) {
                genetics.setGene(type, entry.getValue().sample(random));
            }
        }
    }

    /**
     * Pull each constrained gene into the genome's range, preserving in-range
     * values (non-destructive). Used when assigning an origin to an existing
     * villager in the editor. A full {@code [0,1]} range leaves every gene
     * untouched (Overworlder's no-op).
     */
    public static void clamp(VillagerEntityMCA villager, Genome genome) {
        if (genome == null || genome.genes().isEmpty()) return;
        Genetics genetics = villager.getGenetics();
        for (Map.Entry<String, GeneRange> entry : genome.genes().entrySet()) {
            Genetics.GeneType type = BY_KEY.get(entry.getKey());
            if (type == null) continue;
            GeneRange range = entry.getValue();
            float current = genetics.getGene(type);
            float clamped = Math.max(range.min(), Math.min(range.max(), current));
            if (clamped != current) {
                genetics.setGene(type, clamped);
            }
        }
    }
}
