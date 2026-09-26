package com.aetherianartificer.townstead.switchboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The Switchboard values an operator chose for this world, each stored as JSON text. */
public class SwitchboardSavedData extends SavedData {
    public static final String FILE_ID = "townstead_switchboard";

    private static final String KEY_VALUES = "values";
    private static final String KEY_SETUP_DONE = "setupDone";

    private final Map<String, JsonElement> values = new LinkedHashMap<>();
    private boolean setupDone;
    private boolean created = true;

    public SwitchboardSavedData() {}

    public static SwitchboardSavedData get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(SwitchboardSavedData::new, SwitchboardSavedData::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(
                SwitchboardSavedData::load, SwitchboardSavedData::new, FILE_ID);
        *///?}
    }

    public Map<String, JsonElement> values() {
        return Collections.unmodifiableMap(values);
    }

    public void set(String key, JsonElement value) {
        values.put(key, value);
        setDirty();
    }

    public void remove(String key) {
        if (values.remove(key) != null) setDirty();
    }

    public void replaceAll(Map<String, JsonElement> next) {
        values.clear();
        values.putAll(next);
        setDirty();
    }

    /** True when this world had no Switchboard file before this session. */
    public boolean created() {
        return created;
    }

    public boolean setupDone() {
        return setupDone;
    }

    public void markSetupDone() {
        if (setupDone) return;
        setupDone = true;
        setDirty();
    }

    //? if >=1.21 {
    public static SwitchboardSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static SwitchboardSavedData load(CompoundTag tag) {
    *///?}
        SwitchboardSavedData data = new SwitchboardSavedData();
        data.created = false;
        CompoundTag stored = tag.getCompound(KEY_VALUES);
        for (String key : stored.getAllKeys()) {
            try {
                data.values.put(key, JsonParser.parseString(stored.getString(key)));
            } catch (RuntimeException ignored) {}
        }
        data.setupDone = tag.getBoolean(KEY_SETUP_DONE);
        return data;
    }

    //? if >=1.21 {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
    *///?}
        CompoundTag stored = new CompoundTag();
        values.forEach((key, value) -> stored.putString(key, value.toString()));
        tag.put(KEY_VALUES, stored);
        tag.putBoolean(KEY_SETUP_DONE, setupDone);
        return tag;
    }
}
