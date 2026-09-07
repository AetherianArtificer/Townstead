package com.aetherianartificer.townstead.objectset;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-level store of recognised sets, indexed by chunk so nearby lookups never scan the whole map. */
public final class ObjectSetSavedData extends SavedData {
    public static final String FILE_ID = "townstead_object_sets";
    private static final String KEY_SETS = "sets";

    private final Map<Long, ObjectSetInstance> byAnchor = new HashMap<>();
    private final Map<Long, List<ObjectSetInstance>> byChunk = new HashMap<>();

    public ObjectSetSavedData() {}

    public static ObjectSetSavedData get(ServerLevel level) {
        //? if >=1.21 {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(ObjectSetSavedData::new, ObjectSetSavedData::load), FILE_ID);
        //?} else {
        /*return level.getDataStorage().computeIfAbsent(ObjectSetSavedData::load, ObjectSetSavedData::new, FILE_ID);
        *///?}
    }

    //? if >=1.21 {
    public static ObjectSetSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static ObjectSetSavedData load(CompoundTag tag) {
    *///?}
        ObjectSetSavedData data = new ObjectSetSavedData();
        if (tag.contains(KEY_SETS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_SETS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ObjectSetInstance instance = ObjectSetInstance.load(list.getCompound(i));
                if (instance != null) data.index(instance);
            }
        }
        return data;
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        ListTag list = new ListTag();
        for (ObjectSetInstance instance : byAnchor.values()) list.add(instance.save());
        tag.put(KEY_SETS, list);
        return tag;
    }

    public @Nullable ObjectSetInstance at(BlockPos anchor) {
        return byAnchor.get(anchor.asLong());
    }

    public void put(ObjectSetInstance instance) {
        remove(instance.anchor());
        index(instance);
        setDirty();
    }

    public @Nullable ObjectSetInstance remove(BlockPos anchor) {
        ObjectSetInstance removed = byAnchor.remove(anchor.asLong());
        if (removed != null) {
            List<ObjectSetInstance> bucket = byChunk.get(chunkKey(anchor));
            if (bucket != null) {
                bucket.remove(removed);
                if (bucket.isEmpty()) byChunk.remove(chunkKey(anchor));
            }
            setDirty();
        }
        return removed;
    }

    /** Every instance whose anchor lies within the radius (Euclidean) of the position. */
    public List<ObjectSetInstance> within(BlockPos pos, int radius) {
        List<ObjectSetInstance> out = new ArrayList<>();
        int minCx = (pos.getX() - radius) >> 4, maxCx = (pos.getX() + radius) >> 4;
        int minCz = (pos.getZ() - radius) >> 4, maxCz = (pos.getZ() + radius) >> 4;
        double radiusSq = (double) radius * radius;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                List<ObjectSetInstance> bucket = byChunk.get(ChunkPos.asLong(cx, cz));
                if (bucket == null) continue;
                for (ObjectSetInstance instance : bucket) {
                    if (instance.anchor().distSqr(pos) <= radiusSq) out.add(instance);
                }
            }
        }
        return out;
    }

    public int size() {
        return byAnchor.size();
    }

    private void index(ObjectSetInstance instance) {
        byAnchor.put(instance.anchor().asLong(), instance);
        byChunk.computeIfAbsent(chunkKey(instance.anchor()), k -> new ArrayList<>()).add(instance);
    }

    private static long chunkKey(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
