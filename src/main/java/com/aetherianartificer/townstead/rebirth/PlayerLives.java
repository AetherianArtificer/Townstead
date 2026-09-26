package com.aetherianartificer.townstead.rebirth;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Each player's past lives. A past life keeps its own memorial id, so the family tree and the
 * Chronicles can tell the person who died from the person the player is now. Also holds the name
 * the player goes by in their current life, and which villagers have already forgotten the old one.
 */
public final class PlayerLives extends SavedData {
    private static final String FILE_ID = "townstead_player_lives";

    /** A life that ended. Chronicle events up to {@code lastEventId} belong to it. */
    public record Life(UUID memorialId, String name, long endedAt, long lastEventId) {}

    private static final class Person {
        @Nullable String name;
        final List<Life> lives = new ArrayList<>();
        final Set<UUID> forgottenBy = new HashSet<>();
    }

    private final Map<UUID, Person> people = new HashMap<>();

    /** Server-wide snapshot for callers without a server at hand, such as archive queries. */
    private static volatile Map<UUID, List<Life>> livesCache = Map.of();
    private static volatile Map<UUID, UUID> ownerCache = Map.of();
    private static volatile Map<UUID, String> namesCache = Map.of();

    public static PlayerLives get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PlayerLives::new, PlayerLives::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(PlayerLives::load, PlayerLives::new, FILE_ID);
        *///?}
    }

    public static void onServerStarting(MinecraftServer server) {
        get(server).publish();
    }

    public static void onServerStopping() {
        livesCache = Map.of();
        ownerCache = Map.of();
        namesCache = Map.of();
    }

    /** The name a player chose at their latest rebirth, or null in their first life. */
    public static @Nullable String nameOf(UUID player) {
        return namesCache.get(player);
    }

    public static Map<UUID, String> names() {
        return namesCache;
    }

    public static List<Life> livesOf(UUID player) {
        return livesCache.getOrDefault(player, List.of());
    }

    /** The player whose past life a memorial id stands for, or null. */
    public static @Nullable UUID ownerOf(UUID memorialId) {
        return ownerCache.get(memorialId);
    }

    public boolean hasPastLives(UUID player) {
        Person person = people.get(player);
        return person != null && !person.lives.isEmpty();
    }

    void endLife(UUID player, Life life, String newName) {
        Person person = people.computeIfAbsent(player, ignored -> new Person());
        person.lives.add(life);
        person.name = newName;
        person.forgottenBy.clear();
        setDirty();
        publish();
    }

    /** True the first time a villager meets this player's current life; marks it as met. */
    boolean forgetOnce(UUID player, UUID villager) {
        Person person = people.get(player);
        if (person == null || person.lives.isEmpty() || !person.forgottenBy.add(villager)) return false;
        setDirty();
        return true;
    }

    private void publish() {
        Map<UUID, List<Life>> lives = new HashMap<>();
        Map<UUID, UUID> owners = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        people.forEach((player, person) -> {
            lives.put(player, List.copyOf(person.lives));
            for (Life life : person.lives) owners.put(life.memorialId(), player);
            if (person.name != null) names.put(player, person.name);
        });
        livesCache = Map.copyOf(lives);
        ownerCache = Map.copyOf(owners);
        namesCache = Map.copyOf(names);
    }

    //? if >=1.21 {
    static PlayerLives load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*static PlayerLives load(CompoundTag tag) {
    *///?}
        PlayerLives data = new PlayerLives();
        ListTag list = tag.getList("people", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("id")) continue;
            Person person = new Person();
            if (entry.contains("name")) person.name = entry.getString("name");
            ListTag lives = entry.getList("lives", Tag.TAG_COMPOUND);
            for (int j = 0; j < lives.size(); j++) {
                CompoundTag life = lives.getCompound(j);
                if (!life.hasUUID("memorial")) continue;
                person.lives.add(new Life(life.getUUID("memorial"), life.getString("name"),
                        life.getLong("endedAt"), life.getLong("lastEvent")));
            }
            ListTag forgotten = entry.getList("forgottenBy", Tag.TAG_INT_ARRAY);
            for (int j = 0; j < forgotten.size(); j++) {
                person.forgottenBy.add(net.minecraft.nbt.NbtUtils.loadUUID(forgotten.get(j)));
            }
            data.people.put(entry.getUUID("id"), person);
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
        people.forEach((player, person) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", player);
            if (person.name != null) entry.putString("name", person.name);
            ListTag lives = new ListTag();
            for (Life life : person.lives) {
                CompoundTag t = new CompoundTag();
                t.putUUID("memorial", life.memorialId());
                t.putString("name", life.name());
                t.putLong("endedAt", life.endedAt());
                t.putLong("lastEvent", life.lastEventId());
                lives.add(t);
            }
            entry.put("lives", lives);
            ListTag forgotten = new ListTag();
            for (UUID villager : person.forgottenBy) forgotten.add(net.minecraft.nbt.NbtUtils.createUUID(villager));
            entry.put("forgottenBy", forgotten);
            list.add(entry);
        });
        tag.put("people", list);
        return tag;
    }
}
