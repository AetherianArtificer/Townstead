package com.aetherianartificer.townstead.clothing.wardrobe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the Wardrobe screen assigned: one outfit template (a wardrobe policy id) per weekday for
 * the village, and per villager on top. Empty means "follow the row above": a villager cell
 * follows the Village row, a Village cell follows the weather.
 *
 * <p>Stored with the overworld like week plans. Weekdays are indexed by the calendar's day of
 * the week, so a 5-day or 12-day week needs no migration.</p>
 */
public class WardrobeAssignments extends SavedData {

    public static final String FILE_ID = "townstead_wardrobe";

    private static final String KEY_VILLAGE = "village";
    private static final String KEY_VILLAGERS = "villagers";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_DAYS = "days";
    private static final String KEY_DAY = "day";
    private static final String KEY_POLICY = "policy";

    private final Map<Integer, String> village = new LinkedHashMap<>();
    private final Map<UUID, Map<Integer, String>> villagers = new LinkedHashMap<>();

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
        readDays(tag.getList(KEY_VILLAGE, Tag.TAG_COMPOUND), data.village);
        ListTag list = tag.getList(KEY_VILLAGERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID(KEY_UUID)) continue;
            Map<Integer, String> days = new LinkedHashMap<>();
            readDays(entry.getList(KEY_DAYS, Tag.TAG_COMPOUND), days);
            if (!days.isEmpty()) data.villagers.put(entry.getUUID(KEY_UUID), days);
        }
        return data;
    }

    private static void readDays(ListTag list, Map<Integer, String> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag day = list.getCompound(i);
            String policy = day.getString(KEY_POLICY);
            if (!policy.isEmpty()) into.put(day.getInt(KEY_DAY), policy);
        }
    }

    private static ListTag writeDays(Map<Integer, String> days) {
        ListTag list = new ListTag();
        for (Map.Entry<Integer, String> e : days.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            CompoundTag day = new CompoundTag();
            day.putInt(KEY_DAY, e.getKey());
            day.putString(KEY_POLICY, e.getValue());
            list.add(day);
        }
        return list;
    }

    //? if >=1.21 {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
    *///?}
        tag.put(KEY_VILLAGE, writeDays(village));
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Map<Integer, String>> e : villagers.entrySet()) {
            ListTag days = writeDays(e.getValue());
            if (days.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_UUID, e.getKey());
            entry.put(KEY_DAYS, days);
            list.add(entry);
        }
        tag.put(KEY_VILLAGERS, list);
        return tag;
    }

    /** The Village row's template for a weekday, or empty for the weather. */
    public String village(int day) {
        return village.getOrDefault(day, "");
    }

    /** A villager's own template for a weekday, or empty to follow the Village row. */
    public String villager(@Nullable UUID uuid, int day) {
        if (uuid == null) return "";
        Map<Integer, String> days = villagers.get(uuid);
        return days == null ? "" : days.getOrDefault(day, "");
    }

    public void setVillage(int day, @Nullable String policy) {
        if (policy == null || policy.isEmpty()) village.remove(day);
        else village.put(day, policy);
        setDirty();
    }

    public void setVillager(UUID uuid, int day, @Nullable String policy) {
        if (uuid == null) return;
        if (policy == null || policy.isEmpty()) {
            Map<Integer, String> days = villagers.get(uuid);
            if (days != null) {
                days.remove(day);
                if (days.isEmpty()) villagers.remove(uuid);
            }
        } else {
            villagers.computeIfAbsent(uuid, k -> new LinkedHashMap<>()).put(day, policy);
        }
        setDirty();
    }

    /** The Village row as a list, one entry per weekday, empty strings where nothing is set. */
    public List<String> villageRow(int daysPerWeek) {
        List<String> out = new ArrayList<>(daysPerWeek);
        for (int d = 0; d < daysPerWeek; d++) out.add(village(d));
        return out;
    }

    /** Every villager with at least one cell set, each as a full row. */
    public Map<UUID, List<String>> villagerRows(int daysPerWeek) {
        Map<UUID, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<Integer, String>> e : villagers.entrySet()) {
            List<String> row = new ArrayList<>(daysPerWeek);
            for (int d = 0; d < daysPerWeek; d++) row.add(e.getValue().getOrDefault(d, ""));
            out.put(e.getKey(), row);
        }
        return out;
    }
}
