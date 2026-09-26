package com.aetherianartificer.townstead.politics.standing;

import com.aetherianartificer.townstead.politics.state.SettlementRef;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Deed points each person earned in each settlement: buildings raised, spirit tiers reached. It
 * records what happened, not what anyone believes, so standing never rests on gossip.
 */
public final class DeedLedger extends SavedData {
    public static final String FILE_ID = "townstead_deeds";
    private static final int SCHEMA = 1;
    private final Map<SettlementRef, Map<UUID, Integer>> points = new LinkedHashMap<>();
    private final java.util.Set<String> credited = new java.util.HashSet<>();

    public static DeedLedger get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(DeedLedger::new, DeedLedger::load), FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                DeedLedger::load, DeedLedger::new, FILE_ID);
        *///?}
    }

    public int points(SettlementRef settlement, UUID person) {
        return points.getOrDefault(settlement, Map.of()).getOrDefault(person, 0);
    }

    /** Credits a deed once: the same {@code key} (a building, a tier) never pays twice. */
    public boolean credit(SettlementRef settlement, UUID person, String key, int amount) {
        if (!credited.add(settlement.dimension() + "|" + settlement.villageId() + "|" + key)) return false;
        add(settlement, person, amount);
        return true;
    }

    /** Drops every deed credited to a person, as when they start a new life. */
    public void forget(UUID person) {
        boolean changed = false;
        for (Map<UUID, Integer> bySettlement : points.values()) changed |= bySettlement.remove(person) != null;
        if (changed) setDirty();
    }

    public void add(SettlementRef settlement, UUID person, int amount) {
        if (amount == 0) return;
        points.computeIfAbsent(settlement, ignored -> new HashMap<>()).merge(person, amount, Integer::sum);
        setDirty();
    }

    //? if >=1.21 {
    public static DeedLedger load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static DeedLedger load(CompoundTag tag) {
    *///?}
        DeedLedger data = new DeedLedger();
        ListTag settlements = tag.getList("settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < settlements.size(); i++) {
            CompoundTag entry = settlements.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (dimension == null) continue;
            Map<UUID, Integer> people = new HashMap<>();
            ListTag list = entry.getList("people", Tag.TAG_COMPOUND);
            for (int j = 0; j < list.size(); j++) {
                CompoundTag person = list.getCompound(j);
                if (person.hasUUID("id")) people.put(person.getUUID("id"), person.getInt("points"));
            }
            data.points.put(new SettlementRef(dimension, entry.getInt("village")), people);
        }
        ListTag credited = tag.getList("credited", Tag.TAG_STRING);
        for (int i = 0; i < credited.size(); i++) data.credited.add(credited.getString(i));
        return data;
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        tag.putInt("schema", SCHEMA);
        ListTag settlements = new ListTag();
        points.forEach((settlement, people) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", settlement.dimension().toString());
            entry.putInt("village", settlement.villageId());
            ListTag list = new ListTag();
            people.forEach((id, value) -> {
                CompoundTag person = new CompoundTag();
                person.putUUID("id", id);
                person.putInt("points", value);
                list.add(person);
            });
            entry.put("people", list);
            settlements.add(entry);
        });
        tag.put("settlements", settlements);
        ListTag keys = new ListTag();
        credited.forEach(key -> keys.add(net.minecraft.nbt.StringTag.valueOf(key)));
        tag.put("credited", keys);
        return tag;
    }
}
