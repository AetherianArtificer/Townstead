package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One entry in a {@link LifeCycle}'s ordered list.
 *
 * <p>{@code id} is the stable identifier the runtime uses (NBT, telemetry,
 * dialogue). {@code label} is the localized name shown to players (caterpillar,
 * elder, …). {@code presentsAs} maps the stage onto the canonical axis so
 * MCA's hardcoded {@code == ADULT} gates still work for an origin that calls
 * its adult stage "Butterfly".</p>
 *
 * <p>Two independent axes carry the time model:</p>
 * <ul>
 *   <li>{@code days} — how long the stage lasts in <em>game-days</em> (pacing).
 *       Calendar-independent like MCA's tick-based aging, so a villager grows up
 *       in the same wall-clock time regardless of the world's year length.
 *       Scaled per-villager by the Lifespan variance at spawn.</li>
 *   <li>{@code narrativeStart}/{@code narrativeEnd} — the <em>apparent</em> age
 *       range in "life years" this stage represents (e.g. adult 18→64). Display
 *       interpolates across it by progress, giving the dog-years age shown to
 *       players (the real elapsed game-time is far too small to be a headline).</li>
 * </ul>
 *
 * <p>{@code onEnd} controls what happens when the stage expires; null means
 * {@link StageEndAction#NEXT} for non-last entries and {@link StageEndAction#STAY}
 * for the last.</p>
 */
public record LifeStage(
        String id,
        Component label,
        CanonicalStage presentsAs,
        int days,
        float narrativeStart,
        float narrativeEnd,
        @Nullable StageEndAction onEnd,
        float scale,
        boolean explicitNarrative
) {
    public LifeStage {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("stage id is required");
        if (presentsAs == null) throw new IllegalArgumentException("presentsAs is required");
        if (days < 1) days = 1;
        if (narrativeStart < 0f) narrativeStart = 0f;
        if (narrativeEnd < narrativeStart) narrativeEnd = narrativeStart;
        if (scale <= 0f) scale = 1f;
    }

    /**
     * Build a stage whose apparent-age range and model {@code scale} both default
     * from its canonical kind ({@code presents_as}). The common path: a human-like
     * cycle authors only pacing ({@code days}) and lets apparent age derive linearly
     * from days alive (see {@code explicitNarrative}).
     */
    public static LifeStage of(String id, Component label, CanonicalStage presentsAs,
                               int days, @Nullable StageEndAction onEnd) {
        return new LifeStage(id, label, presentsAs, days,
                presentsAs.defaultNarrativeStart(), presentsAs.defaultNarrativeEnd(), onEnd,
                presentsAs.defaultScale(), false);
    }

    /** Apparent ("life years") age at {@code delta} (0..1) through this stage. */
    public float narrativeAgeAt(float delta) {
        float d = delta < 0f ? 0f : (delta > 1f ? 1f : delta);
        return narrativeStart + d * (narrativeEnd - narrativeStart);
    }
}
