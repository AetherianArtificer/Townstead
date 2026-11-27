package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.Genotype;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Each parent's heredity at the moment MCA made their baby item. The item can be placed long
 * after, far from the villager parent, and an unloaded villager has no state to read.
 */
public final class ParentSnapshots extends SavedData {
    public static final String FILE_ID = "townstead_parent_snapshots";

    private final Map<UUID, Heredity.Parent> parents = new HashMap<>();

    public ParentSnapshots() {}

    public static ParentSnapshots get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(ParentSnapshots::new, ParentSnapshots::load), FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(ParentSnapshots::load, ParentSnapshots::new, FILE_ID);
        *///?}
    }

    public void remember(Entity entity, RandomSource random) {
        Heredity.Parent parent = Heredity.parentOf(entity, random);
        if (parent == null) return;
        parents.put(entity.getUUID(), new Heredity.Parent(parent.rootId(), parent.heritage(),
                parent.genotype().copy()));
        setDirty();
    }

    @Nullable
    public Heredity.Parent recall(@Nullable UUID id) {
        return id == null ? null : parents.get(id);
    }

    //? if >=1.21 {
    public static ParentSnapshots load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static ParentSnapshots load(CompoundTag tag) {
    *///?}
        ParentSnapshots data = new ParentSnapshots();
        CompoundTag list = tag.getCompound("parents");
        for (String key : list.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag t = list.getCompound(key);
            data.parents.put(id, new Heredity.Parent(t.getString("root"),
                    Heritage.fromTag(t.getCompound("heritage")),
                    Genotype.fromTag(t.getCompound("genotype"))));
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
        CompoundTag list = new CompoundTag();
        parents.forEach((id, parent) -> {
            CompoundTag t = new CompoundTag();
            t.putString("root", parent.rootId() == null ? "" : parent.rootId());
            t.put("heritage", parent.heritage().toTag());
            t.put("genotype", parent.genotype().toTag());
            list.put(id.toString(), t);
        });
        tag.put("parents", list);
        return tag;
    }
}
