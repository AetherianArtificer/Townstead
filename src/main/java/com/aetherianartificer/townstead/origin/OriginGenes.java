package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.origin.gene.InheritedGene;
import com.aetherianartificer.townstead.origin.gene.types.BodyMetricGeneType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.List;
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

    /** The full MCA float-gene set, in a fixed order (used for snapshot/restore). */
    private static final Genetics.GeneType[] ORDERED = {
            Genetics.SIZE, Genetics.WIDTH, Genetics.BREAST,
            Genetics.MELANIN, Genetics.HEMOGLOBIN, Genetics.EUMELANIN, Genetics.PHEOMELANIN,
            Genetics.SKIN, Genetics.FACE, Genetics.VOICE, Genetics.VOICE_TONE
    };

    private static final Map<String, Genetics.GeneType> BY_KEY = buildIndex();

    private OriginGenes() {}

    private static Map<String, Genetics.GeneType> buildIndex() {
        Map<String, Genetics.GeneType> index = new LinkedHashMap<>();
        for (Genetics.GeneType type : ORDERED) {
            index.put(mcaKey(type), type);
        }
        return Map.copyOf(index);
    }

    // MCA's GeneType.key() differs by version: 1.21 returns "Size", 1.20.1 returns
    // "gene_size". Drop the 1.20.1 "gene" prefix so both normalize to the author-facing
    // "size"/"voice_tone"/... that a gene's "target" uses.
    private static String mcaKey(Genetics.GeneType type) {
        String k = normalizeKey(type.key());
        return k.length() > 4 && k.startsWith("gene") ? k.substring(4) : k;
    }

    /** Capture all MCA float genes (fixed order) so a preview can be reverted. */
    public static float[] snapshot(VillagerEntityMCA villager) {
        Genetics genetics = villager.getGenetics();
        float[] out = new float[ORDERED.length];
        for (int i = 0; i < ORDERED.length; i++) out[i] = genetics.getGene(ORDERED[i]);
        return out;
    }

    /** Restore a {@link #snapshot}; no-op on a null/mismatched array. */
    public static void restore(VillagerEntityMCA villager, float[] snapshot) {
        if (snapshot == null || snapshot.length != ORDERED.length) return;
        Genetics genetics = villager.getGenetics();
        for (int i = 0; i < ORDERED.length; i++) genetics.setGene(ORDERED[i], snapshot[i]);
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
     * Resolve the body-metric genes in a (locus-collapsed) gene list to a
     * {@code target → range} map over MCA's float genes. The list should be the
     * effective, locus-collapsed grant list, so a lineage's size gene has already
     * replaced its ancestry's; here we just read each body-metric gene's range.
     */
    public static Map<String, GeneRange> resolveBodyMetrics(List<InheritedGene> genes) {
        if (genes == null || genes.isEmpty()) return Map.of();
        LinkedHashMap<String, GeneRange> out = new LinkedHashMap<>();
        for (InheritedGene ref : genes) {
            Gene gene = GeneRegistry.byId(ref.geneId());
            if (gene != null && gene.instance() instanceof BodyMetricGeneType.Instance metric) {
                out.put(metric.target(), metric.range());
            }
        }
        return out;
    }

    /**
     * Roll each constrained MCA float into its range and write it onto the
     * villager; floats not in the map are left as MCA rolled them. A full
     * {@code [0,1]} range reproduces MCA's own uniform roll (Overworlder's no-op).
     */
    public static void apply(VillagerEntityMCA villager, Map<String, GeneRange> targetRanges, RandomSource random) {
        if (targetRanges == null || targetRanges.isEmpty()) return;
        Genetics genetics = villager.getGenetics();
        for (Map.Entry<String, GeneRange> entry : targetRanges.entrySet()) {
            Genetics.GeneType type = BY_KEY.get(entry.getKey());
            if (type != null) {
                genetics.setGene(type, entry.getValue().sample(random));
            }
        }
    }

    /**
     * Pull each constrained MCA float into its range, preserving in-range values
     * (non-destructive). Used when assigning an origin to an existing villager and
     * when re-clamping a bred child's blended floats to its heritage.
     */
    public static void clamp(VillagerEntityMCA villager, Map<String, GeneRange> targetRanges) {
        if (targetRanges == null || targetRanges.isEmpty()) return;
        Genetics genetics = villager.getGenetics();
        for (Map.Entry<String, GeneRange> entry : targetRanges.entrySet()) {
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
