package com.aetherianartificer.townstead.politics.order;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** People training to join an order (Oathseekers), and how many nights each has trained. */
public final class OrderRecruits extends SavedData {
    private static final String FILE_ID = "townstead_order_recruits";
    private final Map<ResourceLocation, Map<UUID, Recruit>> recruits = new HashMap<>();

    /** {@code lastNight} is the day index of the last night that counted. */
    public record Recruit(int nights, long lastNight) {}

    public static OrderRecruits get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(OrderRecruits::new, OrderRecruits::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(OrderRecruits::load, OrderRecruits::new, FILE_ID);
        *///?}
    }

    public Map<UUID, Recruit> of(ResourceLocation order) {
        return recruits.getOrDefault(order, Map.of());
    }

    public @Nullable ResourceLocation orderOf(UUID person) {
        for (Map.Entry<ResourceLocation, Map<UUID, Recruit>> entry : recruits.entrySet()) {
            if (entry.getValue().containsKey(person)) return entry.getKey();
        }
        return null;
    }

    public boolean enlist(ResourceLocation order, UUID person) {
        if (orderOf(person) != null) return false;
        recruits.computeIfAbsent(order, key -> new LinkedHashMap<>()).put(person, new Recruit(0, -1));
        setDirty();
        return true;
    }

    public void trained(ResourceLocation order, UUID person, long night) {
        Map<UUID, Recruit> map = recruits.get(order);
        Recruit recruit = map == null ? null : map.get(person);
        if (recruit == null || recruit.lastNight() == night) return;
        map.put(person, new Recruit(recruit.nights() + 1, night));
        setDirty();
    }

    /** Counts {@code person} as fully trained; for testing. */
    public boolean ready(UUID person, int nights) {
        for (Map<UUID, Recruit> map : recruits.values()) {
            if (map.containsKey(person)) {
                map.put(person, new Recruit(nights, -1));
                setDirty();
                return true;
            }
        }
        return false;
    }

    public void remove(UUID person) {
        for (Map<UUID, Recruit> map : recruits.values()) {
            if (map.remove(person) != null) setDirty();
        }
    }

    public void removeOrder(ResourceLocation order) {
        if (recruits.remove(order) != null) setDirty();
    }

    //? if >=1.21 {
    private static OrderRecruits load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
    //?} else {
    /*private static OrderRecruits load(CompoundTag tag) {
    *///?}
        OrderRecruits data = new OrderRecruits();
        CompoundTag orders = tag.getCompound("orders");
        for (String key : orders.getAllKeys()) {
            ResourceLocation order = ResourceLocation.tryParse(key);
            if (order == null) continue;
            CompoundTag people = orders.getCompound(key);
            Map<UUID, Recruit> map = new LinkedHashMap<>();
            for (String raw : people.getAllKeys()) {
                try {
                    CompoundTag entry = people.getCompound(raw);
                    map.put(UUID.fromString(raw), new Recruit(entry.getInt("nights"), entry.getLong("last_night")));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (!map.isEmpty()) data.recruits.put(order, map);
        }
        return data;
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        CompoundTag orders = new CompoundTag();
        recruits.forEach((order, map) -> {
            CompoundTag people = new CompoundTag();
            map.forEach((person, recruit) -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt("nights", recruit.nights());
                entry.putLong("last_night", recruit.lastNight());
                people.put(person.toString(), entry);
            });
            orders.put(order.toString(), people);
        });
        tag.put("orders", orders);
        return tag;
    }
}
