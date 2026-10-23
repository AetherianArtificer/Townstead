package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.calendar.LifeClientStore;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the per-villager model size multiplier from its current life stage,
 * smoothly interpolated between stage scales. Shared by rendering and physical sizing.
 *
 * <p>The editor's preview villager has no real life data, so the editor pushes a
 * transient override keyed by the preview entity id while the slider is dragged.</p>
 */
public final class LifeStageScale {

    private static final Map<Integer, Float> PREVIEW_OVERRIDE = new ConcurrentHashMap<>();

    private LifeStageScale() {}

    /** The same life-stage multiplier on both sides; 1.0 without life data. */
    public static float forVillager(Entity e) {
        if (e == null) return 1f;
        if (!e.level().isClientSide) {
            if (!(e instanceof net.conczin.mca.entity.VillagerEntityMCA villager) || e.getServer() == null) return 1f;
            var life = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).life();
            if (!life.hasBirth() || !life.hasStageDays()) return 1f;
            var id = net.minecraft.resources.ResourceLocation.tryParse(life.rootId());
            var cycle = RootRegistry.effectiveLifeCycle(id == null ? RootRegistry.DEFAULT_ID : id);
            if (cycle == null || cycle.isEmpty() || cycle.size() != life.stageDaysLength()) return 1f;
            long today = LifeStageProgression.agingDisplayDayView(villager, life,
                    com.aetherianartificer.townstead.calendar.TownsteadCalendar.lifeDay(e.getServer()));
            float[] scales = new float[cycle.size()];
            for (int i = 0; i < scales.length; i++) scales[i] = cycle.stageAt(i).scale();
            return interpolate(scales, life.stageDays(), Math.max(0L, today - life.birthWorldDay()));
        }
        Float override = PREVIEW_OVERRIDE.get(e.getId());
        if (override != null) return safe(override);
        LifeClientStore.Snapshot snap = LifeClientStore.get(e.getId());
        if (snap == null || !snap.hasCycle()) return 1f;
        return interpolate(snap.stageScales(), snap.stageDays(), snap.bioAgeDays());
    }

    private static float safe(float value) {
        return Float.isFinite(value) && value > 0 ? value : 1f;
    }

    public static void setPreviewOverride(int entityId, float scale) {
        PREVIEW_OVERRIDE.put(entityId, scale);
    }

    public static void clearPreviewOverride(int entityId) {
        PREVIEW_OVERRIDE.remove(entityId);
    }

    /**
     * Smoothly interpolate the stage scale at {@code bioAge} (days), anchoring each
     * stage's scale at its midpoint so growth ramps continuously between stages.
     * Returns 1.0 if the arrays are absent or mismatched.
     */
    public static float interpolate(float[] scales, int[] days, double bioAge) {
        if (scales == null || days == null || scales.length == 0 || days.length != scales.length) return 1f;
        int n = scales.length;
        double prevMid = 0;
        float prevScale = scales[0];
        double cum = 0;
        for (int i = 0; i < n; i++) {
            double d = Math.max(0, days[i]);
            double mid = cum + d / 2.0;
            if (i == 0) {
                if (bioAge <= mid) return safe(scales[0]);
            } else if (bioAge < mid) {
                double span = mid - prevMid;
                double t = span <= 0 ? 0 : (bioAge - prevMid) / span;
                if (t < 0) t = 0; else if (t > 1) t = 1;
                return safe((float) (safe(prevScale) + (safe(scales[i]) - safe(prevScale)) * t));
            }
            prevMid = mid;
            prevScale = scales[i];
            cum += d;
        }
        return safe(scales[n - 1]);
    }
}
