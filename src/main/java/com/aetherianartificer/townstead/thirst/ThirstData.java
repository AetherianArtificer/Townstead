package com.aetherianartificer.townstead.thirst;

import net.minecraft.nbt.CompoundTag;

public final class ThirstData {

    public static final int DEFAULT_THIRST = 20;
    public static final int DEFAULT_QUENCHED = 5;

    public static final int MAX_THIRST = 20;
    public static final int MAX_QUENCHED = 20;

    // Threshold is 5x hunger's (4.0) to compensate for the 0-20 scale vs hunger's 0-100.
    // This makes thirst drain proportionally similar to hunger from activity.
    public static final float EXHAUSTION_THRESHOLD = 20.0f;
    public static final float EXHAUSTION_CHORE = 0.012f;
    public static final float EXHAUSTION_GUARD_PATROL = 0.008f;
    public static final float EXHAUSTION_COMBAT = 0.02f;
    public static final float EXHAUSTION_MOVEMENT_PER_BLOCK = 0.01f;
    public static final float EXHAUSTION_AWAKE_BASELINE = 0.0003f;

    public static final int BREAKFAST_THRESHOLD = 16;
    public static final int LUNCH_THRESHOLD = 14;
    public static final int DINNER_THRESHOLD = 12;
    public static final int ADEQUATE_THRESHOLD = 12;
    public static final int EMERGENCY_THRESHOLD = 4;

    public static final int MOOD_CHECK_INTERVAL = 2400;
    public static final int PASSIVE_DRAIN_INTERVAL = 2000;
    public static final long MIN_DRINK_INTERVAL = 200;

    public static final int SPEED_PENALTY_THRESHOLD = 3;
    public static final double SPEED_PENALTY_AMOUNT = -0.20;

    public static final int DAMAGE_INTERVAL = 40;

    private static final String KEY_THIRST = "thirst";
    private static final String KEY_QUENCHED = "quenched";
    private static final String KEY_EXHAUSTION = "thirstExhaustion";
    private static final String KEY_LAST_DRANK_TIME = "lastDrankTime";
    private static final String KEY_DRINKING_MODE = "drinkingMode";
    private static final String KEY_MOOD_DRIFT = "thirstMoodDrift";
    private static final String KEY_DAMAGE_TIMER = "thirstDamageTimer";

    public static final String EDITOR_KEY_THIRST = "townstead_thirst";
    public static final String EDITOR_KEY_QUENCHED = "townstead_quenched";
    public static final String EDITOR_KEY_EXHAUSTION = "townstead_thirst_exhaustion";

    private ThirstData() {}

    public static int getThirst(CompoundTag tag) {
        return tag.contains(KEY_THIRST) ? tag.getInt(KEY_THIRST) : DEFAULT_THIRST;
    }

    public static void setThirst(CompoundTag tag, int value) {
        tag.putInt(KEY_THIRST, clamp(value, 0, MAX_THIRST));
    }

    public static int getQuenched(CompoundTag tag) {
        return tag.contains(KEY_QUENCHED) ? tag.getInt(KEY_QUENCHED) : DEFAULT_QUENCHED;
    }

    public static void setQuenched(CompoundTag tag, int value) {
        int thirst = getThirst(tag);
        tag.putInt(KEY_QUENCHED, Math.max(0, Math.min(value, Math.min(MAX_QUENCHED, thirst))));
    }

    public static float getExhaustion(CompoundTag tag) {
        return tag.getFloat(KEY_EXHAUSTION);
    }

    public static void setExhaustion(CompoundTag tag, float value) {
        tag.putFloat(KEY_EXHAUSTION, Math.max(0f, value));
    }

    public static long getLastDrankTime(CompoundTag tag) {
        return tag.getLong(KEY_LAST_DRANK_TIME);
    }

    public static void setLastDrankTime(CompoundTag tag, long tick) {
        tag.putLong(KEY_LAST_DRANK_TIME, tick);
    }

    public static boolean isDrinkingMode(CompoundTag tag) {
        return tag.getBoolean(KEY_DRINKING_MODE);
    }

    public static void setDrinkingMode(CompoundTag tag, boolean drinkingMode) {
        tag.putBoolean(KEY_DRINKING_MODE, drinkingMode);
    }

    public static float getMoodDrift(CompoundTag tag) {
        return tag.getFloat(KEY_MOOD_DRIFT);
    }

    public static void setMoodDrift(CompoundTag tag, float value) {
        tag.putFloat(KEY_MOOD_DRIFT, Math.max(-4f, Math.min(value, 4f)));
    }

    public static int getDamageTimer(CompoundTag tag) {
        return tag.getInt(KEY_DAMAGE_TIMER);
    }

    public static void setDamageTimer(CompoundTag tag, int value) {
        tag.putInt(KEY_DAMAGE_TIMER, Math.max(0, value));
    }

    public static boolean processExhaustion(CompoundTag tag) {
        float exhaustion = getExhaustion(tag);
        if (exhaustion < EXHAUSTION_THRESHOLD) return false;

        boolean changed = false;
        while (exhaustion >= EXHAUSTION_THRESHOLD) {
            exhaustion -= EXHAUSTION_THRESHOLD;
            int quenched = getQuenched(tag);
            if (quenched > 0) {
                setQuenched(tag, quenched - 1);
                changed = true;
                continue;
            }
            int thirst = getThirst(tag);
            if (thirst > 0) {
                setThirst(tag, thirst - 1);
                changed = true;
            }
        }

        setExhaustion(tag, exhaustion);
        return changed;
    }

    public static boolean passiveDrain(CompoundTag tag) {
        int quenched = getQuenched(tag);
        if (quenched > 0) {
            setQuenched(tag, quenched - 1);
            return true;
        }
        int thirst = getThirst(tag);
        if (thirst > 0) {
            setThirst(tag, thirst - 1);
            return true;
        }
        return false;
    }

    public static void applyDrink(CompoundTag tag, int hydration, int quenchedGain, boolean convertExtraHydrationToQuenched) {
        int oldThirst = getThirst(tag);
        int extraQuenched = convertExtraHydrationToQuenched ? Math.max(oldThirst + hydration - MAX_THIRST, 0) : 0;
        int newThirst = Math.min(MAX_THIRST, oldThirst + Math.max(0, hydration));
        setThirst(tag, newThirst);
        setQuenched(tag, getQuenched(tag) + Math.max(0, quenchedGain) + extraQuenched);
    }

    public static ThirstState getState(int thirst) {
        if (thirst <= 0) return ThirstState.DEHYDRATED;
        if (thirst <= 4) return ThirstState.PARCHED;
        if (thirst <= 8) return ThirstState.THIRSTY;
        if (thirst <= 15) return ThirstState.HYDRATED;
        return ThirstState.QUENCHED;
    }

    public static float getMoodPressure(ThirstState state) {
        return switch (state) {
            case QUENCHED -> 0.15f;
            case HYDRATED -> 0f;
            case THIRSTY -> -0.33f;
            case PARCHED -> -0.5f;
            case DEHYDRATED -> -0.75f;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public enum ThirstState {
        QUENCHED("townstead.thirst.quenched", 0x55D6FF),
        HYDRATED("townstead.thirst.hydrated", 0xFFFFFF),
        THIRSTY("townstead.thirst.thirsty", 0xFFAA00),
        PARCHED("townstead.thirst.parched", 0xFF5555),
        DEHYDRATED("townstead.thirst.dehydrated", 0xAA0000);

        private final String translationKey;
        private final int color;

        ThirstState(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public String getTranslationKey() {
            return translationKey;
        }

        public int getColor() {
            return color;
        }
    }
}
