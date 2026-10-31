package com.aetherianartificer.townstead.rebirth;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What past lives knew, and what each player is relearning. A legacy is the career record a life
 * ended with, kept under that life's memorial id; its journal points at it. Reading a journal sets
 * relearning targets on the reader: career XP to climb back to, and skills to take up again.
 */
public final class CareerLegacies extends SavedData {
    private static final String FILE_ID = "townstead_career_legacies";

    /** A past life's career record, in the same form a career profile saves itself. */
    public record Legacy(String name, CompoundTag profile) {}

    /** Targets a player is climbing back to. Skills keep the order they were first learned in. */
    public static final class Relearn {
        final Map<String, Integer> xp = new LinkedHashMap<>();
        final List<String> skills = new ArrayList<>();
        final Map<String, String> active = new LinkedHashMap<>();

        public Map<String, Integer> xp() { return xp; }
        public List<String> skills() { return skills; }
        public Map<String, String> active() { return active; }

        boolean isEmpty() {
            return xp.isEmpty() && skills.isEmpty();
        }
    }

    private final Map<UUID, Legacy> legacies = new HashMap<>();
    private final Map<UUID, Relearn> relearning = new HashMap<>();

    public static CareerLegacies get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CareerLegacies::new, CareerLegacies::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(CareerLegacies::load, CareerLegacies::new, FILE_ID);
        *///?}
    }

    public @Nullable Legacy legacy(UUID memorialId) {
        return legacies.get(memorialId);
    }

    public void putLegacy(UUID memorialId, Legacy legacy) {
        legacies.put(memorialId, legacy);
        setDirty();
    }

    public @Nullable Relearn relearning(UUID player) {
        return relearning.get(player);
    }

    /** Merges a legacy into the player's targets: the higher XP per career wins, skills add up. */
    public void addTargets(UUID player, Map<String, Integer> xp, List<String> skills, Map<String, String> active) {
        Relearn target = relearning.computeIfAbsent(player, ignored -> new Relearn());
        xp.forEach((career, value) -> target.xp.merge(career, value, Math::max));
        for (String skill : skills) if (!target.skills.contains(skill)) target.skills.add(skill);
        active.forEach(target.active::putIfAbsent);
        setDirty();
    }

    /** Drops what the player has reached, and the whole entry once nothing is left. */
    public void settle(UUID player, Map<String, Integer> reached, java.util.Set<String> learned) {
        Relearn target = relearning.get(player);
        if (target == null) return;
        boolean changed = target.xp.entrySet().removeIf(e -> reached.getOrDefault(e.getKey(), 0) >= e.getValue());
        changed |= target.skills.removeIf(learned::contains);
        if (target.isEmpty()) {
            relearning.remove(player);
            changed = true;
        }
        if (changed) setDirty();
    }

    //? if >=1.21 {
    static CareerLegacies load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*static CareerLegacies load(CompoundTag tag) {
    *///?}
        CareerLegacies data = new CareerLegacies();
        CompoundTag legacies = tag.getCompound("legacies");
        for (String key : legacies.getAllKeys()) {
            CompoundTag entry = legacies.getCompound(key);
            try {
                data.legacies.put(UUID.fromString(key), new Legacy(entry.getString("name"), entry.getCompound("profile")));
            } catch (IllegalArgumentException ignored) {}
        }
        CompoundTag relearning = tag.getCompound("relearning");
        for (String key : relearning.getAllKeys()) {
            CompoundTag entry = relearning.getCompound(key);
            Relearn target = new Relearn();
            CompoundTag xp = entry.getCompound("xp");
            for (String career : xp.getAllKeys()) target.xp.put(career, xp.getInt(career));
            ListTag skills = entry.getList("skills", Tag.TAG_STRING);
            for (int i = 0; i < skills.size(); i++) target.skills.add(skills.getString(i));
            CompoundTag active = entry.getCompound("active");
            for (String group : active.getAllKeys()) target.active.put(group, active.getString(group));
            try {
                data.relearning.put(UUID.fromString(key), target);
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
        CompoundTag legacyTag = new CompoundTag();
        legacies.forEach((id, legacy) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", legacy.name());
            entry.put("profile", legacy.profile());
            legacyTag.put(id.toString(), entry);
        });
        tag.put("legacies", legacyTag);
        CompoundTag relearnTag = new CompoundTag();
        relearning.forEach((id, target) -> {
            CompoundTag entry = new CompoundTag();
            CompoundTag xp = new CompoundTag();
            target.xp.forEach(xp::putInt);
            entry.put("xp", xp);
            ListTag skills = new ListTag();
            for (String skill : target.skills) skills.add(StringTag.valueOf(skill));
            entry.put("skills", skills);
            CompoundTag active = new CompoundTag();
            target.active.forEach(active::putString);
            entry.put("active", active);
            relearnTag.put(id.toString(), entry);
        });
        tag.put("relearning", relearnTag);
        return tag;
    }
}
