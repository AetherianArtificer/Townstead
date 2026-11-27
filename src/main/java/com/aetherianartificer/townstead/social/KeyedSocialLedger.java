package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keyed social changes that must not stack: mood changes held by a key, daily caps, and the hearts each key took so that
 * amends can give them back. One record for the server, so a restart cannot reset a cap, and players and villagers are
 * kept the same way.
 *
 * <p>Mood works as the chronicle's mood drift does: the ledger keeps the total it has applied to a villager, and only the
 * difference between that and the sum of its live keys ever reaches MCA's mood. A key that ends or is cleared takes its
 * part back with it.</p>
 */
public final class KeyedSocialLedger extends SavedData {
    public static final String FILE_ID = "townstead_keyed_social";
    private static final int MOOD_LIMIT = 15;
    private static final long MOOD_EVERY = 100;

    private record Mood(int amount, long until) {}

    private record Daily(long day, int used) {}

    private final Map<UUID, Map<String, Mood>> moods = new HashMap<>();
    private final Map<UUID, Integer> applied = new HashMap<>();
    private final Map<String, Daily> daily = new HashMap<>();
    private final Map<String, Integer> taken = new LinkedHashMap<>();

    public KeyedSocialLedger() {}

    public static KeyedSocialLedger get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(KeyedSocialLedger::new, KeyedSocialLedger::load), FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(KeyedSocialLedger::load, KeyedSocialLedger::new, FILE_ID);
        *///?}
    }

    // ---- Mood.

    /** Holds {@code amount} of mood on a villager under {@code key}, replacing what the key held. 0 ticks lasts until cleared. */
    public static boolean adjustMood(LivingEntity entity, String key, int amount, long durationTicks) {
        if (!(entity instanceof VillagerEntityMCA villager) || !(villager.level() instanceof ServerLevel level)
                || key == null || key.isBlank()) return false;
        KeyedSocialLedger ledger = get(level.getServer());
        long until = durationTicks > 0 ? level.getGameTime() + durationTicks : 0;
        ledger.moods.computeIfAbsent(villager.getUUID(), u -> new LinkedHashMap<>()).put(key, new Mood(amount, until));
        ledger.setDirty();
        ledger.converge(villager, level.getGameTime());
        return true;
    }

    /** Takes back the mood a key holds on a villager. */
    public static boolean clearMood(LivingEntity entity, String key) {
        if (!(entity instanceof VillagerEntityMCA villager) || !(villager.level() instanceof ServerLevel level)) return false;
        KeyedSocialLedger ledger = get(level.getServer());
        Map<String, Mood> held = ledger.moods.get(villager.getUUID());
        if (held == null || held.remove(key) == null) return false;
        ledger.setDirty();
        ledger.converge(villager, level.getGameTime());
        return true;
    }

    /** Ends the keys whose time is up. Called from the villager tick; does its work every few seconds. */
    public static void tick(VillagerEntityMCA villager, long gameTime) {
        if ((gameTime + villager.getId()) % MOOD_EVERY != 0 || !(villager.level() instanceof ServerLevel level)) return;
        KeyedSocialLedger ledger = get(level.getServer());
        if (!ledger.moods.containsKey(villager.getUUID()) && !ledger.applied.containsKey(villager.getUUID())) return;
        ledger.converge(villager, gameTime);
    }

    private void converge(VillagerEntityMCA villager, long now) {
        UUID id = villager.getUUID();
        int target = 0;
        Map<String, Mood> held = moods.get(id);
        if (held != null) {
            for (Iterator<Mood> it = held.values().iterator(); it.hasNext(); ) {
                Mood mood = it.next();
                if (mood.until() != 0 && mood.until() <= now) {
                    it.remove();
                    setDirty();
                } else {
                    target += mood.amount();
                }
            }
            if (held.isEmpty()) moods.remove(id);
        }
        target = Math.max(-MOOD_LIMIT, Math.min(MOOD_LIMIT, target));
        int had = applied.getOrDefault(id, 0);
        if (target == had) return;
        // MCA caps mood, so a change can land short. Keep what really landed, or ending the key would take back more than
        // it gave.
        var brain = villager.getVillagerBrain();
        int before = brain.getMoodValue();
        brain.modifyMoodValue(target - had);
        int landed = had + (brain.getMoodValue() - before);
        if (landed == had) return;
        if (landed == 0) applied.remove(id);
        else applied.put(id, landed);
        setDirty();
    }

    // ---- Hearts.

    /**
     * Changes a villager's MCA hearts with a player by {@code amount}, at most {@code dailyCap} for this key a day (0 for
     * no cap). Hearts a key takes are kept so {@link #returnHearts} can give them back. Returns what changed.
     */
    public static int adjustHearts(LivingEntity entity, LivingEntity other, String key, int amount, int dailyCap) {
        if (!(entity instanceof VillagerEntityMCA villager) || !(other instanceof Player player)
                || !(villager.level() instanceof ServerLevel level) || key == null || key.isBlank() || amount == 0) return 0;
        KeyedSocialLedger ledger = get(level.getServer());
        String pair = villager.getUUID() + "|" + player.getUUID() + "|" + key;
        int allowed = amount;
        if (dailyCap > 0) {
            int left = dailyCap - ledger.usedToday(level.getServer(), "hearts|" + pair);
            if (left <= 0) return 0;
            allowed = Integer.signum(amount) * Math.min(Math.abs(amount), left);
            ledger.use(level.getServer(), "hearts|" + pair, Math.abs(allowed));
        }
        villager.getVillagerBrain().getMemoriesForPlayer(player).modHearts(allowed);
        com.aetherianartificer.townstead.reaction.trigger.event.SocialInteractionTracker.markHeartChange(villager, allowed, level.getGameTime());
        if (allowed < 0) {
            ledger.taken.merge(pair, -allowed, Integer::sum);
            ledger.setDirty();
        }
        return allowed;
    }

    /** Gives a player back the hearts a key took from them with this villager. Returns how many. */
    public static int returnHearts(LivingEntity entity, LivingEntity other, String key) {
        if (!(entity instanceof VillagerEntityMCA villager) || !(other instanceof Player player)
                || !(villager.level() instanceof ServerLevel level)) return 0;
        KeyedSocialLedger ledger = get(level.getServer());
        Integer back = ledger.taken.remove(villager.getUUID() + "|" + player.getUUID() + "|" + key);
        if (back == null || back <= 0) return 0;
        ledger.setDirty();
        villager.getVillagerBrain().getMemoriesForPlayer(player).modHearts(back);
        com.aetherianartificer.townstead.reaction.trigger.event.SocialInteractionTracker.markHeartChange(villager, back, level.getGameTime());
        return back;
    }

    // ---- Daily limits.

    /** Whether {@code from} may do {@code key} toward {@code toward} again today, at most {@code limit} times; counts it if so. */
    public static boolean allowToday(MinecraftServer server, UUID from, UUID toward, String key, int limit) {
        if (limit <= 0) return true;
        KeyedSocialLedger ledger = get(server);
        String slot = "times|" + from + "|" + toward + "|" + key;
        if (ledger.usedToday(server, slot) >= limit) return false;
        ledger.use(server, slot, 1);
        return true;
    }

    private int usedToday(MinecraftServer server, String slot) {
        Daily row = daily.get(slot);
        return row != null && row.day() == TownsteadCalendar.worldDay(server) ? row.used() : 0;
    }

    private void use(MinecraftServer server, String slot, int amount) {
        long today = TownsteadCalendar.worldDay(server);
        int used = usedToday(server, slot) + amount;
        // Rows from other days are spent.
        daily.values().removeIf(row -> row.day() != today);
        daily.put(slot, new Daily(today, used));
        setDirty();
    }

    // ---- Save.

    //? if >=1.21 {
    public static KeyedSocialLedger load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static KeyedSocialLedger load(CompoundTag tag) {
    *///?}
        KeyedSocialLedger data = new KeyedSocialLedger();
        for (Tag raw : tag.getList("moods", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            try {
                data.moods.computeIfAbsent(e.getUUID("villager"), u -> new LinkedHashMap<>())
                        .put(e.getString("key"), new Mood(e.getInt("amount"), e.getLong("until")));
            } catch (RuntimeException ignored) {
                // A malformed row is dropped, not the whole ledger.
            }
        }
        for (Tag raw : tag.getList("applied", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            try {
                data.applied.put(e.getUUID("villager"), e.getInt("amount"));
            } catch (RuntimeException ignored) {
                // As above.
            }
        }
        CompoundTag days = tag.getCompound("daily");
        for (String slot : days.getAllKeys()) {
            CompoundTag e = days.getCompound(slot);
            data.daily.put(slot, new Daily(e.getLong("day"), e.getInt("used")));
        }
        CompoundTag took = tag.getCompound("taken");
        for (String pair : took.getAllKeys()) data.taken.put(pair, took.getInt(pair));
        return data;
    }

    //? if >=1.21 {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
    *///?}
        ListTag moodList = new ListTag();
        moods.forEach((villager, held) -> held.forEach((key, mood) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("villager", villager);
            e.putString("key", key);
            e.putInt("amount", mood.amount());
            e.putLong("until", mood.until());
            moodList.add(e);
        }));
        tag.put("moods", moodList);
        ListTag appliedList = new ListTag();
        applied.forEach((villager, amount) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("villager", villager);
            e.putInt("amount", amount);
            appliedList.add(e);
        });
        tag.put("applied", appliedList);
        CompoundTag days = new CompoundTag();
        daily.forEach((slot, row) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("day", row.day());
            e.putInt("used", row.used());
            days.put(slot, e);
        });
        tag.put("daily", days);
        CompoundTag took = new CompoundTag();
        taken.forEach(took::putInt);
        tag.put("taken", took);
        return tag;
    }
}
