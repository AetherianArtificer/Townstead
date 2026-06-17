package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of villager life snapshots keyed by entity id. Cleared
 * on logout. Mirrors {@link com.aetherianartificer.townstead.hunger.HungerClientStore}
 * in shape. Carries enough of the resolved cycle (per-stage days + labels) for
 * the editor age slider and the interact-screen stage label.
 */
public final class LifeClientStore {

    public record Snapshot(
            int birthYear,
            int birthMonthIndex,
            int birthDayOfMonth,
            String birthMonthKey,
            String birthMonthFallback,
            int ageYears,
            boolean stamped,
            boolean isSenior,
            int seniorProgressPermil,
            int bioAgeDays,
            boolean immortal,
            boolean ageless,
            int currentStageIndex,
            int[] stageDays,
            String[] stageLabelKeys,
            String[] stageLabelFallbacks,
            float narrativeAge,
            float[] stageScales,
            int[] stageModelAges,
            float[] stageNarrativeMin,
            float[] stageNarrativeMax,
            float narrativeRate,
            int seniorStageIndex,
            String personalityName,
            String personalityDesc,
            String[] personalityPoolRefs,
            String[] personalityPoolNames
    ) {
        /** True when this villager carries a custom (data-pack) personality whose name should be shown. */
        public boolean hasCustomPersonality() {
            return personalityName != null && !personalityName.isEmpty();
        }

        /** The personality refs this villager's origin allows (custom ids or base-enum names); for the editor picker. */
        public String[] personalityPool() {
            return personalityPoolRefs == null ? new String[0] : personalityPoolRefs;
        }

        /** The resolved display name for a pool ref at {@code index} (empty for a bare base-enum ref). */
        public String personalityPoolName(int index) {
            return personalityPoolNames != null && index >= 0 && index < personalityPoolNames.length
                    ? personalityPoolNames[index] : "";
        }

        /** Real age in game-years = elapsed game-days / calendar year length. */
        public int realAgeYears(int daysPerYear) {
            return bioAgeDays / Math.max(1, daysPerYear);
        }

        public Component birthMonthComponent() {
            return ComponentSync.reconstruct(birthMonthKey, birthMonthFallback);
        }

        /** 0.0 at start of senior stage, 1.0 at end; 0 when not senior. */
        public float seniorProgress() {
            return isSenior ? Math.max(0f, Math.min(1f, seniorProgressPermil / 1000f)) : 0f;
        }

        /**
         * Senior-stage progress (0..1) for an arbitrary biological age, for the editor
         * preview — independent of the committed {@code isSenior}/{@code seniorProgressPermil}.
         * Returns 0 before the senior stage (or if the cycle has no senior stage), ramps
         * 0→1 across it, and stays 1 past its end.
         */
        public float seniorProgressForBio(int bioAge) {
            if (seniorStageIndex < 0 || stageDays == null || seniorStageIndex >= stageDays.length) {
                return 0f;
            }
            int before = 0;
            for (int i = 0; i < seniorStageIndex; i++) before += Math.max(0, stageDays[i]);
            int span = Math.max(1, stageDays[seniorStageIndex]);
            float p = (bioAge - before) / (float) span;
            return p < 0f ? 0f : (p > 1f ? 1f : p);
        }

        public boolean hasCycle() {
            return stageDays != null && stageDays.length > 0;
        }

        public int stageCount() {
            return stageDays == null ? 0 : stageDays.length;
        }

        /** Sum of all stage durations — the full biological span the slider covers. */
        public int totalDays() {
            int sum = 0;
            if (stageDays != null) for (int d : stageDays) sum += Math.max(0, d);
            return Math.max(1, sum);
        }

        /** Localized label for the stage at {@code index}, or empty if out of range. */
        public Component stageLabel(int index) {
            if (stageLabelKeys == null || index < 0 || index >= stageLabelKeys.length) {
                return Component.empty();
            }
            String key = stageLabelKeys[index];
            String fallback = (stageLabelFallbacks != null && index < stageLabelFallbacks.length)
                    ? stageLabelFallbacks[index] : key;
            return ComponentSync.reconstruct(key, fallback);
        }

        public Component currentStageLabel() {
            return stageLabel(currentStageIndex);
        }

        /**
         * Apparent ("life years") age for a biological age, interpolated across the
         * stage's authored narrative range. Mirrors the server's {@code narrativeAgeAt}
         * so the editor's live readout matches the inspect-screen value. Falls back to
         * the single synced {@link #narrativeAge} when the ranges are absent.
         */
        public float narrativeAgeForBio(int bioAge) {
            // Normal cycles: apparent age is linear in days alive (rate synced from
            // the server's aging scale). Authored-band cycles fall through to the bands.
            if (narrativeRate > 0f) {
                // Clamp to the species' full lifespan so a stale/over-aged birth (e.g.
                // a pre-rework save, or real_clock aging a villager past death before
                // the senescence system exists) can't display an absurd apparent age.
                int clamped = Math.max(0, Math.min(bioAge, totalDays()));
                return clamped * narrativeRate;
            }
            if (stageNarrativeMin == null || stageNarrativeMax == null
                    || stageDays == null || stageNarrativeMin.length != stageDays.length) {
                return narrativeAge;
            }
            int cumulative = 0;
            for (int i = 0; i < stageDays.length; i++) {
                int d = Math.max(1, stageDays[i]);
                if (bioAge < cumulative + d || i == stageDays.length - 1) {
                    float delta = Math.max(0f, Math.min(1f, (bioAge - cumulative) / (float) d));
                    return stageNarrativeMin[i] + delta * (stageNarrativeMax[i] - stageNarrativeMin[i]);
                }
                cumulative += d;
            }
            return narrativeAge;
        }

        /** Apparent age at the midpoint of stage {@code idx} (immortal frozen view). */
        public float narrativeAgeAtIndex(int idx) {
            if (stageDays == null || idx < 0 || idx >= stageDays.length) return narrativeAge;
            if (narrativeRate > 0f) {
                int before = 0;
                for (int i = 0; i < idx; i++) before += Math.max(1, stageDays[i]);
                return (before + Math.max(1, stageDays[idx]) / 2f) * narrativeRate;
            }
            if (stageNarrativeMin == null || stageNarrativeMax == null
                    || idx >= stageNarrativeMin.length) {
                return narrativeAge;
            }
            return (stageNarrativeMin[idx] + stageNarrativeMax[idx]) / 2f;
        }

        /** The stage index a given biological age (in days) falls into. Clamps to the last stage. */
        public int stageIndexForBioAge(int bioAge) {
            if (stageDays == null || stageDays.length == 0) return -1;
            int cumulative = 0;
            for (int i = 0; i < stageDays.length; i++) {
                cumulative += Math.max(0, stageDays[i]);
                if (bioAge < cumulative) return i;
            }
            return stageDays.length - 1;
        }
    }

    private static final Map<Integer, Snapshot> BY_ENTITY = new ConcurrentHashMap<>();
    private static Runnable onChange;

    private LifeClientStore() {}

    public static void setOnChange(Runnable callback) {
        onChange = callback;
    }

    public static void clearOnChange() {
        onChange = null;
    }

    public static void setFrom(VillagerLifeSyncPayload payload) {
        BY_ENTITY.put(payload.entityId(), new Snapshot(
                payload.birthYear(),
                payload.birthMonthIndex(),
                payload.birthDayOfMonth(),
                payload.birthMonthKey(),
                payload.birthMonthFallback(),
                payload.ageYears(),
                payload.stamped(),
                payload.isSenior(),
                payload.seniorProgressPermil(),
                payload.bioAgeDays(),
                payload.immortal(),
                payload.ageless(),
                payload.currentStageIndex(),
                payload.stageDays(),
                payload.stageLabelKeys(),
                payload.stageLabelFallbacks(),
                payload.narrativeAge(),
                payload.stageScales(),
                payload.stageModelAges(),
                payload.stageNarrativeMin(),
                payload.stageNarrativeMax(),
                payload.narrativeRate(),
                payload.seniorStageIndex(),
                payload.personalityName(),
                payload.personalityDesc(),
                payload.personalityPoolRefs(),
                payload.personalityPoolNames()
        ));
        if (onChange != null) onChange.run();
    }

    @Nullable
    public static Snapshot get(int entityId) {
        return BY_ENTITY.get(entityId);
    }

    public static void remove(int entityId) {
        BY_ENTITY.remove(entityId);
    }

    public static void clear() {
        BY_ENTITY.clear();
    }
}
