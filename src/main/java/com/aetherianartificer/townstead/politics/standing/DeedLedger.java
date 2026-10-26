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
    /** The newest deed keys first, per settlement and person, so stories can name what someone did. */
    private final Map<String, java.util.List<String>> recent = new LinkedHashMap<>();
    private static final int RECENT_LIMIT = 16;

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

    /** Deed keys this person earned in the settlement, newest first, such as {@code raised:12:bakery}. */
    public java.util.List<String> recent(SettlementRef settlement, UUID person) {
        return java.util.List.copyOf(recent.getOrDefault(recentKey(settlement, person), java.util.List.of()));
    }

    private static String recentKey(SettlementRef settlement, UUID person) {
        return settlement.dimension() + "|" + settlement.villageId() + "|" + person;
    }

    public int points(SettlementRef settlement, UUID person) {
        return points.getOrDefault(settlement, Map.of()).getOrDefault(person, 0);
    }

    /** Credits a deed once: the same {@code key} (a building, a tier) never pays twice. */
    public boolean credit(SettlementRef settlement, UUID person, String key, int amount) {
        if (!credited.add(settlement.dimension() + "|" + settlement.villageId() + "|" + key)) return false;
        add(settlement, person, amount);
        java.util.List<String> keys = recent.computeIfAbsent(recentKey(settlement, person), k -> new java.util.ArrayList<>());
        keys.add(0, key);
        if (keys.size() > RECENT_LIMIT) keys.subList(RECENT_LIMIT, keys.size()).clear();
        return true;
    }

    /** Drops every deed credited to a person, as when they start a new life. */
    public void forget(UUID person) {
        boolean changed = false;
        for (Map<UUID, Integer> bySettlement : points.values()) changed |= bySettlement.remove(person) != null;
        changed |= recent.keySet().removeIf(key -> key.endsWith("|" + person));
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
        CompoundTag recent = tag.getCompound("recent");
        for (String key : recent.getAllKeys()) {
            ListTag list = recent.getList(key, Tag.TAG_STRING);
            java.util.List<String> keys = new java.util.ArrayList<>();
            for (int i = 0; i < list.size(); i++) keys.add(list.getString(i));
            data.recent.put(key, keys);
        }
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
        CompoundTag recentTag = new CompoundTag();
        recent.forEach((key, list) -> {
            ListTag entries = new ListTag();
            list.forEach(value -> entries.add(net.minecraft.nbt.StringTag.valueOf(value)));
            recentTag.put(key, entries);
        });
        tag.put("recent", recentTag);
        return tag;
    }
}
