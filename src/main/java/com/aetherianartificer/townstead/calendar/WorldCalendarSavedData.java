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

    private long worldDayCounter = 0L;
    private long subDayResidueTicks = 0L;
    private long lastDayTimeSample = 0L;
    private boolean hasLastSample = false;
    private int epochYearOffset = 1000;
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
    @Nullable
    public ResourceLocation activeProfileOverride() { return activeProfileOverride; }

    public void setEpochYearOffset(int offset) {
        if (this.epochYearOffset != offset) {
            this.epochYearOffset = offset;
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
     * Initialize the last-sample baseline without advancing the counter going
     * forward. On the very first prime (no prior sample AND counter still at
     * its default 0), retroactively seed the counter to {@code sample / 24000}
     * so saves that existed before Townstead was installed get credited the
     * vanilla days elapsed since world creation. Fresh worlds (sample=0) seed
     * to 0, a no-op. Once {@code hasLastSample} is true, the delta-based
     * ticker takes over and stays Time-Control-safe.
     */
    public void primeSample(long sample) {
        if (!hasLastSample) {
            this.lastDayTimeSample = sample;
            this.hasLastSample = true;
            if (this.worldDayCounter == 0L) {
                this.worldDayCounter = Math.max(0L, sample / 24000L);
            }
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
}
