package com.aetherianartificer.townstead.persona;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every Persona living in this world: which villager is which Persona, and their home village.
 * A village holds each Persona at most once. Also keeps what each Persona rolled for this world.
 */
public final class PersonaInstances extends SavedData {
    public static final String FILE_ID = "townstead_personas";
    /** The village of a Persona who is on the road between villages. */
    public static final int TRAVELLING = -1;
    private static final int SCHEMA = 1;

    /** @param name the villager's name when they arrived, for naming them while they are unloaded */
    public record Instance(ResourceLocation persona, UUID villager, ResourceLocation dimension, int village, long created,
                           String name) {
        String key() { return PersonaInstances.key(persona, dimension, village); }
    }

    /** A Persona someone introduced, due to arrive in a village on a world day, for a player. */
    public record Pending(ResourceLocation persona, ResourceLocation dimension, int village, long dueDay, UUID player) {}

    private final Map<UUID, Instance> byVillager = new LinkedHashMap<>();
    private final Map<ResourceLocation, Map<String, String>> rolls = new LinkedHashMap<>();
    private final List<Pending> pending = new java.util.ArrayList<>();

    public static PersonaInstances get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PersonaInstances::new, PersonaInstances::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(
                PersonaInstances::load, PersonaInstances::new, FILE_ID);
        *///?}
    }

    static String key(ResourceLocation persona, ResourceLocation dimension, int village) {
        return persona + "|" + dimension + "|" + village;
    }

    public boolean isPersona(UUID villager) {
        return byVillager.containsKey(villager);
    }

    public @Nullable Instance of(UUID villager) {
        return byVillager.get(villager);
    }

    public @Nullable Instance in(ResourceLocation persona, ResourceLocation dimension, int village) {
        String key = key(persona, dimension, village);
        for (Instance instance : byVillager.values()) if (instance.key().equals(key)) return instance;
        return null;
    }

    public List<Instance> of(ResourceLocation persona) {
        return byVillager.values().stream().filter(i -> i.persona().equals(persona)).toList();
    }

    public Collection<Instance> all() {
        return byVillager.values();
    }

    /** Moves a Persona to another village, or onto the road with {@link #TRAVELLING}. */
    public void move(UUID villager, ResourceLocation dimension, int village) {
        Instance instance = byVillager.get(villager);
        if (instance == null) return;
        byVillager.put(villager, new Instance(instance.persona(), villager, dimension, village, instance.created(), instance.name()));
        setDirty();
    }

    /** The world-unique Persona's instance while they are on the road, or null. */
    public @Nullable Instance travelling(ResourceLocation persona) {
        for (Instance instance : byVillager.values()) {
            if (instance.persona().equals(persona) && instance.village() == TRAVELLING) return instance;
        }
        return null;
    }

    public void add(Instance instance) {
        byVillager.put(instance.villager(), instance);
        setDirty();
    }

    /** The value this world rolled for a Persona's roll, or null when it has not rolled yet. */
    public @Nullable String rolled(ResourceLocation persona, String roll) {
        Map<String, String> values = rolls.get(persona);
        return values == null ? null : values.get(roll);
    }

    public void setRolled(ResourceLocation persona, String roll, String value) {
        rolls.computeIfAbsent(persona, k -> new LinkedHashMap<>()).put(roll, value);
        setDirty();
    }

    public void clearRolls(ResourceLocation persona) {
        if (rolls.remove(persona) != null) setDirty();
    }

    public List<Pending> pending() {
        return List.copyOf(pending);
    }

    public boolean isPending(ResourceLocation persona, ResourceLocation dimension, int village) {
        for (Pending p : pending) {
            if (p.persona().equals(persona) && p.dimension().equals(dimension) && p.village() == village) return true;
        }
        return false;
    }

    public boolean isPendingAnywhere(ResourceLocation persona) {
        for (Pending p : pending) if (p.persona().equals(persona)) return true;
        return false;
    }

    public void addPending(Pending entry) {
        pending.add(entry);
        setDirty();
    }

    public void removePending(Pending entry) {
        if (pending.remove(entry)) setDirty();
    }

    public boolean remove(UUID villager) {
        boolean removed = byVillager.remove(villager) != null;
        if (removed) setDirty();
        return removed;
    }

    //? if >=1.21 {
    public static PersonaInstances load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static PersonaInstances load(CompoundTag tag) {
    *///?}
        PersonaInstances data = new PersonaInstances();
        ListTag list = tag.getList("instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation persona = ResourceLocation.tryParse(entry.getString("persona"));
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (persona == null || dimension == null || !entry.hasUUID("villager")) continue;
            Instance instance = new Instance(persona, entry.getUUID("villager"), dimension, entry.getInt("village"),
                    entry.getLong("created"), entry.getString("name"));
            data.byVillager.put(instance.villager(), instance);
        }
        ListTag pendingList = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingList.size(); i++) {
            CompoundTag entry = pendingList.getCompound(i);
            ResourceLocation persona = ResourceLocation.tryParse(entry.getString("persona"));
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (persona == null || dimension == null || !entry.hasUUID("player")) continue;
            data.pending.add(new Pending(persona, dimension, entry.getInt("village"), entry.getLong("due"), entry.getUUID("player")));
        }
        CompoundTag rolled = tag.getCompound("rolls");
        for (String persona : rolled.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(persona);
            if (id == null) continue;
            CompoundTag values = rolled.getCompound(persona);
            Map<String, String> map = new LinkedHashMap<>();
            for (String roll : values.getAllKeys()) map.put(roll, values.getString(roll));
            data.rolls.put(id, map);
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
        ListTag list = new ListTag();
        for (Instance instance : byVillager.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("persona", instance.persona().toString());
            entry.putUUID("villager", instance.villager());
            entry.putString("dimension", instance.dimension().toString());
            entry.putInt("village", instance.village());
            entry.putLong("created", instance.created());
            entry.putString("name", instance.name());
            list.add(entry);
        }
        tag.put("instances", list);
        CompoundTag rolled = new CompoundTag();
        for (Map.Entry<ResourceLocation, Map<String, String>> persona : rolls.entrySet()) {
            CompoundTag values = new CompoundTag();
            persona.getValue().forEach(values::putString);
            rolled.put(persona.getKey().toString(), values);
        }
        tag.put("rolls", rolled);
        ListTag pendingList = new ListTag();
        for (Pending p : pending) {
            CompoundTag entry = new CompoundTag();
            entry.putString("persona", p.persona().toString());
            entry.putString("dimension", p.dimension().toString());
            entry.putInt("village", p.village());
            entry.putLong("due", p.dueDay());
            entry.putUUID("player", p.player());
            pendingList.add(entry);
        }
        tag.put("pending", pendingList);
        return tag;
    }
}
