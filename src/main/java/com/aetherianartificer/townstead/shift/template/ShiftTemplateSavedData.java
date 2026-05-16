package com.aetherianartificer.townstead.shift.template;

import com.aetherianartificer.townstead.shift.ShiftData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-overworld persistence of user-created shift templates. Built-ins are
 * not stored here — those come from data pack JSON.
 */
public class ShiftTemplateSavedData extends SavedData {

    public static final String FILE_ID = "townstead_shift_templates";

    private static final String KEY_TEMPLATES = "templates";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_SHIFTS = "shifts";
    private static final String KEY_CHRONO = "chronotype";

    private final Map<ResourceLocation, ShiftTemplate> templates = new LinkedHashMap<>();

    public ShiftTemplateSavedData() {}

    public static ShiftTemplateSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(ShiftTemplateSavedData::new, ShiftTemplateSavedData::load),
                FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                ShiftTemplateSavedData::load,
                ShiftTemplateSavedData::new,
                FILE_ID);
        *///?}
    }

    //? if >=1.21 {
    public static ShiftTemplateSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static ShiftTemplateSavedData load(CompoundTag tag) {
    *///?}
        ShiftTemplateSavedData data = new ShiftTemplateSavedData();
        if (!tag.contains(KEY_TEMPLATES, Tag.TAG_LIST)) return data;
        ListTag list = tag.getList(KEY_TEMPLATES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String idStr = entry.getString(KEY_ID);
            String name = entry.getString(KEY_NAME);
            int[] shifts = entry.getIntArray(KEY_SHIFTS);
            String chronoStr = entry.contains(KEY_CHRONO) ? entry.getString(KEY_CHRONO) : null;
            ResourceLocation id;
            try {
                //? if >=1.21 {
                id = ResourceLocation.parse(idStr);
                //?} else {
                /*id = new ResourceLocation(idStr);
                *///?}
            } catch (Exception ex) {
                continue;
            }
            if (shifts.length != ShiftData.HOURS_PER_DAY) continue;
            try {
                Optional<Chronotype> chrono = chronoStr != null && !chronoStr.isBlank()
                        ? Optional.of(Chronotype.fromName(chronoStr))
                        : Optional.empty();
                ShiftTemplate template = new ShiftTemplate(id, name, shifts, chrono, false);
                data.templates.put(id, template);
            } catch (IllegalArgumentException ignored) {}
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
        for (ShiftTemplate template : templates.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_ID, template.id().toString());
            entry.putString(KEY_NAME, template.displayName());
            entry.putIntArray(KEY_SHIFTS, template.copyShifts());
            template.chronotype().ifPresent(c -> entry.putString(KEY_CHRONO, c.name()));
            list.add(entry);
        }
        tag.put(KEY_TEMPLATES, list);
        return tag;
    }

    public Collection<ShiftTemplate> all() {
        return new ArrayList<>(templates.values());
    }

    public Optional<ShiftTemplate> get(ResourceLocation id) {
        return Optional.ofNullable(templates.get(id));
    }

    public void put(ShiftTemplate template) {
        if (!template.isUserTemplate()) {
            throw new IllegalArgumentException("Refusing to store built-in template in SavedData: " + template.id());
        }
        templates.put(template.id(), template);
        setDirty();
    }

    public boolean remove(ResourceLocation id) {
        if (templates.remove(id) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public List<ShiftTemplate> snapshot() {
        return new ArrayList<>(templates.values());
    }
}
