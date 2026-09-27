package com.aetherianartificer.townstead.livery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Proclaimed liveries, keyed like heraldry ({@code settlement:...}, {@code faction:...}), and the
 * players who chose to wear their faction's. A body with no entry wears its culture's default.
 */
public final class LiverySavedData extends SavedData {
    public static final String FILE_ID = "townstead_livery";

    /**
     * {@code revision} counts proclamations, so a drafted change can tell if it went stale. A null
     * style is a proclaimed return to the culture's default; the entry stays to keep the count.
     */
    public record Entry(@Nullable ResourceLocation style, int primary, int secondary, long revision) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Set<UUID> wearers = new LinkedHashSet<>();

    public static LiverySavedData get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(LiverySavedData::new, LiverySavedData::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(
                LiverySavedData::load, LiverySavedData::new, FILE_ID);
        *///?}
    }

    public @Nullable Entry get(String actor) { return entries.get(actor); }

    public long revision(String actor) {
        Entry entry = entries.get(actor);
        return entry == null ? 0 : entry.revision();
    }

    /**
     * Proclaims a livery, or with a null style returns the body to its culture's default. False when
     * another was proclaimed since this one was drafted.
     */
    public boolean publish(String actor, @Nullable ResourceLocation style, int primary, int secondary, long expected) {
        if (revision(actor) != expected) return false;
        entries.put(actor, new Entry(style, primary, secondary, expected + 1));
        setDirty();
        return true;
    }

    public boolean wears(UUID player) { return wearers.contains(player); }

    public void setWears(UUID player, boolean wear) {
        if (wear ? wearers.add(player) : wearers.remove(player)) setDirty();
    }

    //? if >=1.21 {
    public static LiverySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static LiverySavedData load(CompoundTag tag) {
    *///?}
        LiverySavedData data = new LiverySavedData();
        CompoundTag all = tag.getCompound("entries");
        for (String actor : all.getAllKeys()) {
            CompoundTag entry = all.getCompound(actor);
            ResourceLocation style = entry.getString("style").isEmpty() ? null : ResourceLocation.tryParse(entry.getString("style"));
            data.entries.put(actor, new Entry(style, entry.getInt("primary"), entry.getInt("secondary"), entry.getLong("revision")));
        }
        ListTag wearers = tag.getList("wearers", Tag.TAG_STRING);
        for (int i = 0; i < wearers.size(); i++) {
            try {
                data.wearers.add(UUID.fromString(wearers.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // A malformed id is dropped.
            }
        }
        return data;
    }

    //? if >=1.21 {
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override public CompoundTag save(CompoundTag tag) {
    *///?}
        CompoundTag all = new CompoundTag();
        entries.forEach((actor, entry) -> {
            CompoundTag one = new CompoundTag();
            one.putString("style", entry.style() == null ? "" : entry.style().toString());
            one.putInt("primary", entry.primary());
            one.putInt("secondary", entry.secondary());
            one.putLong("revision", entry.revision());
            all.put(actor, one);
        });
        tag.put("entries", all);
        ListTag list = new ListTag();
        wearers.forEach(uuid -> list.add(StringTag.valueOf(uuid.toString())));
        tag.put("wearers", list);
        return tag;
    }
}
