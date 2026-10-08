package com.aetherianartificer.townstead.clothing.wardrobe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the Wardrobe screen set: the village's weather layers, and per villager an off-shift
 * outfit per weekday, a work outfit, weather overrides, and favourites.
 *
 * <p>A day cell holds an MCA skin id, or an outfit template id from before skins. Empty means
 * the villager's own choice. Weekdays are indexed by the calendar's day of the week.</p>
 */
public class WardrobeAssignments extends SavedData {

    public static final String FILE_ID = "townstead_wardrobe";

    private static final String KEY_WEATHER = "weather";
    private static final String KEY_WARM = "warm";
    private static final String KEY_LIGHT = "light";
    private static final String KEY_VILLAGERS = "villagers";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_DAYS = "days";
    private static final String KEY_DAY = "day";
    private static final String KEY_POLICY = "policy";
    private static final String KEY_WORK = "work";
    private static final String KEY_WORK_PROFESSION = "work_profession";
    private static final String KEY_STARRED = "starred";
    private static final String KEY_PICKS = "picks";
    private static final String KEY_SKIN = "skin";
    private static final String KEY_COUNT = "count";

    /** Override states for a villager's weather layers. */
    public static final byte INHERIT = 0;
    public static final byte ON = 1;
    public static final byte OFF = 2;

    /** One villager's settings. */
    public static final class Entry {
        final Map<Integer, String> days = new LinkedHashMap<>();
        String work = "";
        String workProfession = "";
        byte warm = INHERIT;
        byte light = INHERIT;
        final Set<String> starred = new LinkedHashSet<>();
        final Map<String, Integer> picks = new LinkedHashMap<>();

        boolean isEmpty() {
            return days.isEmpty() && work.isEmpty() && warm == INHERIT && light == INHERIT
                    && starred.isEmpty() && picks.isEmpty();
        }

        public String day(int day) {
            return days.getOrDefault(day, "");
        }

        public String work() {
            return work;
        }

        public String workProfession() {
            return workProfession;
        }

        public byte warm() {
            return warm;
        }

        public byte light() {
            return light;
        }

        public Set<String> starred() {
            return starred;
        }

        public Map<String, Integer> picks() {
            return picks;
        }
    }

    private boolean villageWarm = true;
    private boolean villageLight = true;
    private final Map<UUID, Entry> villagers = new LinkedHashMap<>();

    public WardrobeAssignments() {}

    public static WardrobeAssignments get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(WardrobeAssignments::new, WardrobeAssignments::load),
                FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                WardrobeAssignments::load,
                WardrobeAssignments::new,
                FILE_ID);
        *///?}
    }

    //? if >=1.21 {
    public static WardrobeAssignments load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static WardrobeAssignments load(CompoundTag tag) {
    *///?}
        WardrobeAssignments data = new WardrobeAssignments();
        if (tag.contains(KEY_WEATHER, Tag.TAG_COMPOUND)) {
            CompoundTag weather = tag.getCompound(KEY_WEATHER);
            data.villageWarm = !weather.contains(KEY_WARM) || weather.getBoolean(KEY_WARM);
            data.villageLight = !weather.contains(KEY_LIGHT) || weather.getBoolean(KEY_LIGHT);
        }
        ListTag list = tag.getList(KEY_VILLAGERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompound(i);
            if (!saved.hasUUID(KEY_UUID)) continue;
            Entry entry = new Entry();
            ListTag days = saved.getList(KEY_DAYS, Tag.TAG_COMPOUND);
            for (int d = 0; d < days.size(); d++) {
                CompoundTag day = days.getCompound(d);
                String value = day.getString(KEY_POLICY);
                if (!value.isEmpty()) entry.days.put(day.getInt(KEY_DAY), value);
            }
            entry.work = saved.getString(KEY_WORK);
            entry.workProfession = saved.getString(KEY_WORK_PROFESSION);
            entry.warm = saved.getByte(KEY_WARM);
            entry.light = saved.getByte(KEY_LIGHT);
            ListTag starred = saved.getList(KEY_STARRED, Tag.TAG_STRING);
            for (int s = 0; s < starred.size(); s++) entry.starred.add(starred.getString(s));
            ListTag picks = saved.getList(KEY_PICKS, Tag.TAG_COMPOUND);
            for (int p = 0; p < picks.size(); p++) {
                CompoundTag pick = picks.getCompound(p);
                if (pick.getInt(KEY_COUNT) > 0) entry.picks.put(pick.getString(KEY_SKIN), pick.getInt(KEY_COUNT));
            }
            if (!entry.isEmpty()) data.villagers.put(saved.getUUID(KEY_UUID), entry);
        }
        return data;
    }

    //? if >=1.21 {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
    *///?}
        CompoundTag weather = new CompoundTag();
        weather.putBoolean(KEY_WARM, villageWarm);
        weather.putBoolean(KEY_LIGHT, villageLight);
        tag.put(KEY_WEATHER, weather);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> e : villagers.entrySet()) {
            Entry entry = e.getValue();
            if (entry.isEmpty()) continue;
            CompoundTag saved = new CompoundTag();
            saved.putUUID(KEY_UUID, e.getKey());
            ListTag days = new ListTag();
            for (Map.Entry<Integer, String> d : entry.days.entrySet()) {
                CompoundTag day = new CompoundTag();
                day.putInt(KEY_DAY, d.getKey());
                day.putString(KEY_POLICY, d.getValue());
                days.add(day);
            }
            saved.put(KEY_DAYS, days);
            if (!entry.work.isEmpty()) {
                saved.putString(KEY_WORK, entry.work);
                saved.putString(KEY_WORK_PROFESSION, entry.workProfession);
            }
            if (entry.warm != INHERIT) saved.putByte(KEY_WARM, entry.warm);
            if (entry.light != INHERIT) saved.putByte(KEY_LIGHT, entry.light);
            ListTag starred = new ListTag();
            for (String skin : entry.starred) starred.add(StringTag.valueOf(skin));
            saved.put(KEY_STARRED, starred);
            ListTag picks = new ListTag();
            for (Map.Entry<String, Integer> p : entry.picks.entrySet()) {
                CompoundTag pick = new CompoundTag();
                pick.putString(KEY_SKIN, p.getKey());
                pick.putInt(KEY_COUNT, p.getValue());
                picks.add(pick);
            }
            saved.put(KEY_PICKS, picks);
            list.add(saved);
        }
        tag.put(KEY_VILLAGERS, list);
        return tag;
    }

    public boolean villageWarm() {
        return villageWarm;
    }

    public boolean villageLight() {
        return villageLight;
    }

    public void setVillageWeather(boolean warm, boolean light) {
        villageWarm = warm;
        villageLight = light;
        setDirty();
    }

    public @Nullable Entry entry(@Nullable UUID uuid) {
        return uuid == null ? null : villagers.get(uuid);
    }

    private Entry edit(UUID uuid) {
        return villagers.computeIfAbsent(uuid, k -> new Entry());
    }

    private void tidy(UUID uuid) {
        Entry entry = villagers.get(uuid);
        if (entry != null && entry.isEmpty()) villagers.remove(uuid);
        setDirty();
    }

    /** A villager's cell for a weekday, or empty for their own choice. */
    public String villager(@Nullable UUID uuid, int day) {
        Entry entry = entry(uuid);
        return entry == null ? "" : entry.day(day);
    }

    public void setVillager(UUID uuid, int day, @Nullable String value) {
        if (uuid == null) return;
        if (value == null || value.isEmpty()) {
            Entry entry = villagers.get(uuid);
            if (entry != null) entry.days.remove(day);
        } else {
            edit(uuid).days.put(day, value);
        }
        tidy(uuid);
    }

    public void setWork(UUID uuid, @Nullable String skin, String profession) {
        if (uuid == null) return;
        if (skin == null || skin.isEmpty()) {
            Entry entry = villagers.get(uuid);
            if (entry != null) {
                entry.work = "";
                entry.workProfession = "";
            }
        } else {
            Entry entry = edit(uuid);
            entry.work = skin;
            entry.workProfession = profession == null ? "" : profession;
        }
        tidy(uuid);
    }

    public void setWeather(UUID uuid, boolean warmLayer, byte state) {
        if (uuid == null) return;
        Entry entry = edit(uuid);
        if (warmLayer) entry.warm = state;
        else entry.light = state;
        tidy(uuid);
    }

    public void setStarred(UUID uuid, String skin, boolean starred) {
        if (uuid == null || skin == null || skin.isEmpty()) return;
        if (starred) edit(uuid).starred.add(skin);
        else if (villagers.containsKey(uuid)) villagers.get(uuid).starred.remove(skin);
        tidy(uuid);
    }

    public void countPick(UUID uuid, String skin) {
        if (uuid == null || skin == null || skin.isEmpty()) return;
        edit(uuid).picks.merge(skin, 1, Integer::sum);
        setDirty();
    }

    /** Whether the villager's warm layers (true) or light layers (false) are on. */
    public boolean layersOn(@Nullable UUID uuid, boolean warmLayer) {
        Entry entry = entry(uuid);
        byte state = entry == null ? INHERIT : warmLayer ? entry.warm : entry.light;
        if (state == ON) return true;
        if (state == OFF) return false;
        return warmLayer ? villageWarm : villageLight;
    }

    /** The day cells as a full row, one entry per weekday. */
    public List<String> row(@Nullable UUID uuid, int daysPerWeek) {
        List<String> out = new ArrayList<>(daysPerWeek);
        for (int d = 0; d < daysPerWeek; d++) out.add(villager(uuid, d));
        return out;
    }
}
