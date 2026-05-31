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

    /**
     * Transient editor command keys, written into the editor's {@code villagerData}
     * and read server-side on commit. Never persisted on normal saves. They are
     * mutually exclusive: editing one control clears the others so the server has
     * a single unambiguous instruction.
     * <ul>
     *   <li>{@link #EDITOR_KEY_BIO_AGE_DAYS} — biological age in days (mortal age
     *       slider); server stamps {@code birth = today - bioAge}.</li>
     *   <li>{@link #EDITOR_KEY_BIRTH_YEAR}/{@code _MONTH}/{@code _DAY} — an exact
     *       date of birth from the debug date picker; server converts via the
     *       active calendar profile.</li>
     *   <li>{@link #EDITOR_KEY_FROZEN_STAGE_INDEX} — the cycle index an immortal
     *       villager is frozen at (immortal appearance slider); leaves the date of
     *       birth / calendar age untouched.</li>
     * </ul>
     */
    public static final String EDITOR_KEY_BIO_AGE_DAYS = "TownsteadEditorBioAgeDays";
    public static final String EDITOR_KEY_BIRTH_YEAR = "TownsteadEditorBirthYear";
    public static final String EDITOR_KEY_BIRTH_MONTH = "TownsteadEditorBirthMonth";
    public static final String EDITOR_KEY_BIRTH_DAY = "TownsteadEditorBirthDay";
    public static final String EDITOR_KEY_FROZEN_STAGE_INDEX = "TownsteadEditorFrozenStage";

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
