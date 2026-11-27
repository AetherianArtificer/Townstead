package com.aetherianartificer.townstead.profession;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The saved {@link SeatRoll} of every career in every village. */
public final class SeatLedger extends SavedData {
    public static final String FILE_ID = "townstead_seats";

    private record Key(ResourceLocation dimension, int villageId, ResourceLocation career) {}

    private final Map<Key, SeatRoll> rolls = new HashMap<>();

    public SeatLedger() {}

    public static SeatLedger get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(SeatLedger::new, SeatLedger::load), FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(SeatLedger::load, SeatLedger::new, FILE_ID);
        *///?}
    }

    public SeatRoll roll(ResourceLocation dimension, int villageId, ResourceLocation career) {
        return rolls.computeIfAbsent(new Key(dimension, villageId, career), key -> new SeatRoll());
    }

    /** A worker lost or left their job; no roll may keep holding a seat for them. */
    public void dismissEverywhere(UUID worker) {
        boolean changed = false;
        for (SeatRoll roll : rolls.values()) changed |= roll.dismiss(worker);
        if (changed) setDirty();
    }

    //? if >=1.21 {
    public static SeatLedger load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static SeatLedger load(CompoundTag tag) {
    *///?}
        SeatLedger data = new SeatLedger();
        ListTag list = tag.getList("rolls", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(t.getString("dim"));
            ResourceLocation career = ResourceLocation.tryParse(t.getString("career"));
            if (dimension == null || career == null) continue;
            data.rolls.put(new Key(dimension, t.getInt("village"), career), SeatRoll.load(t));
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
        for (Map.Entry<Key, SeatRoll> entry : rolls.entrySet()) {
            if (entry.getValue().isEmpty() && !entry.getValue().seeded()) continue;
            CompoundTag t = entry.getValue().save();
            t.putString("dim", entry.getKey().dimension().toString());
            t.putInt("village", entry.getKey().villageId());
            t.putString("career", entry.getKey().career().toString());
            list.add(t);
        }
        tag.put("rolls", list);
        return tag;
    }
}
