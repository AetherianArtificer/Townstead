package com.aetherianartificer.townstead.hunger;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.food.FoodProperties;

public final class HungerData {

    // --- Defaults (applied to existing villagers on first access) ---
    public static final int DEFAULT_HUNGER = 80;
    public static final float DEFAULT_SATURATION = 5f;

    // --- Bounds ---
    public static final int MAX_HUNGER = 100;
    public static final float MAX_SATURATION = 100f;

    // --- Exhaustion threshold (mirrors vanilla player) ---
    public static final float EXHAUSTION_THRESHOLD = 4.0f;

    // --- Exhaustion rates (per tick unless noted) ---
    public static final float EXHAUSTION_CHORE = 0.012f;
    public static final float EXHAUSTION_GUARD_PATROL = 0.008f;
    public static final float EXHAUSTION_COMBAT = 0.02f;
    public static final float EXHAUSTION_MOVEMENT_PER_BLOCK = 0.01f;
    public static final float EXHAUSTION_AWAKE_BASELINE = 0.0003f;

    // --- Meal hunger thresholds ---
    public static final int BREAKFAST_THRESHOLD = 80;
    public static final int LUNCH_THRESHOLD = 70;
    public static final int DINNER_THRESHOLD = 60;
    public static final int EMERGENCY_THRESHOLD = 25;
    public static final int ADEQUATE_THRESHOLD = 50;

    // --- Mood effect interval (ticks) ---
    // Longer cadence plus fractional pressure accumulation keeps mood drift subtle.
    public static final int MOOD_CHECK_INTERVAL = 2400;

    // --- Speed penalty ---
    public static final int SPEED_PENALTY_THRESHOLD = 25;
    public static final double SPEED_PENALTY_AMOUNT = -0.25;

    // --- Passive metabolic drain (time-based, independent of exhaustion) ---
    public static final int PASSIVE_DRAIN_INTERVAL = 500; // ticks (~25 sec, ~24 times/day)

    // --- Minimum ticks between meals ---
    public static final long MIN_EAT_INTERVAL = 200;

    // --- Food restoration multiplier (scales vanilla nutrition to 0-100 range) ---
    public static final float FOOD_SCALE = 3.5f;

    // --- NBT keys (attachment internal) ---
    private static final String KEY_HUNGER = "hunger";
    private static final String KEY_SATURATION = "saturation";
    private static final String KEY_EXHAUSTION = "exhaustion";
    private static final String KEY_LAST_ATE_TIME = "lastAteTime";
    private static final String KEY_EATING_MODE = "eatingMode";
    private static final String KEY_MOOD_DRIFT = "moodDrift";
    private static final String KEY_FARM_BLOCKED_REASON = "farmBlockedReason";
    private static final String KEY_BUTCHER_BLOCKED_REASON = "butcherBlockedReason";

    // --- NBT keys for editor sync (piggybacked on MCA's VillagerEditorSyncRequest) ---
    public static final String EDITOR_KEY_HUNGER = "townstead_hunger";
    public static final String EDITOR_KEY_SATURATION = "townstead_saturation";
    public static final String EDITOR_KEY_EXHAUSTION = "townstead_exhaustion";

    private HungerData() {}

    // --- CompoundTag read/write ---

    public static int getHunger(CompoundTag tag) {
        return tag.contains(KEY_HUNGER) ? tag.getInt(KEY_HUNGER) : DEFAULT_HUNGER;
    }

    public static float getSaturation(CompoundTag tag) {
        return tag.contains(KEY_SATURATION) ? tag.getFloat(KEY_SATURATION) : DEFAULT_SATURATION;
    }

    public static float getExhaustion(CompoundTag tag) {
        return tag.getFloat(KEY_EXHAUSTION);
    }

    public static long getLastAteTime(CompoundTag tag) {
        return tag.getLong(KEY_LAST_ATE_TIME);
    }

    public static void setHunger(CompoundTag tag, int value) {
        tag.putInt(KEY_HUNGER, clamp(value, 0, MAX_HUNGER));
    }

    public static void setSaturation(CompoundTag tag, float value) {
        tag.putFloat(KEY_SATURATION, Math.max(0f, Math.min(value, MAX_SATURATION)));
    }

    public static void setExhaustion(CompoundTag tag, float value) {
        tag.putFloat(KEY_EXHAUSTION, Math.max(0f, value));
    }

    public static void setLastAteTime(CompoundTag tag, long tick) {
        tag.putLong(KEY_LAST_ATE_TIME, tick);
    }

    public static boolean isEatingMode(CompoundTag tag) {
        return tag.getBoolean(KEY_EATING_MODE);
    }

    public static void setEatingMode(CompoundTag tag, boolean eatingMode) {
        tag.putBoolean(KEY_EATING_MODE, eatingMode);
    }

    public static float getMoodDrift(CompoundTag tag) {
        return tag.getFloat(KEY_MOOD_DRIFT);
    }

    public static void setMoodDrift(CompoundTag tag, float value) {
        // Keep residual bounded so stale values cannot explode due to future tuning.
        tag.putFloat(KEY_MOOD_DRIFT, Math.max(-4f, Math.min(value, 4f)));
    }

    public static FarmBlockedReason getFarmBlockedReason(CompoundTag tag) {
        String id = tag.getString(KEY_FARM_BLOCKED_REASON);
        return FarmBlockedReason.fromId(id);
    }

    public static void setFarmBlockedReason(CompoundTag tag, FarmBlockedReason reason) {
        tag.putString(KEY_FARM_BLOCKED_REASON, reason.id);
    }

    public static ButcherBlockedReason getButcherBlockedReason(CompoundTag tag) {
        String id = tag.getString(KEY_BUTCHER_BLOCKED_REASON);
        return ButcherBlockedReason.fromId(id);
    }

    public static void setButcherBlockedReason(CompoundTag tag, ButcherBlockedReason reason) {
        tag.putString(KEY_BUTCHER_BLOCKED_REASON, reason.id);
    }

    /**
     * Process the exhaustion pipeline: when exhaustion reaches the threshold,
     * drain saturation first, then hunger. Returns true if hunger value changed.
     */
    public static boolean processExhaustion(CompoundTag tag) {
        float exhaustion = getExhaustion(tag);
        if (exhaustion < EXHAUSTION_THRESHOLD) return false;

        boolean hungerChanged = false;
        while (exhaustion >= EXHAUSTION_THRESHOLD) {
            exhaustion -= EXHAUSTION_THRESHOLD;
            float sat = getSaturation(tag);
            if (sat > 0f) {
                setSaturation(tag, sat - 1f);
            } else {
                int hunger = getHunger(tag);
                if (hunger > 0) {
                    setHunger(tag, hunger - 1);
                    hungerChanged = true;
                }
            }
        }
        setExhaustion(tag, exhaustion);
        return hungerChanged;
    }

    /**
     * Passive metabolic drain: drains 1 saturation (or 1 hunger if empty).
     * Returns true if hunger value changed.
     */
    public static boolean passiveDrain(CompoundTag tag) {
        float sat = getSaturation(tag);
        if (sat > 0f) {
            setSaturation(tag, sat - 1f);
            return false;
        }
        int hunger = getHunger(tag);
        if (hunger > 0) {
            setHunger(tag, hunger - 1);
            return true;
        }
        return false;
    }

    /**
     * Apply food restoration from eating. Returns the new hunger value.
     */
    public static int applyFood(CompoundTag tag, FoodProperties food) {
        int nutrition = food.nutrition();
        float satMod = food.saturation();

        int hungerRestored = (int)(nutrition * FOOD_SCALE);
        int oldHunger = getHunger(tag);
        int newHunger = Math.min(oldHunger + hungerRestored, MAX_HUNGER);
        setHunger(tag, newHunger);

        float satRestored = Math.min(nutrition * satMod * FOOD_SCALE, newHunger);
        setSaturation(tag, Math.min(getSaturation(tag) + satRestored, MAX_SATURATION));

        return newHunger;
    }

    /**
     * Get the HungerState for UI display and effect calculation.
     */
    public static HungerState getState(int hunger) {
        if (hunger <= 0) return HungerState.STARVING;
        if (hunger < 25) return HungerState.FAMISHED;
        if (hunger < 50) return HungerState.HUNGRY;
        if (hunger < 80) return HungerState.ADEQUATE;
        return HungerState.WELL_FED;
    }

    /**
     * Get mood pressure for the given hunger state.
     */
    public static float getMoodPressure(HungerState state) {
        return switch (state) {
            case WELL_FED -> 0.15f;
            case ADEQUATE -> 0;
            case HUNGRY -> -0.33f;
            case FAMISHED -> -0.5f;
            case STARVING -> -0.75f;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public enum HungerState {
        WELL_FED("townstead.hunger.well_fed", 0x55FF55),      // green
        ADEQUATE("townstead.hunger.adequate", 0xFFFFFF),        // white
        HUNGRY("townstead.hunger.hungry", 0xFFAA00),            // gold
        FAMISHED("townstead.hunger.famished", 0xFF5555),        // red
        STARVING("townstead.hunger.starving", 0xAA0000);        // dark red

        private final String translationKey;
        private final int color;

        HungerState(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public String getTranslationKey() { return translationKey; }
        public int getColor() { return color; }
    }

    public enum FarmBlockedReason {
        NONE("none", "townstead.farm.blocked.none"),
        NO_SEEDS("no_seeds", "townstead.farm.blocked.no_seeds"),
        NO_TOOL("no_tool", "townstead.farm.blocked.no_tool"),
        NO_WATER_PLAN("no_water_plan", "townstead.farm.blocked.no_water_plan"),
        UNREACHABLE("unreachable", "townstead.farm.blocked.unreachable"),
        NO_VALID_TARGET("no_valid_target", "townstead.farm.blocked.no_valid_target"),
        OUT_OF_SCOPE("out_of_scope", "townstead.farm.blocked.out_of_scope"),
        UNSUPPORTED_CROP("unsupported_crop", "townstead.farm.blocked.unsupported_crop");

        private final String id;
        private final String translationKey;

        FarmBlockedReason(String id, String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return translationKey;
        }

        public static FarmBlockedReason fromId(String id) {
            for (FarmBlockedReason reason : values()) {
                if (reason.id.equals(id)) return reason;
            }
            return NONE;
        }
    }

    public enum ButcherBlockedReason {
        NONE("none", "townstead.butcher.blocked.none"),
        NO_SMOKER("no_smoker", "townstead.butcher.blocked.no_smoker"),
        NO_INPUT("no_input", "townstead.butcher.blocked.no_input"),
        UNSUPPORTED_RECIPE("unsupported_recipe", "townstead.butcher.blocked.unsupported_recipe"),
        NO_FUEL("no_fuel", "townstead.butcher.blocked.no_fuel"),
        OUTPUT_BLOCKED("output_blocked", "townstead.butcher.blocked.output_blocked"),
        UNREACHABLE("unreachable", "townstead.butcher.blocked.unreachable"),
        OUT_OF_SCOPE("out_of_scope", "townstead.butcher.blocked.out_of_scope"),
        NO_VALID_TARGET("no_valid_target", "townstead.butcher.blocked.no_valid_target");

        private final String id;
        private final String translationKey;

        ButcherBlockedReason(String id, String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return translationKey;
        }

        public static ButcherBlockedReason fromId(String id) {
            for (ButcherBlockedReason reason : values()) {
                if (reason.id.equals(id)) return reason;
            }
            return NONE;
        }
    }
}
