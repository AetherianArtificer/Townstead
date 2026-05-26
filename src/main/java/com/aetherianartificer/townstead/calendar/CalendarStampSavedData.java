package com.aetherianartificer.townstead.calendar;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Overworld-level persistence of player-placed calendar stamps, shared server-wide. */
public class CalendarStampSavedData extends SavedData {
    public static final String FILE_ID = "townstead_calendar_stamps";

    /** Hard cap so a runaway client can't bloat the save / sync packet. */
    public static final int MAX_STAMPS = 4096;
    public static final int MAX_CAPTION_LEN = 256;

    private static final String KEY_STAMPS = "stamps";
    private static final String K_ID = "id";
    private static final String K_TEX = "tex";
    private static final String K_SRC = "src";
    private static final String K_CAPTION = "caption";
    private static final String K_YEAR = "year";
    private static final String K_MONTH = "month";
    private static final String K_DAY = "day";
    private static final String K_OFFX = "offX";
    private static final String K_OFFY = "offY";
    private static final String K_BY = "by";
    private static final String K_PUBLIC = "public";

    // Insertion-ordered so render/z-order is stable (last placed draws on top).
    private final Map<UUID, CalendarStamp> stamps = new LinkedHashMap<>();

    public CalendarStampSavedData() {}

    public static CalendarStampSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(CalendarStampSavedData::new, CalendarStampSavedData::load),
                FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                CalendarStampSavedData::load,
                CalendarStampSavedData::new,
                FILE_ID);
        *///?}
    }

    //? if >=1.21 {
    public static CalendarStampSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static CalendarStampSavedData load(CompoundTag tag) {
    *///?}
        CalendarStampSavedData data = new CalendarStampSavedData();
        if (tag.contains(KEY_STAMPS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_STAMPS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                try {
                    UUID id = e.getUUID(K_ID);
                    CalendarStamp stamp = new CalendarStamp(
                            id,
                            e.getString(K_TEX),
                            e.getString(K_SRC),
                            e.getString(K_CAPTION),
                            e.getInt(K_YEAR),
                            e.getInt(K_MONTH),
                            e.getInt(K_DAY),
                            e.getFloat(K_OFFX),
                            e.getFloat(K_OFFY),
                            e.getUUID(K_BY),
                            e.getBoolean(K_PUBLIC));
                    data.stamps.put(id, stamp);
                } catch (Exception ignored) {
                    // Skip a malformed entry rather than dropping the whole list.
                }
            }
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
        ListTag list = new ListTag();
        for (CalendarStamp s : stamps.values()) {
            CompoundTag e = new CompoundTag();
            e.putUUID(K_ID, s.id());
            e.putString(K_TEX, s.textureId());
            e.putString(K_SRC, s.sourcePack());
            e.putString(K_CAPTION, s.caption());
            e.putInt(K_YEAR, s.year());
            e.putInt(K_MONTH, s.monthIndex());
            e.putInt(K_DAY, s.dayOfMonth());
            e.putFloat(K_OFFX, s.offX());
            e.putFloat(K_OFFY, s.offY());
            e.putUUID(K_BY, s.placedBy());
            e.putBoolean(K_PUBLIC, s.isPublic());
            list.add(e);
        }
        tag.put(KEY_STAMPS, list);
        return tag;
    }

    public Collection<CalendarStamp> all() {
        return stamps.values();
    }

    @Nullable
    public CalendarStamp get(UUID id) {
        return stamps.get(id);
    }

    /** @return false if the stamp cap is reached (caller should ignore the add). */
    public boolean add(CalendarStamp stamp) {
        if (stamps.size() >= MAX_STAMPS && !stamps.containsKey(stamp.id())) return false;
        stamps.put(stamp.id(), stamp);
        setDirty();
        return true;
    }

    /** Replace an existing stamp in place (preserves z-order). No-op if absent. */
    public void replace(CalendarStamp stamp) {
        if (stamps.containsKey(stamp.id())) {
            stamps.put(stamp.id(), stamp);
            setDirty();
        }
    }

    public boolean remove(UUID id) {
        boolean removed = stamps.remove(id) != null;
        if (removed) setDirty();
        return removed;
    }
}
