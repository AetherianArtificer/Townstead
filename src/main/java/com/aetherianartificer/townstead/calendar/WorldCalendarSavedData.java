package com.aetherianartificer.townstead.calendar;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Overworld-level persistence of the Townstead world calendar.
 *
 * Stored state is intentionally small:
 *   - {@code worldDayCounter} — monotonic day count since save start, advanced
 *     by {@link WorldCalendarTicker} from {@code level.getDayTime()} deltas
 *     so it survives Time Control / slow-time mods (see memory
 *     {@code project_time_scaling}).
 *   - {@code subDayResidueTicks} — sub-day remainder of dayTime accumulated
 *     between day rollovers (0..23999).
 *   - {@code lastDayTimeSample} — last observed {@code overworld.getDayTime()}.
 *     Used by the ticker to compute deltas across reloads safely.
 *   - {@code epochYearOffset} — admin-configurable; added to the year derived
 *     from {@code worldDayCounter} when displayed.
 *   - {@code activeProfileOverride} — non-null = admin pinned a specific
 *     profile via {@code /townstead calendar set-profile}; null = use the
 *     config / auto-detection path.
 */
public class WorldCalendarSavedData extends SavedData {
    public static final String FILE_ID = "townstead_world_calendar";

    private static final String KEY_DAY = "worldDay";
    private static final String KEY_RESIDUE = "residueTicks";
    private static final String KEY_LAST_SAMPLE = "lastSample";
    private static final String KEY_EPOCH = "epochYearOffset";
    private static final String KEY_PROFILE_OVERRIDE = "profileOverride";
    private static final String KEY_HAS_SAMPLE = "hasSample";
    private static final String KEY_VILLAGE_BIRTHS = "villageBirths";
    private static final String KEY_VB_DIM = "dim";
    private static final String KEY_VB_ID = "id";
    private static final String KEY_VB_DAY = "day";
    private static final String KEY_VB_PLAYER = "playerFounded";
    private static final String KEY_INITIALIZED = "calendarInitialized";
    private static final String KEY_LAST_REAL_MILLIS = "lastRealMillisAtSave";
    private static final String KEY_HAS_LAST_REAL_MILLIS = "hasLastRealMillis";
    private static final String KEY_TIME_MODE_OVERRIDE = "timeModeOverride";
    private static final String KEY_LIFE_EPOCH_SHIFT = "lifeEpochShift";

    public static final int DEFAULT_EPOCH_YEAR_OFFSET = 1000;
    private static final long MILLIS_PER_DAY = 86_400_000L;

    private long worldDayCounter = 0L;
    private long subDayResidueTicks = 0L;
    private long lastDayTimeSample = 0L;
    private boolean hasLastSample = false;
    private int epochYearOffset = DEFAULT_EPOCH_YEAR_OFFSET;
    // Days of pure calendar relabeling (date edits) absorbed away from the
    // biological clock. Biological "today" = worldDayCounter - lifeEpochShift, so
    // re-dating the calendar never ages villagers, while real elapsed time still does.
    private long lifeEpochShift = 0L;
    private boolean calendarInitialized = false;
    private long lastRealMillisAtSave = 0L;
    private boolean hasLastRealMillis = false;
    private boolean realClockCatchupApplied = false;
    @Nullable
    private String timeModeOverride = null;
    @Nullable
    private ResourceLocation activeProfileOverride = null;
    private final Map<VillageKey, VillageBirth> villageBirths = new HashMap<>();

    /**
     * Per-dimension village id. Village ids are unique within a dimension
     * (MCA's VillageManager), not across the server.
     */
    public record VillageKey(ResourceLocation dimension, int villageId) {}

    /**
     * Establishment record. {@code worldDay} is signed; negative means the
     * village predates the save's start (pregenerated). {@code playerFounded}
     * distinguishes "today, by the player" from "ancient, fabricated."
     */
    public record VillageBirth(long worldDay, boolean playerFounded) {}

    public WorldCalendarSavedData() {}

    public static WorldCalendarSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(WorldCalendarSavedData::new, WorldCalendarSavedData::load),
                FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                WorldCalendarSavedData::load,
                WorldCalendarSavedData::new,
                FILE_ID);
        *///?}
    }

    //? if >=1.21 {
    public static WorldCalendarSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static WorldCalendarSavedData load(CompoundTag tag) {
    *///?}
        WorldCalendarSavedData data = new WorldCalendarSavedData();
        if (tag.contains(KEY_DAY)) data.worldDayCounter = tag.getLong(KEY_DAY);
        if (tag.contains(KEY_RESIDUE)) data.subDayResidueTicks = tag.getLong(KEY_RESIDUE);
        if (tag.contains(KEY_LAST_SAMPLE)) data.lastDayTimeSample = tag.getLong(KEY_LAST_SAMPLE);
        if (tag.contains(KEY_HAS_SAMPLE)) data.hasLastSample = tag.getBoolean(KEY_HAS_SAMPLE);
        if (tag.contains(KEY_EPOCH)) data.epochYearOffset = tag.getInt(KEY_EPOCH);
        if (tag.contains(KEY_LIFE_EPOCH_SHIFT)) data.lifeEpochShift = tag.getLong(KEY_LIFE_EPOCH_SHIFT);
        if (tag.contains(KEY_INITIALIZED)) data.calendarInitialized = tag.getBoolean(KEY_INITIALIZED);
        if (tag.contains(KEY_LAST_REAL_MILLIS)) data.lastRealMillisAtSave = tag.getLong(KEY_LAST_REAL_MILLIS);
        if (tag.contains(KEY_HAS_LAST_REAL_MILLIS)) data.hasLastRealMillis = tag.getBoolean(KEY_HAS_LAST_REAL_MILLIS);
        if (tag.contains(KEY_TIME_MODE_OVERRIDE)) {
            String s = tag.getString(KEY_TIME_MODE_OVERRIDE);
            if (!s.isBlank()) data.timeModeOverride = s;
        }
        if (tag.contains(KEY_PROFILE_OVERRIDE)) {
            String s = tag.getString(KEY_PROFILE_OVERRIDE);
            if (!s.isBlank()) {
                try {
                    data.activeProfileOverride = parseRl(s);
                } catch (Exception ignored) {}
            }
        }
        if (tag.contains(KEY_VILLAGE_BIRTHS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_VILLAGE_BIRTHS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                ResourceLocation dim;
                try {
                    dim = parseRl(entry.getString(KEY_VB_DIM));
                } catch (Exception ex) {
                    continue;
                }
                int id = entry.getInt(KEY_VB_ID);
                long day = entry.getLong(KEY_VB_DAY);
                boolean playerFounded = entry.getBoolean(KEY_VB_PLAYER);
                data.villageBirths.put(new VillageKey(dim, id), new VillageBirth(day, playerFounded));
            }
        }
        return data;
    }

    private static ResourceLocation parseRl(String s) {
        //? if >=1.21 {
        return ResourceLocation.parse(s);
        //?} else {
        /*return new ResourceLocation(s);
        *///?}
    }

    //? if >=1.21 {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
    *///?}
        tag.putLong(KEY_DAY, worldDayCounter);
        tag.putLong(KEY_RESIDUE, subDayResidueTicks);
        tag.putLong(KEY_LAST_SAMPLE, lastDayTimeSample);
        tag.putBoolean(KEY_HAS_SAMPLE, hasLastSample);
        tag.putInt(KEY_EPOCH, epochYearOffset);
        tag.putLong(KEY_LIFE_EPOCH_SHIFT, lifeEpochShift);
        tag.putBoolean(KEY_INITIALIZED, calendarInitialized);
        // Stamp current wall-clock millis at save time so Animal Crossing mode
        // can compute real-days-elapsed on the next load. Always written
        // (regardless of time_mode) so the timestamp is available if the user
        // switches modes between sessions.
        long nowMillis = System.currentTimeMillis();
        this.lastRealMillisAtSave = nowMillis;
        this.hasLastRealMillis = true;
        tag.putLong(KEY_LAST_REAL_MILLIS, nowMillis);
        tag.putBoolean(KEY_HAS_LAST_REAL_MILLIS, true);
        if (timeModeOverride != null) {
            tag.putString(KEY_TIME_MODE_OVERRIDE, timeModeOverride);
        }
        if (activeProfileOverride != null) {
            tag.putString(KEY_PROFILE_OVERRIDE, activeProfileOverride.toString());
        }
        if (!villageBirths.isEmpty()) {
            ListTag list = new ListTag();
            for (Map.Entry<VillageKey, VillageBirth> entry : villageBirths.entrySet()) {
                CompoundTag ct = new CompoundTag();
                ct.putString(KEY_VB_DIM, entry.getKey().dimension().toString());
                ct.putInt(KEY_VB_ID, entry.getKey().villageId());
                ct.putLong(KEY_VB_DAY, entry.getValue().worldDay());
                ct.putBoolean(KEY_VB_PLAYER, entry.getValue().playerFounded());
                list.add(ct);
            }
            tag.put(KEY_VILLAGE_BIRTHS, list);
        }
        return tag;
    }

    @Nullable
    public VillageBirth getVillageBirth(VillageKey key) {
        return villageBirths.get(key);
    }

    public void putVillageBirth(VillageKey key, VillageBirth birth) {
        VillageBirth prev = villageBirths.put(key, birth);
        if (prev == null || !prev.equals(birth)) {
            setDirty();
        }
    }

    public long worldDayCounter() { return worldDayCounter; }
    public long subDayResidueTicks() { return subDayResidueTicks; }
    public boolean hasLastSample() { return hasLastSample; }
    public long lastDayTimeSample() { return lastDayTimeSample; }
    public int epochYearOffset() { return epochYearOffset; }
    public long lifeEpochShift() { return lifeEpochShift; }
    public boolean calendarInitialized() { return calendarInitialized; }
    @Nullable
    public ResourceLocation activeProfileOverride() { return activeProfileOverride; }
    @Nullable
    public String timeModeOverride() { return timeModeOverride; }

    /**
     * Set the world-level time-mode override. Null clears the override and
     * falls back to the {@code townstead.calendar.timeMode} config value.
     */
    public void setTimeModeOverride(@Nullable String mode) {
        if (java.util.Objects.equals(this.timeModeOverride, mode)) return;
        this.timeModeOverride = mode;
        setDirty();
    }

    public void markCalendarInitialized() {
        if (!this.calendarInitialized) {
            this.calendarInitialized = true;
            setDirty();
        }
    }

    public void setEpochYearOffset(int offset) {
        if (this.epochYearOffset != offset) {
            this.epochYearOffset = offset;
            setDirty();
        }
    }

    public void setLifeEpochShift(long shift) {
        if (this.lifeEpochShift != shift) {
            this.lifeEpochShift = shift;
            setDirty();
        }
    }

    public void setActiveProfileOverride(@Nullable ResourceLocation id) {
        if (id == null ? this.activeProfileOverride != null
                : !id.equals(this.activeProfileOverride)) {
            this.activeProfileOverride = id;
            setDirty();
        }
    }

    /**
     * Called by {@link WorldCalendarTicker} after applying a dayTime delta.
     * Persists silently — caller decides when to mark dirty to limit IO.
     */
    public void advance(long newSample, long deltaTicks, int daysAdvanced) {
        this.lastDayTimeSample = newSample;
        this.hasLastSample = true;
        this.subDayResidueTicks = Math.floorMod(this.subDayResidueTicks + deltaTicks, 24000L);
        if (daysAdvanced != 0) {
            this.worldDayCounter += daysAdvanced;
            setDirty();
        }
    }

    /**
     * Initialize the last-sample baseline without advancing the counter. Used
     * on first tick after world load to avoid a phantom day jump. Cheap and
     * idempotent: only writes on the very first call.
     */
    public void primeSample(long sample) {
        if (!hasLastSample) {
            this.lastDayTimeSample = sample;
            this.hasLastSample = true;
            setDirty();
        }
    }

    /** Force-set the counter (admin commands only). */
    public void setWorldDayCounter(long day) {
        if (this.worldDayCounter != day) {
            this.worldDayCounter = day;
            setDirty();
        }
    }

    public boolean realClockCatchupApplied() { return realClockCatchupApplied; }

    public void markRealClockCatchupApplied() { this.realClockCatchupApplied = true; }

    /**
     * Advance {@link #worldDayCounter} by the number of whole real-world days
     * that have passed since {@link #lastRealMillisAtSave}. Used by the
     * {@code real_clock} time mode on world load: villagers visibly age and
     * the calendar moves forward to "today" even though no Minecraft time
     * passed while the world was off.
     *
     * <p>Returns the number of days added (0 if no timestamp recorded yet,
     * or fewer than 24 real hours elapsed, or the system clock moved
     * backward). Caller is responsible for any sync-broadcast.</p>
     *
     * <p>The timestamp is advanced by exactly {@code daysAdded × 86_400_000ms}
     * so the sub-day remainder carries forward — if you load 1.5 days after
     * the last save, this adds 1 day now and leaves 0.5 day in the bank for
     * a subsequent load.</p>
     */
    public int applyRealClockCatchup() {
        if (!hasLastRealMillis) return 0;
        long now = System.currentTimeMillis();
        long elapsed = now - lastRealMillisAtSave;
        if (elapsed <= 0L) return 0;
        long days = elapsed / MILLIS_PER_DAY;
        if (days <= 0L) return 0;
        this.worldDayCounter += days;
        this.lastRealMillisAtSave += days * MILLIS_PER_DAY;
        setDirty();
        return (int) Math.min(days, Integer.MAX_VALUE);
    }
}
