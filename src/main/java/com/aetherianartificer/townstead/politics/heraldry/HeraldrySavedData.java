package com.aetherianartificer.townstead.politics.heraldry;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}
import java.util.*;

public final class HeraldrySavedData extends SavedData {
    public record Entry(EmblemRecipe recipe, long revision, UUID author, long time) {}
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, List<Entry>> history = new LinkedHashMap<>();
    public Entry get(String actor) { return entries.getOrDefault(actor, new Entry(EmblemRecipe.DEFAULT, 0, new UUID(0, 0), 0)); }
    public boolean publish(String actor, EmblemRecipe recipe, long expected, UUID author, long time) {
        Entry previous = get(actor);
        if (previous.revision() != expected) return false;
        if (previous.recipe().equals(recipe) && previous.revision() > 0) return true;
        var versions = history.computeIfAbsent(actor, key -> new ArrayList<>());
        if (previous.revision() > 0) versions.add(previous);
        if (versions.size() > 32) versions.remove(0);
        entries.put(actor, new Entry(recipe, previous.revision() + 1, author, time)); setDirty(); return true;
    }
    public static HeraldrySavedData get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(HeraldrySavedData::new, HeraldrySavedData::load), "townstead_heraldry");
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(HeraldrySavedData::load, HeraldrySavedData::new, "townstead_heraldry");
        *///?}
    }
    //? if >=1.21 {
    public static HeraldrySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static HeraldrySavedData load(CompoundTag tag) {
    *///?}
        HeraldrySavedData data = new HeraldrySavedData();
        var list = tag.getList("actors", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var t = list.getCompound(i);
            try {
                String actor = t.getString("actor"); data.entries.put(actor, readEntry(t));
                var versions = t.getList("history", Tag.TAG_COMPOUND); var past = new ArrayList<Entry>();
                for (int j = Math.max(0, versions.size() - 32); j < versions.size(); j++) past.add(readEntry(versions.getCompound(j)));
                data.history.put(actor, past);
            } catch (RuntimeException error) { com.aetherianartificer.townstead.Townstead.LOGGER.warn("Ignoring invalid saved heraldry", error); }
        }
        return data;
    }
    private static Entry readEntry(CompoundTag t) { return new Entry(EmblemRecipe.decode(t.getString("recipe")), t.getLong("revision"), t.getUUID("author"), t.getLong("time")); }
    private static CompoundTag writeEntry(Entry e) {
        var t = new CompoundTag(); t.putString("recipe", e.recipe().encode()); t.putLong("revision", e.revision()); t.putUUID("author", e.author()); t.putLong("time", e.time()); return t;
    }
    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        tag.putInt("schema", 1); var list = new ListTag();
        entries.forEach((actor, entry) -> {
            var t = writeEntry(entry); t.putString("actor", actor); var past = new ListTag();
            history.getOrDefault(actor, List.of()).forEach(e -> past.add(writeEntry(e))); t.put("history", past); list.add(t);
        });
        tag.put("actors", list); return tag;
    }
}
