package com.aetherianartificer.townstead.profession.career;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import com.aetherianartificer.townstead.villager.ProfessionXp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Durable history and active choices shared by player and villager Careers. */
public final class CareerProfile {
    public static final int SCHEMA_VERSION = 1;

    private ResourceLocation primaryVocation;
    private final Set<ResourceLocation> careerHistory = new LinkedHashSet<>();
    private final Set<ResourceLocation> learnedChoices = new LinkedHashSet<>();
    private final Map<ResourceLocation, ResourceLocation> activeBySkillGroup = new LinkedHashMap<>();
    private final Set<ResourceLocation> acquiredCareers = new LinkedHashSet<>();
    private final Set<ResourceLocation> discoveries = new LinkedHashSet<>();
    private final List<ResourceLocation> activeLoadout = new ArrayList<>();
    private final Map<String, ProfessionXp> progress = new LinkedHashMap<>();
    private final Set<ResourceLocation> trackedCareers = new LinkedHashSet<>();
    private long lastVocationChangeDay = -1L;

    public ResourceLocation primaryVocation() { return primaryVocation; }
    public Set<ResourceLocation> careerHistory() { return Set.copyOf(careerHistory); }
    public Set<ResourceLocation> learnedChoices() { return Set.copyOf(learnedChoices); }
    public Map<ResourceLocation, ResourceLocation> activeBySkillGroup() { return Map.copyOf(activeBySkillGroup); }
    public Set<ResourceLocation> acquiredCareers() { return Set.copyOf(acquiredCareers); }
    public Set<ResourceLocation> discoveries() { return Set.copyOf(discoveries); }
    public List<ResourceLocation> activeLoadout() { return List.copyOf(activeLoadout); }
    /** Reads fall back from the canonical full id to the bare legacy key old saves wrote. */
    public ProfessionXp professionXp(String careerId) {
        if (careerId == null) return ProfessionXp.EMPTY;
        ProfessionXp direct = progress.get(careerId);
        if (direct != null) return direct;
        int colon = careerId.indexOf(':');
        if (colon >= 0) {
            ProfessionXp legacy = progress.get(careerId.substring(colon + 1));
            if (legacy != null) return legacy;
        }
        return ProfessionXp.EMPTY;
    }
    /** Writes under the canonical id and retire the bare legacy key, migrating lazily. */
    public void setProfessionXp(String careerId, ProfessionXp value) {
        if (careerId == null || careerId.isBlank()) return;
        progress.put(careerId, value == null ? ProfessionXp.EMPTY : value);
        int colon = careerId.indexOf(':');
        if (colon >= 0) progress.remove(careerId.substring(colon + 1));
    }

    public Set<ResourceLocation> trackedCareers() { return Set.copyOf(trackedCareers); }

    public boolean track(ResourceLocation id) { return id != null && trackedCareers.add(id); }

    public boolean untrack(ResourceLocation id) { return id != null && trackedCareers.remove(id); }

    public long lastVocationChangeDay() { return lastVocationChangeDay; }

    public void setLastVocationChangeDay(long day) { this.lastVocationChangeDay = day; }

    public boolean setPrimaryVocation(ResourceLocation vocation) {
        if (java.util.Objects.equals(primaryVocation, vocation)) return false;
        if (primaryVocation != null) careerHistory.add(primaryVocation);
        primaryVocation = vocation;
        if (vocation != null) careerHistory.add(vocation);
        return true;
    }

    /** Learning is permanent history. Activation is a separate operation. */
    public boolean learnChoice(ResourceLocation choice) {
        return choice != null && learnedChoices.add(choice);
    }

    public boolean activateSkill(ResourceLocation skillGroup, ResourceLocation skill) {
        if (skillGroup == null || skill == null || !learnedChoices.contains(skill)) return false;
        return !skill.equals(activeBySkillGroup.put(skillGroup, skill));
    }

    public boolean acquireCareer(ResourceLocation id) {
        return id != null && acquiredCareers.add(id);
    }

    public boolean discover(ResourceLocation id) {
        return id != null && discoveries.add(id);
    }

    public void setActiveLoadout(List<ResourceLocation> abilities, int maximum) {
        activeLoadout.clear();
        if (abilities == null || maximum <= 0) return;
        LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>(abilities);
        for (ResourceLocation ability : unique) {
            if (ability != null) activeLoadout.add(ability);
            if (activeLoadout.size() >= maximum) break;
        }
    }

    /** Explicit admin/migration repair only; normal gameplay never forgets history. */
    public boolean adminForgetChoice(ResourceLocation choice) {
        if (choice == null || !learnedChoices.remove(choice)) return false;
        activeBySkillGroup.values().removeIf(choice::equals);
        return true;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", SCHEMA_VERSION);
        if (primaryVocation != null) tag.putString("primary", primaryVocation.toString());
        if (lastVocationChangeDay >= 0) tag.putLong("vocationDay", lastVocationChangeDay);
        putIds(tag, "history", careerHistory);
        putIds(tag, "learned", learnedChoices);
        putIds(tag, "advanced", acquiredCareers);
        putIds(tag, "discoveries", discoveries);
        putIds(tag, "trackedGoals", trackedCareers);
        putIds(tag, "loadout", activeLoadout);
        CompoundTag active = new CompoundTag();
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : activeBySkillGroup.entrySet()) {
            active.putString(entry.getKey().toString(), entry.getValue().toString());
        }
        tag.put("activeChoices", active);
        CompoundTag progressTag = new CompoundTag();
        for (Map.Entry<String, ProfessionXp> entry : progress.entrySet()) {
            ProfessionXp value = entry.getValue();
            if (value == null || value.isEmpty()) continue;
            CompoundTag xp = new CompoundTag();
            xp.putInt("xp", value.xp());
            xp.putInt("tier", value.tier());
            xp.putLong("lastTierUp", value.lastTierUpTick());
            xp.putLong("xpDay", value.xpDay());
            xp.putInt("xpToday", value.xpToday());
            progressTag.put(entry.getKey(), xp);
        }
        tag.put("progress", progressTag);
        return tag;
    }

    public static CareerProfile fromTag(CompoundTag tag) {
        CareerProfile profile = new CareerProfile();
        profile.primaryVocation = ResourceLocation.tryParse(tag.getString("primary"));
        profile.lastVocationChangeDay = tag.contains("vocationDay") ? tag.getLong("vocationDay") : -1L;
        readIds(tag, "history", profile.careerHistory);
        readSkillIds(tag, "learned", profile.learnedChoices);
        readIds(tag, "advanced", profile.acquiredCareers);
        readIds(tag, "discoveries", profile.discoveries);
        readIds(tag, "trackedGoals", profile.trackedCareers);
        readSkillIds(tag, "loadout", profile.activeLoadout);
        CompoundTag active = tag.getCompound("activeChoices");
        for (String key : active.getAllKeys()) {
            ResourceLocation skillGroup = ResourceLocation.tryParse(key);
            ResourceLocation choice = com.aetherianartificer.townstead.profession.def.SkillDefs
                    .canonicalId(ResourceLocation.tryParse(active.getString(key)));
            if (skillGroup != null && choice != null && profile.learnedChoices.contains(choice)) {
                profile.activeBySkillGroup.put(skillGroup, choice);
            }
        }
        if (profile.primaryVocation != null) profile.careerHistory.add(profile.primaryVocation);
        CompoundTag progress = tag.getCompound("progress");
        for (String key : progress.getAllKeys()) {
            CompoundTag xp = progress.getCompound(key);
            profile.progress.put(key, new ProfessionXp(xp.getInt("xp"), xp.getInt("tier"),
                    xp.getLong("lastTierUp"), xp.getLong("xpDay"), xp.getInt("xpToday")));
        }
        return profile;
    }

    private static void putIds(CompoundTag tag, String key, Iterable<ResourceLocation> ids) {
        ListTag list = new ListTag();
        for (ResourceLocation id : ids) if (id != null) list.add(StringTag.valueOf(id.toString()));
        if (!list.isEmpty()) tag.put(key, list);
    }

    private static void readIds(CompoundTag tag, String key, java.util.Collection<ResourceLocation> out) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) out.add(id);
        }
    }

    /** Skill ids saved under a legacy flat form resolve to their path-scoped successor. */
    private static void readSkillIds(CompoundTag tag, String key,
                                     java.util.Collection<ResourceLocation> out) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = com.aetherianartificer.townstead.profession.def.SkillDefs
                    .canonicalId(ResourceLocation.tryParse(list.getString(i)));
            if (id != null) out.add(id);
        }
    }
}
