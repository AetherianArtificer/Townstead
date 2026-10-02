package com.aetherianartificer.townstead.politics.order;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Where each order's altar stands, recorded when it is placed and cleared when it is broken. */
public final class OrderAltars extends SavedData {
    private static final String FILE_ID = "townstead_order_altars";
    private final Map<ResourceLocation, GlobalPos> altars = new HashMap<>();

    public static OrderAltars get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(OrderAltars::new, OrderAltars::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(OrderAltars::load, OrderAltars::new, FILE_ID);
        *///?}
    }

    public @Nullable GlobalPos altar(ResourceLocation order) {
        return altars.get(order);
    }

    public void put(ResourceLocation order, GlobalPos pos) {
        altars.put(order, pos);
        setDirty();
    }

    /** Forgets whichever order's altar stood here. */
    public void removeAt(GlobalPos pos) {
        if (altars.values().removeIf(pos::equals)) setDirty();
    }

    //? if >=1.21 {
    private static OrderAltars load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
    //?} else {
    /*private static OrderAltars load(CompoundTag tag) {
    *///?}
        OrderAltars data = new OrderAltars();
        CompoundTag values = tag.getCompound("altars");
        for (String key : values.getAllKeys()) {
            ResourceLocation order = ResourceLocation.tryParse(key);
            CompoundTag entry = values.getCompound(key);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (order == null || dimension == null) continue;
            data.altars.put(order, GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(entry.getLong("pos"))));
        }
        return data;
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        CompoundTag values = new CompoundTag();
        altars.forEach((order, pos) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", pos.dimension().location().toString());
            entry.putLong("pos", pos.pos().asLong());
            values.put(order.toString(), entry);
        });
        tag.put("altars", values);
        return tag;
    }
}
