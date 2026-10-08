package com.aetherianartificer.townstead.chronicle.pregen;

import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import net.minecraft.util.RandomSource;

/**
 * How big a fabricated occurrence was. Live emission gets magnitude from the
 * tap that fired it; a fabricated past has to invent one, and inventing 1.0
 * every time makes every harvest identical and leaves scope thresholds nothing
 * to discriminate on. The spread is what lets an exceptional harvest reach the
 * village digest while a routine one stays a personal detail.
 */
public final class PregenMagnitude {

    /** What an occurrence varies by when the template says nothing: a wide working range. */
    public static final float DEFAULT_MIN = 0.6f;
    public static final float DEFAULT_MAX = 1.5f;

    private PregenMagnitude() {}

    /**
     * A harvest can be poor or spectacular, so its spread is wide. A birth is a
     * birth: templates that declare a narrow {@code pregen.magnitude} stop
     * drifting across a scope's threshold on noise alone, which otherwise made
     * half the weddings in a village miss the digest for no reason anyone could
     * name.
     */
    public static float draw(RandomSource rng, ChronicleEventTemplate template) {
        float min = template.magnitudeMin();
        float max = template.magnitudeMax();
        return max <= min ? min : min + rng.nextFloat() * (max - min);
    }
}
