package com.aetherianartificer.townstead.origin;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The canonical life-stage axis every Townstead stage maps onto. Origins are
 * free to name their stages anything ("Caterpillar", "Larva", …); each one
 * declares which canonical stage it presents as, and that's what MCA gates
 * (marriage, work-as-adult, advancement) read.
 *
 * <p>Mirrors MCA's {@code AgeState} plus a Townstead-owned {@link #SENIOR}
 * tail. SENIOR collapses to MCA's {@code ADULT} for those gates; Townstead
 * tracks the senior-ness flag separately.</p>
 */
public enum CanonicalStage {
    // defaultScale is a MULTIPLIER on MCA's own per-AgeState sizing (which already
    // ramps baby 0.4 -> adult 1.0 and interpolates it), NOT an absolute size. All
    // stages default to 1.0 = identical to vanilla MCA; per-stage size is purely
    // opt-in via the gene's "scale" field.
    // Contiguous apparent-age bands (each stage's end = next stage's start) so a
    // default human cycle authoring stage days == apparent-year spans reproduces a
    // smooth 0..90 apparent curve with no gaps. See LifeCycle.defaultHumanLike().
    BABY(0f, 2f, 1.00f),
    TODDLER(2f, 4f, 1.00f),
    CHILD(4f, 13f, 1.00f),
    TEEN(13f, 18f, 1.00f),
    ADULT(18f, 65f, 1.00f),
    SENIOR(65f, 90f, 1.00f);

    private final float defaultNarrativeStart;
    private final float defaultNarrativeEnd;
    private final float defaultScale;

    CanonicalStage(float defaultNarrativeStart, float defaultNarrativeEnd, float defaultScale) {
        this.defaultNarrativeStart = defaultNarrativeStart;
        this.defaultNarrativeEnd = defaultNarrativeEnd;
        this.defaultScale = defaultScale;
    }

    /**
     * Human-scaled apparent-age range a stage of this kind represents by default
     * (baby 0–2 … senior 65–90). Used when a cycle's stage omits an explicit
     * {@code narrative_age}, so a normal human-like cycle authors only pacing.
     * Long-lived races (elf centuries) override per stage.
     */
    public float defaultNarrativeStart() { return defaultNarrativeStart; }
    public float defaultNarrativeEnd() { return defaultNarrativeEnd; }

    /**
     * Default size multiplier on top of MCA's own per-AgeState sizing (and the
     * genetic Size), so 1.0 means "exactly what MCA would render". All stages
     * default to 1.0; per-stage size is opt-in via the gene's {@code scale} field.
     */
    public float defaultScale() { return defaultScale; }

    /** Parse the JSON {@code presents_as} field (case/underscore tolerant). */
    @Nullable
    public static CanonicalStage parse(String raw) {
        if (raw == null) return null;
        String norm = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (CanonicalStage c : values()) {
            if (c.name().toLowerCase(Locale.ROOT).equals(norm)) return c;
        }
        return null;
    }
}
