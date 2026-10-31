package com.aetherianartificer.townstead.chronicle.model;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A village's public digest: the notable entries that define its remembered
 * history, plus per-category counts of everything that ever happened there.
 * Pre-gen writes fabricated entries here directly.
 */
public final class VillageHistory {

    public static final int MAX_ENTRIES = 256;

    /** Headline strings are server-resolved at record time; params fill display templates. */
    public record Entry(long worldDay, long eventId, String templateId,
                        String headlineLiteral, String headlineLangKey,
                        Map<String, String> params) {
        public Entry {
            params = params == null ? Map.of() : Map.copyOf(params);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Object2IntOpenHashMap<String> eventCounts = new Object2IntOpenHashMap<>();

    public List<Entry> entries() { return entries; }

    public int countFor(String category) { return eventCounts.getInt(category); }

    public Map<String, Integer> counts() {
        Map<String, Integer> copy = new HashMap<>();
        for (Object2IntMap.Entry<String> entry : eventCounts.object2IntEntrySet()) {
            copy.put(entry.getKey(), entry.getIntValue());
        }
        return Map.copyOf(copy);
    }

    public void bumpCount(String category) { eventCounts.addTo(category, 1); }

    /** Adds a notable entry, evicting the oldest non-founding entry past the cap. */
    public void addEntry(Entry entry) {
        entries.add(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() > 1 ? 1 : 0);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag e = new CompoundTag();
            e.putLong("day", entry.worldDay());
            e.putLong("event", entry.eventId());
            e.putString("template", entry.templateId());
            e.putString("lit", entry.headlineLiteral());
            e.putString("lang", entry.headlineLangKey());
            if (!entry.params().isEmpty()) {
                CompoundTag p = new CompoundTag();
                for (Map.Entry<String, String> pe : entry.params().entrySet()) p.putString(pe.getKey(), pe.getValue());
                e.put("params", p);
            }
            list.add(e);
        }
        tag.put("entries", list);
        CompoundTag counts = new CompoundTag();
        for (Object2IntMap.Entry<String> e : eventCounts.object2IntEntrySet()) {
            counts.putInt(e.getKey(), e.getIntValue());
        }
        tag.put("counts", counts);
        return tag;
    }

    public static VillageHistory load(CompoundTag tag) {
        VillageHistory history = new VillageHistory();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            Map<String, String> params = new HashMap<>();
            if (e.contains("params")) {
                CompoundTag p = e.getCompound("params");
                for (String k : p.getAllKeys()) params.put(k, p.getString(k));
            }
            history.entries.add(new Entry(e.getLong("day"), e.getLong("event"), e.getString("template"),
                    e.getString("lit"), e.getString("lang"), params));
        }
        CompoundTag counts = tag.getCompound("counts");
        for (String k : counts.getAllKeys()) history.eventCounts.put(k, counts.getInt(k));
        return history;
    }
}
