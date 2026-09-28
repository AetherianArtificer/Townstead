package com.aetherianartificer.townstead.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One player's side of every story they have heard: the saved Ink state and their quests. Kept on
 * the player, so it travels with them and resets with them.
 */
public final class PlayerStories {
    private static final String KEY = "townstead_stories";

    public enum QuestState { ACTIVE, READY, COMPLETE }

    public static final class QuestRecord {
        public final String knot;
        public QuestState state = QuestState.ACTIVE;
        /** Per goal: the running count for counter goals, the last reading for state goals. */
        public long[] values;
        public boolean skipped;
        /** Whether this quest's rewards have been handed over. They are given once, on completion. */
        public boolean rewarded;

        QuestRecord(String knot, int goals) {
            this.knot = knot;
            this.values = new long[goals];
        }
    }

    public static final class Entry {
        public final ResourceLocation story;
        public final UUID villager;
        public String villagerName;
        /** Just the given name, for goal lines where the full name is too long. */
        public String givenName = "";
        public String ink = "";
        public String hash = "";
        public int operations;
        public final Map<String, QuestRecord> quests = new LinkedHashMap<>();
        /** How often each of the story's event goals has happened since the player met the villager. */
        public final Map<String, Long> seen = new LinkedHashMap<>();

        Entry(ResourceLocation story, UUID villager, String villagerName) {
            this.story = story;
            this.villager = villager;
            this.villagerName = villagerName;
        }

        public QuestRecord quest(String knot, int goals) {
            QuestRecord record = quests.computeIfAbsent(knot, k -> new QuestRecord(k, goals));
            if (record.values.length != goals) record.values = java.util.Arrays.copyOf(record.values, goals);
            return record;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static String key(StoryDefinition story, UUID villager) {
        return story.id() + "|" + (story.bind() == StoryDefinition.Bind.PLAYER ? "*" : villager.toString());
    }

    public @Nullable Entry get(String key) { return entries.get(key); }

    public Entry getOrCreate(String key, StoryDefinition story, UUID villager, String villagerName) {
        return entries.computeIfAbsent(key, k -> new Entry(story.id(), villager, villagerName));
    }

    public Collection<Entry> entries() { return entries.values(); }

    public boolean remove(ResourceLocation story) {
        return entries.values().removeIf(entry -> entry.story.equals(story));
    }

    public static PlayerStories load(Player player) {
        PlayerStories out = new PlayerStories();
        CompoundTag root = data(player);
        if (!root.contains(KEY, Tag.TAG_LIST)) return out;
        for (Tag raw : root.getList(KEY, Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            ResourceLocation story = ResourceLocation.tryParse(tag.getString("story"));
            if (story == null || !tag.hasUUID("villager")) continue;
            Entry entry = new Entry(story, tag.getUUID("villager"), tag.getString("name"));
            entry.givenName = tag.getString("given");
            entry.ink = tag.getString("ink");
            entry.hash = tag.getString("hash");
            entry.operations = tag.getInt("ops");
            for (Tag q : tag.getList("quests", Tag.TAG_COMPOUND)) {
                CompoundTag qt = (CompoundTag) q;
                long[] values = qt.getLongArray("values");
                QuestRecord record = new QuestRecord(qt.getString("knot"), values.length);
                record.values = values;
                int state = qt.getByte("state");
                record.state = QuestState.values()[Math.max(0, Math.min(state, QuestState.values().length - 1))];
                record.skipped = qt.getBoolean("skipped");
                record.rewarded = qt.getBoolean("rewarded");
                entry.quests.put(record.knot, record);
            }
            CompoundTag seen = tag.getCompound("seen");
            for (String name : seen.getAllKeys()) entry.seen.put(name, seen.getLong(name));
            out.entries.put(tag.getString("key"), entry);
        }
        return out;
    }

    public void save(Player player) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            CompoundTag tag = new CompoundTag();
            tag.putString("key", e.getKey());
            tag.putString("story", entry.story.toString());
            tag.putUUID("villager", entry.villager);
            tag.putString("name", entry.villagerName);
            tag.putString("given", entry.givenName);
            tag.putString("ink", entry.ink);
            tag.putString("hash", entry.hash);
            tag.putInt("ops", entry.operations);
            ListTag quests = new ListTag();
            for (QuestRecord record : entry.quests.values()) {
                CompoundTag qt = new CompoundTag();
                qt.putString("knot", record.knot);
                qt.putByte("state", (byte) record.state.ordinal());
                qt.putLongArray("values", record.values);
                qt.putBoolean("skipped", record.skipped);
                qt.putBoolean("rewarded", record.rewarded);
                quests.add(qt);
            }
            tag.put("quests", quests);
            CompoundTag seen = new CompoundTag();
            entry.seen.forEach(seen::putLong);
            tag.put("seen", seen);
            list.add(tag);
        }
        CompoundTag root = data(player);
        root.put(KEY, list);
        store(player, root);
    }

    private static CompoundTag data(Player player) {
        //? if neoforge {
        return player.getData(com.aetherianartificer.townstead.Townstead.PLAYER_ROOT_DATA);
        //?} else {
        /*return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        *///?}
    }

    private static void store(Player player, CompoundTag root) {
        //? if neoforge {
        player.setData(com.aetherianartificer.townstead.Townstead.PLAYER_ROOT_DATA, root);
        //?} else {
        /*player.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        *///?}
    }
}
