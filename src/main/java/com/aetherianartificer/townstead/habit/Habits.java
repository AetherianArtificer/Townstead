package com.aetherianartificer.townstead.habit;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How often each personality keeps up a habit, such as cleaning: {@code habit/v1} data, one file a leaning (fussy, steady,
 * easygoing...) naming its personalities and the game hours between rounds of each habit it names; 0 hours is never. Any habit name works, so
 * any job can be gated by one ({@code pheno:habit_due}) and count toward it ({@code pheno:habit_done}).
 *
 * <p>A villager is due once its hours have passed since their last round began, and stays due until the round is done:
 * enough goes at it, or the next round comes due. A place with nothing to do simply gives the round no work. Rounds are kept
 * in memory only: after a restart everyone is due once.</p>
 */
public final class Habits {

    public static final String SCHEMA = "townstead:habit/v1";
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/Habits");
    // A game hour, and how many goes make a round, such as strokes of a broom.
    private static final long HOUR = 1000L;
    private static final int A_ROUND = 32;
    // For a habit no leaning names hours for, and for anyone no leaning names.
    private static final float DEFAULT_HOURS = 24f;
    private static final Leaning FALLBACK = new Leaning(Set.of(), Map.of());

    /** A leaning: its personalities, and the game hours between rounds of each habit. */
    public record Leaning(Set<String> personalities, Map<String, Float> hours) {
        float hoursOf(String habit) {
            return hours.getOrDefault(habit, DEFAULT_HOURS);
        }

        /** A habit given 0 hours is one this leaning never keeps. */
        boolean never(String habit) {
            return hoursOf(habit) <= 0f;
        }
    }

    /** A round of a habit: when it began, and how many goes it has had. */
    private record Round(long began, int goes) {}

    private static volatile List<Leaning> LEANINGS = List.of();
    private static final Map<UUID, Map<String, Round>> ROUNDS = new ConcurrentHashMap<>();

    private Habits() {}

    /** The leaning this villager's personality falls under, by its exact data-pack personality first, then MCA's. */
    public static Leaning of(VillagerEntityMCA villager) {
        String exact = normalize(com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).life().personalityId());
        String base = normalize(com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat.id(villager.getVillagerBrain().getPersonality()));
        for (Leaning leaning : LEANINGS) if (!exact.isEmpty() && leaning.personalities().contains(exact)) return leaning;
        for (Leaning leaning : LEANINGS) if (leaning.personalities().contains(base)) return leaning;
        return FALLBACK;
    }

    /** Whether it's time for this villager to {@code habit}, or they're in the middle of a round of it. */
    public static boolean due(VillagerEntityMCA villager, String habit, long gameTime) {
        if (of(villager).never(habit)) return false;
        Round round = ROUNDS.getOrDefault(villager.getUUID(), Map.of()).get(habit);
        return round == null || round.goes() < A_ROUND || nextRound(villager, habit, round, gameTime);
    }

    /** This villager just had a go at {@code habit}: the round it's in counts it, or a new round begins. */
    public static void did(VillagerEntityMCA villager, String habit, long gameTime) {
        Map<String, Round> rounds = ROUNDS.computeIfAbsent(villager.getUUID(), id -> new ConcurrentHashMap<>());
        Round round = rounds.get(habit);
        rounds.put(habit, round == null || nextRound(villager, habit, round, gameTime) ? new Round(gameTime, 1) : new Round(round.began(), round.goes() + 1));
    }

    /** This villager did all of {@code habit} there was to do, such as a whole bath: the round is over. */
    public static void finished(VillagerEntityMCA villager, String habit, long gameTime) {
        Map<String, Round> rounds = ROUNDS.computeIfAbsent(villager.getUUID(), id -> new ConcurrentHashMap<>());
        Round round = rounds.get(habit);
        long began = round == null || nextRound(villager, habit, round, gameTime) ? gameTime : round.began();
        rounds.put(habit, new Round(began, A_ROUND));
    }

    private static boolean nextRound(VillagerEntityMCA villager, String habit, Round round, long gameTime) {
        return gameTime < round.began() || gameTime - round.began() >= (long) (of(villager).hoursOf(habit) * HOUR);
    }

    static boolean validName(String habit) {
        return habit != null && habit.matches("[a-z0-9_.-]+");
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') < 0 ? "mca:" + normalized : normalized;
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : manager
                    .listResources("habit", id -> id.getPath().endsWith(".json")).entrySet()) {
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(entry.getKey(), parsed.getAsJsonObject());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read habit leaning {}: {}", entry.getKey(), ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager manager, ProfilerFiller profiler) {
            List<Leaning> loaded = new ArrayList<>();
            prepared.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString))).forEach(entry -> {
                JsonObject json = entry.getValue();
                try {
                    TownsteadSchema.validateRequired(json, SCHEMA);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Habit leaning {} rejected: {}", entry.getKey(), ex.getMessage());
                    return;
                }
                Set<String> personalities = new LinkedHashSet<>();
                for (JsonElement id : GsonHelper.getAsJsonArray(json, "personalities", new com.google.gson.JsonArray())) {
                    if (id.isJsonPrimitive()) personalities.add(normalize(id.getAsString()));
                }
                Map<String, Float> hours = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> habit : GsonHelper.getAsJsonObject(json, "hours", new JsonObject()).entrySet()) {
                    if (!validName(habit.getKey()) || !habit.getValue().isJsonPrimitive()) {
                        LOGGER.warn("Habit leaning {}: bad habit {}", entry.getKey(), habit.getKey());
                        continue;
                    }
                    float value = habit.getValue().getAsFloat();
                    hours.put(habit.getKey(), value <= 0f ? 0f : Math.max(0.5f, value));
                }
                loaded.add(new Leaning(Set.copyOf(personalities), Map.copyOf(hours)));
            });
            LEANINGS = List.copyOf(loaded);
        }
    }
}
