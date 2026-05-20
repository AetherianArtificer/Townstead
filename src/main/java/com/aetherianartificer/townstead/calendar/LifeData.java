package com.aetherianartificer.townstead.calendar;

import net.minecraft.nbt.CompoundTag;

/**
 * Per-villager birth data, persisted in the {@code LIFE_DATA} attachment
 * (NeoForge) or via {@code getPersistentData()} (Forge). Pattern mirrors
 * {@link com.aetherianartificer.townstead.hunger.HungerData}.
 *
 * Two fields:
 * <ul>
 *   <li>{@code birthWorldDay} — absolute, signed. Negative values represent
 *       villagers older than the save's start (pregenerated adults).</li>
 *   <li>{@code stamped} — {@code false} when the date was fabricated from
 *       {@code AgeState} on first encounter, {@code true} when stamped from a
 *       real birth event (future, currently unused). Lets later phases refine
 *       fabricated DOBs without losing real ones.</li>
 * </ul>
 *
 * The {@code hasBirth} marker is a separate boolean so a fabricated DOB of
 * {@code 0} is distinguishable from "not yet stamped at all."
 */
public final class LifeData {
    private static final String KEY_BIRTH_WORLD_DAY = "birthWorldDay";
    private static final String KEY_STAMPED = "stamped";
    private static final String KEY_HAS_BIRTH = "hasBirth";

    private LifeData() {}

    public static boolean hasBirth(CompoundTag tag) {
        return tag.getBoolean(KEY_HAS_BIRTH);
    }

    public static long getBirthWorldDay(CompoundTag tag) {
        return tag.getLong(KEY_BIRTH_WORLD_DAY);
    }

    public static boolean isStamped(CompoundTag tag) {
        return tag.getBoolean(KEY_STAMPED);
    }

    public static void setBirth(CompoundTag tag, long birthWorldDay, boolean stamped) {
        tag.putLong(KEY_BIRTH_WORLD_DAY, birthWorldDay);
        tag.putBoolean(KEY_STAMPED, stamped);
        tag.putBoolean(KEY_HAS_BIRTH, true);
    }
}
