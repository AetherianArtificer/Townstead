package com.aetherianartificer.townstead.chronicle.store;

import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.model.SentimentEntry;
import com.aetherianartificer.townstead.chronicle.model.VillageHistory;
import com.aetherianartificer.townstead.chronicle.model.VillagerMemory;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The chronicle hot tier: the aggregates gameplay reads at tick rate.
 * Counters, memories, village digests, sentiments, and news points live here
 * so mood/careers/relationships never touch the SQLite archive, and keep
 * working even if the archive file is lost.
 */
public class ChronicleSavedData extends SavedData {
    public static final String FILE_ID = "townstead_chronicles";
    public static final int SCHEMA_VERSION = 1;

    public static final int MAX_MEMORIES_PER_VILLAGER = 32;
    public static final int MAX_SENTIMENT_PARTNERS = 24;
    public static final float MEMORY_DAILY_DECAY = 0.97f;
    public static final float MEMORY_PRUNE_BELOW = 0.05f;
    public static final float SENTIMENT_DAILY_DECAY = 0.995f;
    public static final float SENTIMENT_PRUNE_BELOW = 0.1f;

    private long nextEventId = 1L;
    private long nextArcId = 1L;
    private long nextAccountId = 1L;

    public static final float MOOD_IMPACT_DAILY_DECAY = 0.85f;
    public static final float MOOD_IMPACT_PRUNE_BELOW = 0.25f;
    public static final float MOOD_TARGET_CAP = 15f;

    private final Map<UUID, Object2IntOpenHashMap<String>> counters = new HashMap<>();
    private final Map<UUID, List<VillagerMemory>> memories = new HashMap<>();
    private final Map<VillageKey, VillageHistory> histories = new HashMap<>();
    private final Map<UUID, Map<UUID, SentimentEntry>> sentiments = new HashMap<>();
    private final Object2IntOpenHashMap<UUID> newsPoints = new Object2IntOpenHashMap<>();
    // Belief-driven mood: accumulated on-learn impacts (decay daily) and the
    // portion currently applied to MCA mood (so removal reverses cleanly).
    private final it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap<UUID> moodImpacts =
            new it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap<>();
    private final it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap<UUID> appliedMoodDrift =
            new it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap<>();

    public ChronicleSavedData() {}

    public static ChronicleSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(ChronicleSavedData::new, ChronicleSavedData::load),
                FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                ChronicleSavedData::load,
                ChronicleSavedData::new,
                FILE_ID);
        *///?}
    }

    // ---- id sequences ----

    public long assignEventId() {
        long id = nextEventId++;
        setDirty();
        return id;
    }

    public long assignArcId() {
        long id = nextArcId++;
        setDirty();
        return id;
    }

    public long assignAccountId() {
        long id = nextAccountId++;
        setDirty();
        return id;
    }

    // ---- counters (truth-side; the Careers contract) ----

    public void addCounter(UUID subject, String key, int amount) {
        counters.computeIfAbsent(subject, ignored -> new Object2IntOpenHashMap<>()).addTo(key, amount);
        setDirty();
    }

    public int counter(UUID subject, String key) {
        Object2IntOpenHashMap<String> map = counters.get(subject);
        return map == null ? 0 : map.getInt(key);
    }

    public Map<String, Integer> countersFor(UUID subject) {
        Object2IntOpenHashMap<String> map = counters.get(subject);
        return map == null ? Map.of() : Map.copyOf(map);
    }

    /** Cooldown stamps ride the counter map under a reserved prefix. */
    public void putCounterRaw(UUID subject, String key, int value) {
        counters.computeIfAbsent(subject, ignored -> new Object2IntOpenHashMap<>()).put(key, value);
        setDirty();
    }

    // ---- memories (belief-side) ----

    public List<VillagerMemory> memoriesFor(UUID knower) {
        return memories.getOrDefault(knower, List.of());
    }

    public void addOrReinforceMemory(UUID knower, String memoryKey, @Nullable UUID otherParty,
                                     long day, float strength, float valence,
                                     Map<String, String> params) {
        List<VillagerMemory> list = memories.computeIfAbsent(knower, ignored -> new ArrayList<>());
        for (VillagerMemory memory : list) {
            if (memory.matches(memoryKey, otherParty)) {
                memory.reinforce(day, strength, valence);
                setDirty();
                return;
            }
        }
        list.add(new VillagerMemory(memoryKey, otherParty, day, strength, valence, params));
        if (list.size() > MAX_MEMORIES_PER_VILLAGER) {
            VillagerMemory weakest = null;
            for (VillagerMemory memory : list) {
                if (weakest == null || memory.strength() < weakest.strength()) weakest = memory;
            }
            list.remove(weakest);
        }
        setDirty();
    }

    // ---- village digests ----

    public VillageHistory historyFor(VillageKey key) {
        return histories.computeIfAbsent(key, ignored -> new VillageHistory());
    }

    public @Nullable VillageHistory historyIfPresent(VillageKey key) {
        return histories.get(key);
    }

    /** Admin reroll support: wipes a village's digest so pre-gen can run again. */
    public void clearHistory(VillageKey key) {
        if (histories.remove(key) != null) setDirty();
    }

    // ---- sentiment (belief-side) ----

    public float sentiment(UUID from, UUID toward) {
        Map<UUID, SentimentEntry> map = sentiments.get(from);
        SentimentEntry entry = map == null ? null : map.get(toward);
        return entry == null ? 0f : entry.value();
    }

    public @Nullable SentimentEntry sentimentEntry(UUID from, UUID toward) {
        Map<UUID, SentimentEntry> map = sentiments.get(from);
        return map == null ? null : map.get(toward);
    }

    public void adjustSentiment(UUID from, UUID toward, float delta, long day, long accountId) {
        Map<UUID, SentimentEntry> map = sentiments.computeIfAbsent(from, ignored -> new HashMap<>());
        SentimentEntry entry = map.get(toward);
        if (entry != null) {
            entry.adjust(delta, day, accountId);
        } else {
            if (map.size() >= MAX_SENTIMENT_PARTNERS) {
                UUID weakest = null;
                float weakestAbs = Float.MAX_VALUE;
                for (Map.Entry<UUID, SentimentEntry> e : map.entrySet()) {
                    float abs = Math.abs(e.getValue().value());
                    if (abs < weakestAbs) {
                        weakestAbs = abs;
                        weakest = e.getKey();
                    }
                }
                if (weakest != null) map.remove(weakest);
            }
            map.put(toward, new SentimentEntry(delta, day, accountId));
        }
        setDirty();
    }

    // ---- belief-driven mood (consumed by ChronicleMoodTicker) ----

    public void addMoodImpact(UUID knower, float amount) {
        if (amount == 0f) return;
        moodImpacts.addTo(knower, amount);
        setDirty();
    }

    /** The drift target: clamped accumulated impacts. */
    public float moodTarget(UUID knower) {
        float value = moodImpacts.getFloat(knower);
        return Math.max(-MOOD_TARGET_CAP, Math.min(value, MOOD_TARGET_CAP));
    }

    public float appliedMoodDrift(UUID knower) {
        return appliedMoodDrift.getFloat(knower);
    }

    public void setAppliedMoodDrift(UUID knower, float value) {
        if (value == 0f) {
            appliedMoodDrift.removeFloat(knower);
        } else {
            appliedMoodDrift.put(knower, value);
        }
        setDirty();
    }

    // ---- news points ----

    public int newsPoints(UUID player) {
        return newsPoints.getInt(player);
    }

    public void addNewsPoints(UUID player, int amount) {
        newsPoints.addTo(player, amount);
        setDirty();
    }

    // ---- daily decay (called once per day rollover) ----

    public void decayDaily() {
        boolean changed = false;
        for (List<VillagerMemory> list : memories.values()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                VillagerMemory memory = list.get(i);
                memory.decay(MEMORY_DAILY_DECAY);
                if (memory.strength() < MEMORY_PRUNE_BELOW) list.remove(i);
                changed = true;
            }
        }
        memories.values().removeIf(List::isEmpty);
        for (Map<UUID, SentimentEntry> map : sentiments.values()) {
            for (var it = map.entrySet().iterator(); it.hasNext(); ) {
                SentimentEntry entry = it.next().getValue();
                entry.decay(SENTIMENT_DAILY_DECAY);
                if (Math.abs(entry.value()) < SENTIMENT_PRUNE_BELOW) it.remove();
                changed = true;
            }
        }
        sentiments.values().removeIf(Map::isEmpty);
        for (var it = moodImpacts.object2FloatEntrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            float decayed = entry.getFloatValue() * MOOD_IMPACT_DAILY_DECAY;
            if (Math.abs(decayed) < MOOD_IMPACT_PRUNE_BELOW) {
                it.remove();
            } else {
                entry.setValue(decayed);
            }
            changed = true;
        }
        if (changed) setDirty();
    }

    // ---- diagnostics ----

    public int counterSubjects() { return counters.size(); }
    public int memoryHolders() { return memories.size(); }
    public int historyVillages() { return histories.size(); }
    public int sentimentHolders() { return sentiments.size(); }

    // ---- persistence ----

    //? if >=1.21 {
    public static ChronicleSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static ChronicleSavedData load(CompoundTag tag) {
    *///?}
        ChronicleSavedData data = new ChronicleSavedData();
        data.nextEventId = Math.max(1L, tag.getLong("nextEventId"));
        data.nextArcId = Math.max(1L, tag.getLong("nextArcId"));
        data.nextAccountId = Math.max(1L, tag.getLong("nextAccountId"));

        ListTag counterList = tag.getList("counters", Tag.TAG_COMPOUND);
        for (int i = 0; i < counterList.size(); i++) {
            CompoundTag e = counterList.getCompound(i);
            if (!e.hasUUID("id")) continue;
            Object2IntOpenHashMap<String> map = new Object2IntOpenHashMap<>();
            CompoundTag values = e.getCompound("values");
            for (String k : values.getAllKeys()) map.put(k, values.getInt(k));
            data.counters.put(e.getUUID("id"), map);
        }

        ListTag memoryList = tag.getList("memories", Tag.TAG_COMPOUND);
        for (int i = 0; i < memoryList.size(); i++) {
            CompoundTag e = memoryList.getCompound(i);
            if (!e.hasUUID("id")) continue;
            List<VillagerMemory> list = new ArrayList<>();
            ListTag entries = e.getList("entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) list.add(VillagerMemory.load(entries.getCompound(j)));
            data.memories.put(e.getUUID("id"), list);
        }

        ListTag historyList = tag.getList("histories", Tag.TAG_COMPOUND);
        for (int i = 0; i < historyList.size(); i++) {
            CompoundTag e = historyList.getCompound(i);
            ResourceLocation dim;
            try {
                dim = parseRl(e.getString("dim"));
            } catch (Exception ignored) {
                continue;
            }
            data.histories.put(new VillageKey(dim, e.getInt("village")),
                    VillageHistory.load(e.getCompound("history")));
        }

        ListTag sentimentList = tag.getList("sentiments", Tag.TAG_COMPOUND);
        for (int i = 0; i < sentimentList.size(); i++) {
            CompoundTag e = sentimentList.getCompound(i);
            if (!e.hasUUID("id")) continue;
            Map<UUID, SentimentEntry> map = new HashMap<>();
            ListTag entries = e.getList("entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) {
                CompoundTag se = entries.getCompound(j);
                if (!se.hasUUID("toward")) continue;
                map.put(se.getUUID("toward"), SentimentEntry.load(se));
            }
            data.sentiments.put(e.getUUID("id"), map);
        }

        ListTag pointsList = tag.getList("newsPoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < pointsList.size(); i++) {
            CompoundTag e = pointsList.getCompound(i);
            if (e.hasUUID("id")) data.newsPoints.put(e.getUUID("id"), e.getInt("points"));
        }

        ListTag moodList = tag.getList("moodImpacts", Tag.TAG_COMPOUND);
        for (int i = 0; i < moodList.size(); i++) {
            CompoundTag e = moodList.getCompound(i);
            if (!e.hasUUID("id")) continue;
            data.moodImpacts.put(e.getUUID("id"), e.getFloat("impact"));
            if (e.contains("applied")) data.appliedMoodDrift.put(e.getUUID("id"), e.getFloat("applied"));
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
        tag.putInt("schemaVersion", SCHEMA_VERSION);
        tag.putLong("nextEventId", nextEventId);
        tag.putLong("nextArcId", nextArcId);
        tag.putLong("nextAccountId", nextAccountId);

        ListTag counterList = new ListTag();
        for (Map.Entry<UUID, Object2IntOpenHashMap<String>> e : counters.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", e.getKey());
            CompoundTag values = new CompoundTag();
            for (Object2IntMap.Entry<String> v : e.getValue().object2IntEntrySet()) {
                values.putInt(v.getKey(), v.getIntValue());
            }
            entry.put("values", values);
            counterList.add(entry);
        }
        tag.put("counters", counterList);

        ListTag memoryList = new ListTag();
        for (Map.Entry<UUID, List<VillagerMemory>> e : memories.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", e.getKey());
            ListTag entries = new ListTag();
            for (VillagerMemory memory : e.getValue()) entries.add(memory.save());
            entry.put("entries", entries);
            memoryList.add(entry);
        }
        tag.put("memories", memoryList);

        ListTag historyList = new ListTag();
        for (Map.Entry<VillageKey, VillageHistory> e : histories.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("dim", e.getKey().dimension().toString());
            entry.putInt("village", e.getKey().villageId());
            entry.put("history", e.getValue().save());
            historyList.add(entry);
        }
        tag.put("histories", historyList);

        ListTag sentimentList = new ListTag();
        for (Map.Entry<UUID, Map<UUID, SentimentEntry>> e : sentiments.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", e.getKey());
            ListTag entries = new ListTag();
            for (Map.Entry<UUID, SentimentEntry> se : e.getValue().entrySet()) {
                CompoundTag s = se.getValue().save();
                s.putUUID("toward", se.getKey());
                entries.add(s);
            }
            entry.put("entries", entries);
            sentimentList.add(entry);
        }
        tag.put("sentiments", sentimentList);

        ListTag pointsList = new ListTag();
        for (Object2IntMap.Entry<UUID> e : newsPoints.object2IntEntrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", e.getKey());
            entry.putInt("points", e.getIntValue());
            pointsList.add(entry);
        }
        tag.put("newsPoints", pointsList);

        ListTag moodList = new ListTag();
        java.util.Set<UUID> moodIds = new java.util.HashSet<>(moodImpacts.keySet());
        moodIds.addAll(appliedMoodDrift.keySet());
        for (UUID id : moodIds) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putFloat("impact", moodImpacts.getFloat(id));
            float applied = appliedMoodDrift.getFloat(id);
            if (applied != 0f) entry.putFloat("applied", applied);
            moodList.add(entry);
        }
        tag.put("moodImpacts", moodList);
        return tag;
    }

    private static ResourceLocation parseRl(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
