package com.aetherianartificer.townstead.client.animation.emote;

import java.util.Locale;

/**
 * Easing functions used by Emotecraft. Names accept both the JSON form
 * ({@code EASEINCIRC}) and the parsed-enum form ({@code INCIRC}); we normalize by
 * stripping any leading {@code EASE} and any underscores. Anything we don't
 * recognize falls back to {@link #LINEAR}.
 *
 * <p>Standard Penner easing equations; only the most common easings are
 * implemented because the rest gracefully degrade to LINEAR rather than crashing
 * the loader.</p>
 */
public enum EmoteEasing {
    LINEAR { @Override public float apply(float t) { return t; } },
    CONSTANT { @Override public float apply(float t) { return t < 1f ? 0f : 1f; } },
    INSINE {
        @Override public float apply(float t) {
            return 1f - (float) Math.cos((t * Math.PI) / 2.0);
        }
    },
    OUTSINE {
        @Override public float apply(float t) {
            return (float) Math.sin((t * Math.PI) / 2.0);
        }
    },
    INOUTSINE {
        @Override public float apply(float t) {
            return -(float) (Math.cos(Math.PI * t) - 1.0) / 2f;
        }
    },
    INQUAD { @Override public float apply(float t) { return t * t; } },
    OUTQUAD { @Override public float apply(float t) { return 1f - (1f - t) * (1f - t); } },
    INOUTQUAD {
        @Override public float apply(float t) {
            return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
        }
    },
    INCUBIC { @Override public float apply(float t) { return t * t * t; } },
    OUTCUBIC {
        @Override public float apply(float t) {
            float u = 1f - t;
            return 1f - u * u * u;
        }
    },
    INOUTCUBIC {
        @Override public float apply(float t) {
            return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }
    },
    INQUART { @Override public float apply(float t) { return t * t * t * t; } },
    OUTQUART { @Override public float apply(float t) { return 1f - (float) Math.pow(1f - t, 4); } },
    INOUTQUART {
        @Override public float apply(float t) {
            return t < 0.5f ? 8f * t * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 4) / 2f;
        }
    },
    INQUINT { @Override public float apply(float t) { return t * t * t * t * t; } },
    OUTQUINT { @Override public float apply(float t) { return 1f - (float) Math.pow(1f - t, 5); } },
    INOUTQUINT {
        @Override public float apply(float t) {
            return t < 0.5f ? 16f * t * t * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 5) / 2f;
        }
    },
    INCIRC { @Override public float apply(float t) { return 1f - (float) Math.sqrt(1f - t * t); } },
    OUTCIRC {
        @Override public float apply(float t) {
            return (float) Math.sqrt(1f - (t - 1f) * (t - 1f));
        }
    },
    INOUTCIRC {
        @Override public float apply(float t) {
            return t < 0.5f
                    ? (1f - (float) Math.sqrt(1f - 4f * t * t)) / 2f
                    : ((float) Math.sqrt(1f - (-2f * t + 2f) * (-2f * t + 2f)) + 1f) / 2f;
        }
    },
    INEXPO {
        @Override public float apply(float t) {
            return t == 0f ? 0f : (float) Math.pow(2.0, 10.0 * t - 10.0);
        }
    },
    OUTEXPO {
        @Override public float apply(float t) {
            return t >= 1f ? 1f : 1f - (float) Math.pow(2.0, -10.0 * t);
        }
    },
    INOUTEXPO {
        @Override public float apply(float t) {
            if (t == 0f) return 0f;
            if (t >= 1f) return 1f;
            return t < 0.5f
                    ? (float) Math.pow(2.0, 20.0 * t - 10.0) / 2f
                    : (2f - (float) Math.pow(2.0, -20.0 * t + 10.0)) / 2f;
        }
    },
    INBACK {
        @Override public float apply(float t) {
            float c1 = 1.70158f;
            float c3 = c1 + 1f;
            return c3 * t * t * t - c1 * t * t;
        }
    },
    OUTBACK {
        @Override public float apply(float t) {
            float c1 = 1.70158f;
            float c3 = c1 + 1f;
            float u = t - 1f;
            return 1f + c3 * u * u * u + c1 * u * u;
        }
    },
    INOUTBACK {
        @Override public float apply(float t) {
            float c1 = 1.70158f;
            float c2 = c1 * 1.525f;
            return t < 0.5f
                    ? ((float) Math.pow(2f * t, 2) * ((c2 + 1f) * 2f * t - c2)) / 2f
                    : ((float) Math.pow(2f * t - 2f, 2) * ((c2 + 1f) * (2f * t - 2f) + c2) + 2f) / 2f;
        }
    };

    public abstract float apply(float t);

    public static EmoteEasing fromNameOrLinear(String name) {
        if (name == null) return LINEAR;
        String key = name.trim().toUpperCase(Locale.ROOT).replace("_", "");
        if (key.startsWith("EASE")) key = key.substring(4);
        for (EmoteEasing easing : values()) {
            if (easing.name().equals(key)) return easing;
        }
        return LINEAR;
    }
}
