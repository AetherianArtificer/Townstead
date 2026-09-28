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
 * A village holds each Persona at most once.
 */
public final class PersonaInstances extends SavedData {
    public static final String FILE_ID = "townstead_personas";
    private static final int SCHEMA = 1;

    public record Instance(ResourceLocation persona, UUID villager, ResourceLocation dimension, int village, long created) {
        String key() { return PersonaInstances.key(persona, dimension, village); }
    }

    private final Map<UUID, Instance> byVillager = new LinkedHashMap<>();

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

    public void add(Instance instance) {
        byVillager.put(instance.villager(), instance);
        setDirty();
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
            Instance instance = new Instance(persona, entry.getUUID("villager"), dimension, entry.getInt("village"), entry.getLong("created"));
            data.byVillager.put(instance.villager(), instance);
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
            list.add(entry);
        }
        tag.put("instances", list);
        return tag;
    }
}
