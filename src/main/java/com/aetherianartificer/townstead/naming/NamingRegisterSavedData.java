package com.aetherianartificer.townstead.naming;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * World-level persistence for naming registers: the resolved register of each MCA nationality
 * region, and any register a village has been assigned.
 *
 * <p>Regions are stored by MCA's region id mapped to the resolved register <em>string</em>, never
 * to an index into {@code Names.REGION_NAMES}. That list is rebuilt from whatever {@code mca_names}
 * folders happen to be loaded, so an index is only meaningful until the next mod or datapack adds
 * a bucket; the string survives.</p>
 */
public class NamingRegisterSavedData extends SavedData {
    public static final String FILE_ID = "townstead_naming_registers";

    /** Ceiling on remembered regions, so an endlessly explored world can't bloat the save. */
    public static final int MAX_REGIONS = 65536;

    private static final String KEY_REGIONS = "regions";
    private static final String KEY_VILLAGES = "villages";
    private static final String KEY_VILLAGE_CULTURES = "villageCultures";
    private static final String K_REGION_ID = "region";
    private static final String K_VILLAGE_ID = "village";
    private static final String K_DIMENSION = "dimension";
    private static final String K_REGISTER = "register";
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryParse("minecraft:overworld");

    private final Map<Integer, String> regions = new LinkedHashMap<>();
    private final Map<Integer, String> villages = new LinkedHashMap<>();
    private final Map<VillageCultureKey, String> villageCultures = new LinkedHashMap<>();

    public NamingRegisterSavedData() {}

    public static NamingRegisterSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(NamingRegisterSavedData::new, NamingRegisterSavedData::load),
                FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                NamingRegisterSavedData::load,
                NamingRegisterSavedData::new,
                FILE_ID);
        *///?}
    }

    /** The register frozen for this MCA nationality region, or empty when none is recorded yet. */
    public String region(int regionId) {
        String value = regions.get(regionId);
        return value == null ? "" : value;
    }

    /** Freezes a region's register. First write wins: a recorded region is never re-derived. */
    public void putRegion(int regionId, String register) {
        if (register == null || register.isBlank()) return;
        if (regions.containsKey(regionId)) return;
        if (regions.size() >= MAX_REGIONS) return;
        regions.put(regionId, register);
        setDirty();
    }

    /** The register assigned to this village, or empty when none is. */
    public String village(int villageId) {
        String value = villages.get(villageId);
        return value == null ? "" : value;
    }

    /** Assigns (or with a blank register, clears) a village's naming register. */
    public void putVillage(int villageId, String register) {
        String previous = villages.get(villageId);
        if (register == null || register.isBlank()) {
            if (previous == null) return;
            villages.remove(villageId);
        } else {
            if (register.equals(previous)) return;
            villages.put(villageId, register);
        }
        setDirty();
    }

    /** The culture a village has settled into, or empty when it has none yet. */
    public String villageCulture(ResourceLocation dimension, int villageId) {
        String value = villageCultures.get(new VillageCultureKey(dimension, villageId));
        return value == null ? "" : value;
    }

    /** Sets (or with a blank culture, clears) a village's culture. */
    public void putVillageCulture(ResourceLocation dimension, int villageId, String culture) {
        VillageCultureKey key = new VillageCultureKey(dimension, villageId);
        String previous = villageCultures.get(key);
        if (culture == null || culture.isBlank()) {
            if (previous == null) return;
            villageCultures.remove(key);
        } else {
            if (culture.equals(previous)) return;
            villageCultures.put(key, culture);
        }
        setDirty();
    }

    /** Legacy source compatibility; pre-dimension entries always belonged to the Overworld. */
    @Deprecated
    public String villageCulture(int villageId) {
        return villageCulture(OVERWORLD, villageId);
    }

    /** Legacy source compatibility; new callers must always provide the dimension. */
    @Deprecated
    public void putVillageCulture(int villageId, String culture) {
        putVillageCulture(OVERWORLD, villageId, culture);
    }

    //? if >=1.21 {
    public static NamingRegisterSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static NamingRegisterSavedData load(CompoundTag tag) {
    *///?}
        NamingRegisterSavedData data = new NamingRegisterSavedData();
        readInto(tag, KEY_REGIONS, K_REGION_ID, data.regions);
        readInto(tag, KEY_VILLAGES, K_VILLAGE_ID, data.villages);
        readVillageCultures(tag, data.villageCultures);
        return data;
    }

    private static void readInto(CompoundTag tag, String listKey, String idKey, Map<Integer, String> into) {
        if (!tag.contains(listKey, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(listKey, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String register = entry.getString(K_REGISTER);
            if (register.isBlank()) continue;
            into.put(entry.getInt(idKey), register);
        }
    }

    private static void readVillageCultures(CompoundTag tag, Map<VillageCultureKey, String> into) {
        if (!tag.contains(KEY_VILLAGE_CULTURES, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(KEY_VILLAGE_CULTURES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String culture = entry.getString(K_REGISTER);
            if (culture.isBlank()) continue;
            ResourceLocation dimension = entry.contains(K_DIMENSION, Tag.TAG_STRING)
                    ? ResourceLocation.tryParse(entry.getString(K_DIMENSION)) : OVERWORLD;
            if (dimension == null) continue;
            into.put(new VillageCultureKey(dimension, entry.getInt(K_VILLAGE_ID)), culture);
        }
    }

    //? if >=1.21 {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
    *///?}
        tag.put(KEY_REGIONS, write(regions, K_REGION_ID));
        tag.put(KEY_VILLAGES, write(villages, K_VILLAGE_ID));
        tag.put(KEY_VILLAGE_CULTURES, writeVillageCultures(villageCultures));
        return tag;
    }

    private static ListTag write(Map<Integer, String> from, String idKey) {
        ListTag list = new ListTag();
        for (Map.Entry<Integer, String> entry : from.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putInt(idKey, entry.getKey());
            e.putString(K_REGISTER, entry.getValue());
            list.add(e);
        }
        return list;
    }

    private static ListTag writeVillageCultures(Map<VillageCultureKey, String> from) {
        ListTag list = new ListTag();
        for (Map.Entry<VillageCultureKey, String> entry : from.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putString(K_DIMENSION, entry.getKey().dimension().toString());
            value.putInt(K_VILLAGE_ID, entry.getKey().villageId());
            value.putString(K_REGISTER, entry.getValue());
            list.add(value);
        }
        return list;
    }

    private record VillageCultureKey(ResourceLocation dimension, int villageId) {
        private VillageCultureKey {
            if (dimension == null) throw new IllegalArgumentException("Village culture dimension cannot be null");
        }
    }
}
