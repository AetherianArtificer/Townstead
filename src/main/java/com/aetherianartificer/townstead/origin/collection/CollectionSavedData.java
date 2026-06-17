package com.aetherianartificer.townstead.origin.collection;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Overworld-level persistence for {@code collection} stores, shared server-wide and keyed by
 * holder UUID (works for villagers and players alike, no MCA coupling). The nested map is the live
 * store {@link CollectionValues} reads and writes; callers {@link #setDirty()} after a mutation.
 * Collections persist because their membership is unrecoverable accumulated state, unlike a
 * {@code resource} which resets to a defined start.
 */
public class CollectionSavedData extends SavedData {

    public static final String FILE_ID = "townstead_collections";

    private static final String K_HOLDERS = "holders";
    private static final String K_UUID = "uuid";
    private static final String K_SETS = "sets";
    private static final String K_ID = "id";
    private static final String K_MEMBERS = "members";
    private static final String K_MEMBER = "m";
    private static final String K_EXPIRY = "exp";
    private static final String K_COUNT = "n";

    // holder UUID -> collection gene id -> (member -> count + expiry game-time, Long.MAX_VALUE = permanent)
    private final Map<UUID, Map<ResourceLocation, LinkedHashMap<String, CollectionMember>>> store = new ConcurrentHashMap<>();

    public CollectionSavedData() {}

    public static CollectionSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(CollectionSavedData::new, CollectionSavedData::load),
                FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                CollectionSavedData::load,
                CollectionSavedData::new,
                FILE_ID);
        *///?}
    }

    public Map<UUID, Map<ResourceLocation, LinkedHashMap<String, CollectionMember>>> store() {
        return store;
    }

    //? if >=1.21 {
    public static CollectionSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static CollectionSavedData load(CompoundTag tag) {
    *///?}
        CollectionSavedData data = new CollectionSavedData();
        if (!tag.contains(K_HOLDERS, Tag.TAG_LIST)) return data;
        ListTag holders = tag.getList(K_HOLDERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < holders.size(); i++) {
            try {
                CompoundTag h = holders.getCompound(i);
                UUID holder = h.getUUID(K_UUID);
                Map<ResourceLocation, LinkedHashMap<String, CollectionMember>> byId = new ConcurrentHashMap<>();
                ListTag sets = h.getList(K_SETS, Tag.TAG_COMPOUND);
                for (int j = 0; j < sets.size(); j++) {
                    CompoundTag s = sets.getCompound(j);
                    ResourceLocation id = ResourceLocation.tryParse(s.getString(K_ID));
                    if (id == null) continue;
                    LinkedHashMap<String, CollectionMember> members = new LinkedHashMap<>();
                    ListTag list = s.getList(K_MEMBERS, Tag.TAG_COMPOUND);
                    for (int k = 0; k < list.size(); k++) {
                        CompoundTag m = list.getCompound(k);
                        // A pre-count save has no K_COUNT; those were plain-set members, so default 1.
                        int count = m.contains(K_COUNT) ? m.getInt(K_COUNT) : 1;
                        members.put(m.getString(K_MEMBER), new CollectionMember(count, m.getLong(K_EXPIRY)));
                    }
                    if (!members.isEmpty()) byId.put(id, members);
                }
                if (!byId.isEmpty()) data.store.put(holder, byId);
            } catch (Exception ignored) {
                // Skip a malformed holder rather than dropping the whole save.
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
        ListTag holders = new ListTag();
        for (Map.Entry<UUID, Map<ResourceLocation, LinkedHashMap<String, CollectionMember>>> he : store.entrySet()) {
            if (he.getValue().isEmpty()) continue;
            CompoundTag h = new CompoundTag();
            h.putUUID(K_UUID, he.getKey());
            ListTag sets = new ListTag();
            for (Map.Entry<ResourceLocation, LinkedHashMap<String, CollectionMember>> se : he.getValue().entrySet()) {
                if (se.getValue().isEmpty()) continue;
                CompoundTag s = new CompoundTag();
                s.putString(K_ID, se.getKey().toString());
                ListTag members = new ListTag();
                for (Map.Entry<String, CollectionMember> me : se.getValue().entrySet()) {
                    CompoundTag m = new CompoundTag();
                    m.putString(K_MEMBER, me.getKey());
                    m.putLong(K_EXPIRY, me.getValue().expiry);
                    if (me.getValue().count != 1) m.putInt(K_COUNT, me.getValue().count);
                    members.add(m);
                }
                s.put(K_MEMBERS, members);
                sets.add(s);
            }
            h.put(K_SETS, sets);
            holders.add(h);
        }
        tag.put(K_HOLDERS, holders);
        return tag;
    }
}
