package com.aetherianartificer.townstead.client.skin;

import com.aetherianartificer.townstead.calendar.LifeClientStore;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.jetbrains.annotations.Nullable;

/**
 * Computes the sigmoid greying curve applied to a villager's hair colour while
 * they're in their senior life-stage. Center of the sigmoid is at ~50% progress
 * through senior, so "mostly grey by mid-senior" matches what the user picked.
 *
 * <p>Per-channel lerp toward a mid-grey target. Both stonecutter branches share
 * the math; the call sites (int ARGB on 1.21.1, float[] on 1.20.1) live in the
 * {@code HairLayer} mixin.</p>
 */
public final class SeniorHairDesat {

    /** Target grey hair colour at full senior progress. Mid-grey reads as "elder". */
    private static final int TARGET_R = 0xBC;
    private static final int TARGET_G = 0xBC;
    private static final int TARGET_B = 0xBC;

    // Editor preview override: the editor's villager is a throwaway client entity with
    // no committed snapshot, so the age slider pushes a transient senior-progress (0..1)
    // here keyed by the preview entity id. -1 = no override. Mirrors LifeStageScale.
    private static final java.util.Map<Integer, Float> PREVIEW_PROGRESS = new java.util.concurrent.ConcurrentHashMap<>();

    private SeniorHairDesat() {}

    /** Editor: set the previewed senior progress (0..1) for an entity id. */
    public static void setPreviewProgress(int entityId, float progress) {
        PREVIEW_PROGRESS.put(entityId, Math.max(0f, Math.min(1f, progress)));
    }

    public static void clearPreviewProgress(int entityId) {
        PREVIEW_PROGRESS.remove(entityId);
    }

    /**
     * Sigmoid-curved progress factor in {@code [0,1]} for a tracked villager,
     * or {@code 0} if not senior / unknown. Result is how far the hair colour
     * should be lerped toward grey. The editor preview override wins when present.
     */
    public static float lerpFactor(@Nullable VillagerEntityMCA villager) {
        if (villager == null) return 0f;
        Float override = PREVIEW_PROGRESS.get(villager.getId());
        float p;
        if (override != null) {
            p = override;
        } else {
            LifeClientStore.Snapshot snap = LifeClientStore.get(villager.getId());
            if (snap == null || !snap.isSenior()) return 0f;
            p = snap.seniorProgress();
        }
        if (p <= 0f) return 0f;
        // Centred at p=0.5 so it's "mostly grey by mid-senior" rather than tail-loaded.
        double sig = 1.0 / (1.0 + Math.exp(-10.0 * (p - 0.5)));
        if (sig < 0.0) sig = 0.0;
        if (sig > 1.0) sig = 1.0;
        return (float) sig;
    }

    /** Lerp an ARGB int toward grey by {@code factor}. Pass-through at factor=0. */
    public static int applyArgb(int argb, float factor) {
        if (factor <= 0f) return argb;
        if (factor >= 1f) {
            return (argb & 0xFF000000) | (TARGET_R << 16) | (TARGET_G << 8) | TARGET_B;
        }
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int rr = lerpChannel(r, TARGET_R, factor);
        int gg = lerpChannel(g, TARGET_G, factor);
        int bb = lerpChannel(b, TARGET_B, factor);
        return (a << 24) | (rr << 16) | (gg << 8) | bb;
    }

    /** Lerp a float[3-or-4] (r,g,b[,a]) toward grey by {@code factor}. */
    public static float[] applyFloats(float[] base, float factor) {
        if (factor <= 0f || base == null) return base;
        float tr = TARGET_R / 255f;
        float tg = TARGET_G / 255f;
        float tb = TARGET_B / 255f;
        float clamped = factor > 1f ? 1f : factor;
        float[] out = new float[base.length];
        out[0] = base[0] + (tr - base[0]) * clamped;
        out[1] = base[1] + (tg - base[1]) * clamped;
        out[2] = base[2] + (tb - base[2]) * clamped;
        if (base.length > 3) out[3] = base[3];
        return out;
    }

    private static int lerpChannel(int from, int to, float t) {
        int v = Math.round(from + (to - from) * t);
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }
}
