package com.aetherianartificer.townstead.shift.weekplan;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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

/**
 * Per-overworld persistence of user-created week plans. Built-ins are not
 * stored here — those come from data pack JSON. Mirrors
 * {@code ShiftTemplateSavedData}.
 */
public class WeekPlanSavedData extends SavedData {

    public static final String FILE_ID = "townstead_week_plans";

    private static final String KEY_PLANS = "plans";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_DAYS = "days";

    private final Map<ResourceLocation, WeekPlan> plans = new LinkedHashMap<>();

    public WeekPlanSavedData() {}

    public static WeekPlanSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(WeekPlanSavedData::new, WeekPlanSavedData::load),
                FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                WeekPlanSavedData::load,
                WeekPlanSavedData::new,
                FILE_ID);
        *///?}
    }

    //? if >=1.21 {
    public static WeekPlanSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static WeekPlanSavedData load(CompoundTag tag) {
    *///?}
        WeekPlanSavedData data = new WeekPlanSavedData();
        if (!tag.contains(KEY_PLANS, Tag.TAG_LIST)) return data;
        ListTag list = tag.getList(KEY_PLANS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String idStr = entry.getString(KEY_ID);
            String name = entry.getString(KEY_NAME);
            List<String> days = new ArrayList<>();
            ListTag dayList = entry.getList(KEY_DAYS, Tag.TAG_STRING);
            for (int j = 0; j < dayList.size(); j++) days.add(dayList.getString(j));
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
            try {
                data.plans.put(id, new WeekPlan(id, name, days, false));
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
        for (WeekPlan plan : plans.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_ID, plan.id().toString());
            entry.putString(KEY_NAME, plan.displayName());
            ListTag dayList = new ListTag();
            for (String d : plan.dayTemplates()) dayList.add(StringTag.valueOf(d == null ? "" : d));
            entry.put(KEY_DAYS, dayList);
            list.add(entry);
        }
        tag.put(KEY_PLANS, list);
        return tag;
    }

    public Collection<WeekPlan> all() {
        return new ArrayList<>(plans.values());
    }

    public java.util.Optional<WeekPlan> get(ResourceLocation id) {
        return java.util.Optional.ofNullable(plans.get(id));
    }

    public void put(WeekPlan plan) {
        if (!plan.isUserPlan()) {
            throw new IllegalArgumentException("Refusing to store built-in week plan in SavedData: " + plan.id());
        }
        plans.put(plan.id(), plan);
        setDirty();
    }

    public boolean remove(ResourceLocation id) {
        if (plans.remove(id) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public List<WeekPlan> snapshot() {
        return new ArrayList<>(plans.values());
    }
}
