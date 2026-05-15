package com.aetherianartificer.townstead.client.animation.emote;

import java.util.Locale;

/**
 * Easing functions used by Emotecraft. Names accept both the JSON form
 * ({@code EASEINCIRC}) and the parsed-enum form ({@code INCIRC}); we normalize
 * by stripping any leading {@code EASE} and any underscores. Anything we don't
 * recognize falls back to {@link #LINEAR}.
 *
 * <p>For Back/Elastic/Bounce/Step, the optional {@code easingArg} parameter
 * shapes the curve the same way upstream's {@code Easing.back/elastic/bounce/step}
 * does (overshoot magnitude for back, oscillation rate for elastic, bounce
 * height ratio, step count). When {@code easingArg} is null we use the same
 * defaults upstream does, so a JSON keyframe without {@code easingArg} produces
 * a bit-identical curve.</p>
 *
 * <p>For the simpler curves (sine, quad, cubic, etc.), {@code easingArg} is
 * ignored — upstream's {@code Easing} class doesn't parameterize those either.</p>
 *
 * <p><b>Fidelity policy:</b> these curves reproduce Emotecraft's playback
 * exactly, including upstream's {@code CATMULLROM} bug (the polynomial
 * coefficients there aren't multiplied by {@code t}/{@code t²}/{@code t³}, so
 * the function reduces to {@code t + 2} — animation values lerp into
 * [2a−b, 3b−2a]). An emote that authors {@code CATMULLROM} looks busted in
 * Emotecraft and must look identically busted in Townstead.</p>
 *
 * <p>This enum is intentionally separate from the CEM/OptiFine easings in
 * {@link com.aetherianartificer.townstead.client.animation.cem.CemAnimationProgram} —
 * several share a name (e.g. {@code INELASTIC}) but use different formulas.
 * Each one is bit-identical to its respective upstream; don't try to merge.</p>
 */
public enum EmoteEasing {
    LINEAR {
        @Override public float apply(float t, Float easingArg) { return t; }
    },
    CONSTANT {
        @Override public float apply(float t, Float easingArg) { return t < 1f ? 0f : 1f; }
    },
    INSINE {
        @Override public float apply(float t, Float easingArg) {
            return 1f - (float) Math.cos((t * Math.PI) / 2.0);
        }
    },
    OUTSINE {
        @Override public float apply(float t, Float easingArg) {
            return (float) Math.sin((t * Math.PI) / 2.0);
        }
    },
    INOUTSINE {
        @Override public float apply(float t, Float easingArg) {
            return -(float) (Math.cos(Math.PI * t) - 1.0) / 2f;
        }
    },
    INQUAD {
        @Override public float apply(float t, Float easingArg) { return t * t; }
    },
    OUTQUAD {
        @Override public float apply(float t, Float easingArg) { return 1f - (1f - t) * (1f - t); }
    },
    INOUTQUAD {
        @Override public float apply(float t, Float easingArg) {
            return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
        }
    },
    INCUBIC {
        @Override public float apply(float t, Float easingArg) { return t * t * t; }
    },
    OUTCUBIC {
        @Override public float apply(float t, Float easingArg) {
            float u = 1f - t;
            return 1f - u * u * u;
        }
    },
    INOUTCUBIC {
        @Override public float apply(float t, Float easingArg) {
            return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }
    },
    INQUART {
        @Override public float apply(float t, Float easingArg) { return t * t * t * t; }
    },
    OUTQUART {
        @Override public float apply(float t, Float easingArg) { return 1f - (float) Math.pow(1f - t, 4); }
    },
    INOUTQUART {
        @Override public float apply(float t, Float easingArg) {
            return t < 0.5f ? 8f * t * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 4) / 2f;
        }
    },
    INQUINT {
        @Override public float apply(float t, Float easingArg) { return t * t * t * t * t; }
    },
    OUTQUINT {
        @Override public float apply(float t, Float easingArg) { return 1f - (float) Math.pow(1f - t, 5); }
    },
    INOUTQUINT {
        @Override public float apply(float t, Float easingArg) {
            return t < 0.5f ? 16f * t * t * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 5) / 2f;
        }
    },
    INCIRC {
        @Override public float apply(float t, Float easingArg) { return 1f - (float) Math.sqrt(1f - t * t); }
    },
    OUTCIRC {
        @Override public float apply(float t, Float easingArg) {
            return (float) Math.sqrt(1f - (t - 1f) * (t - 1f));
        }
    },
    INOUTCIRC {
        @Override public float apply(float t, Float easingArg) {
            return t < 0.5f
                    ? (1f - (float) Math.sqrt(1f - 4f * t * t)) / 2f
                    : ((float) Math.sqrt(1f - (-2f * t + 2f) * (-2f * t + 2f)) + 1f) / 2f;
        }
    },
    INEXPO {
        @Override public float apply(float t, Float easingArg) {
            return t == 0f ? 0f : (float) Math.pow(2.0, 10.0 * t - 10.0);
        }
    },
    OUTEXPO {
        @Override public float apply(float t, Float easingArg) {
            return t >= 1f ? 1f : 1f - (float) Math.pow(2.0, -10.0 * t);
        }
    },
    INOUTEXPO {
        @Override public float apply(float t, Float easingArg) {
            if (t == 0f) return 0f;
            if (t >= 1f) return 1f;
            return t < 0.5f
                    ? (float) Math.pow(2.0, 20.0 * t - 10.0) / 2f
                    : (2f - (float) Math.pow(2.0, -20.0 * t + 10.0)) / 2f;
        }
    },
    INBACK {
        @Override public float apply(float t, Float easingArg) { return backKernel(t, easingArg); }
    },
    OUTBACK {
        @Override public float apply(float t, Float easingArg) { return 1f - backKernel(1f - t, easingArg); }
    },
    INOUTBACK {
        @Override public float apply(float t, Float easingArg) {
            return t < 0.5f
                    ? backKernel(2f * t, easingArg) / 2f
                    : 1f - backKernel(2f - 2f * t, easingArg) / 2f;
        }
    },
    INELASTIC {
        @Override public float apply(float t, Float easingArg) { return elasticKernel(t, easingArg); }
    },
    OUTELASTIC {
        @Override public float apply(float t, Float easingArg) { return 1f - elasticKernel(1f - t, easingArg); }
    },
    INOUTELASTIC {
        @Override public float apply(float t, Float easingArg) {
            return t < 0.5f
                    ? elasticKernel(2f * t, easingArg) / 2f
                    : 1f - elasticKernel(2f - 2f * t, easingArg) / 2f;
        }
    },
    INBOUNCE {
        @Override public float apply(float t, Float easingArg) { return bounceKernel(t, easingArg); }
    },
    OUTBOUNCE {
        @Override public float apply(float t, Float easingArg) { return 1f - bounceKernel(1f - t, easingArg); }
    },
    INOUTBOUNCE {
        @Override public float apply(float t, Float easingArg) {
            return t < 0.5f
                    ? bounceKernel(2f * t, easingArg) / 2f
                    : 1f - bounceKernel(2f - 2f * t, easingArg) / 2f;
        }
    },
    STEP {
        @Override public float apply(float t, Float easingArg) { return stepKernel(t, easingArg); }
    },
    // Upstream's Easing.catmullRom expands a Catmull-Rom polynomial but the
    // coefficients are never multiplied by t / t² / t³ — see the class-level
    // fidelity note. The arithmetic literally reduces to t + 2.
    CATMULLROM {
        @Override public float apply(float t, Float easingArg) { return t + 2f; }
    };

    public abstract float apply(float t, Float easingArg);

    public final float apply(float t) {
        return apply(t, null);
    }

    public static EmoteEasing fromNameOrLinear(String name) {
        if (name == null) return LINEAR;
        String key = name.trim().toUpperCase(Locale.ROOT).replace("_", "");
        if (key.startsWith("EASE")) key = key.substring(4);
        for (EmoteEasing easing : values()) {
            if (easing.name().equals(key)) return easing;
        }
        return LINEAR;
    }

    private static float backKernel(float t, Float easingArg) {
        float c1 = (easingArg == null ? 1f : easingArg) * 1.70158f;
        return t * t * ((c1 + 1f) * t - c1);
    }

    private static float elasticKernel(float t, Float easingArg) {
        float c4 = easingArg == null ? 1f : easingArg;
        double cosHalf = Math.cos(t * Math.PI / 2.0);
        return (float) (1.0 - Math.pow(cosHalf, 3.0) * Math.cos(t * c4 * Math.PI));
    }

    // Upstream's parameterized bounce — min of four parabolic arcs, with the
    // arc heights scaling by r, r², r³. At r=0.5 (default) this matches the
    // canonical Penner out-bounce shape at t=0 and t=1 but is smoother in
    // between (no piecewise discontinuities at the arc boundaries).
    private static float bounceKernel(float t, Float easingArg) {
        float r = easingArg == null ? 0.5f : easingArg;
        float r2 = r * r;
        float r3 = r2 * r;
        float a0 = (121f / 16f) * t * t;
        float d1 = t - 6f / 11f;
        float a1 = (121f / 4f) * r * d1 * d1 + (1f - r);
        float d2 = t - 9f / 11f;
        float a2 = 121f * r2 * d2 * d2 + (1f - r2);
        float d3 = t - 10.5f / 11f;
        float a3 = 484f * r3 * d3 * d3 + (1f - r3);
        return Math.min(Math.min(a0, a1), Math.min(a2, a3));
    }

    // Upstream throws when n<2; we coerce. With default n=2 the curve only
    // reaches 0.5 at t=1 (never lands on 1), which is upstream's documented
    // behavior — STEP IS a half-step at default.
    private static float stepKernel(float t, Float easingArg) {
        float n = easingArg == null ? 2f : easingArg;
        if (n < 2f) n = 2f;
        int steps = (int) n;
        if (t < 0f) return 0f;
        float stride = 1f / steps;
        float max = (steps - 1) * stride;
        if (t > max) return max;
        int lo = 0;
        int hi = steps - 1;
        while (hi - lo > 1) {
            int mid = lo + (hi - lo) / 2;
            if (t >= mid * stride) lo = mid; else hi = mid;
        }
        return lo * stride;
    }
}
