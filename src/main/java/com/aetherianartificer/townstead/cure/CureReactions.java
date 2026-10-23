package com.aetherianartificer.townstead.cure;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps;
import com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How someone feels about being cured of a state they had (vampirism, say): grateful or resentful,
 * by how far into it they were and their personality. The outcome goes into their chronicle, and
 * they tell whoever cured them, now or the next time that person comes near.
 */
public final class CureReactions extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/CureReactions");
    /** Who began the cure, saved on the one being cured by {@code pheno:remember} or a cure item. */
    public static final String CURED_BY = "townstead:cured_by";
    private static final String PENDING = "townstead:cure_reaction";
    private static final double RANGE = 6.0;

    private record Chance(double minRank, double chance) {}

    private record Reaction(ResourceLocation state, List<Chance> resentful, Map<String, Double> personality,
                            int gratefulHearts, int resentfulHearts, List<String> gratefulLines,
                            List<String> resentfulLines, String gratefulChronicle, String resentfulChronicle) {}

    private static volatile Map<ResourceLocation, Reaction> byState = Map.of();

    public CureReactions() {
        super(new Gson(), "cure_reaction");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        entries = com.aetherianartificer.townstead.data.DataDocuments.resolve("cure_reaction", entries, manager);
        Map<ResourceLocation, Reaction> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            try {
                Reaction reaction = parse(GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString()));
                loaded.put(reaction.state(), reaction);
            } catch (Exception exception) {
                LOGGER.warn("Could not load cure reaction {}: {}", entry.getKey(), exception.getMessage());
            }
        }
        byState = Map.copyOf(loaded);
    }

    private static Reaction parse(JsonObject json) {
        ResourceLocation state = ResourceLocation.tryParse(GsonHelper.getAsString(json, "state"));
        if (state == null) throw new IllegalArgumentException("'state' is not an id");
        List<Chance> chances = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "resentful_chance")) {
            JsonObject row = element.getAsJsonObject();
            chances.add(new Chance(GsonHelper.getAsDouble(row, "min_rank", 0), GsonHelper.getAsDouble(row, "chance")));
        }
        chances.sort((a, b) -> Double.compare(a.minRank(), b.minRank()));
        Map<String, Double> personality = new HashMap<>();
        if (json.has("personality")) {
            for (Map.Entry<String, JsonElement> shift : GsonHelper.getAsJsonObject(json, "personality").entrySet()) {
                personality.put(shift.getKey(), shift.getValue().getAsDouble());
            }
        }
        JsonObject hearts = GsonHelper.getAsJsonObject(json, "hearts", new JsonObject());
        JsonObject lines = GsonHelper.getAsJsonObject(json, "lines");
        JsonObject chronicle = GsonHelper.getAsJsonObject(json, "chronicle");
        return new Reaction(state, List.copyOf(chances), Map.copyOf(personality),
                GsonHelper.getAsInt(hearts, "grateful", 0), GsonHelper.getAsInt(hearts, "resentful", 0),
                strings(GsonHelper.getAsJsonArray(lines, "grateful")), strings(GsonHelper.getAsJsonArray(lines, "resentful")),
                GsonHelper.getAsString(chronicle, "grateful"), GsonHelper.getAsString(chronicle, "resentful"));
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>();
        for (JsonElement element : array) out.add(element.getAsString());
        return List.copyOf(out);
    }

    /** Remembers who began the cure, for the reaction when it is done. */
    public static void curedBy(VillagerEntityMCA villager, Player curer) {
        villager.getPersistentData().putUUID(CURED_BY, curer.getUUID());
    }

    /**
     * {@code villager} was just cured of {@code state}, at {@code formerRank}. Rolls how they feel,
     * chronicles it, and keeps the words for whoever cured them. False when no reaction covers the state.
     */
    public static boolean cured(VillagerEntityMCA villager, ResourceLocation state, double formerRank) {
        Reaction reaction = byState.get(state);
        if (reaction == null) return false;
        double chance = 0;
        for (Chance row : reaction.resentful()) {
            if (formerRank >= row.minRank()) chance = row.chance();
        }
        String personality = McaPersonalityCompat.id(villager.getVillagerBrain().getPersonality());
        chance += reaction.personality().getOrDefault(personality, 0.0);
        boolean resentful = villager.getRandom().nextDouble() < Math.max(0, Math.min(1, chance));
        ChronicleTaps.survival(villager, resentful ? reaction.resentfulChronicle() : reaction.gratefulChronicle(), Map.of());

        CompoundTag data = villager.getPersistentData();
        if (data.hasUUID(CURED_BY)) {
            CompoundTag pending = new CompoundTag();
            pending.putUUID("curer", data.getUUID(CURED_BY));
            pending.putString("state", state.toString());
            pending.putBoolean("resentful", resentful);
            data.put(PENDING, pending);
            data.remove(CURED_BY);
        }
        return true;
    }

    /** Says the reaction to the curer once they are near and in sight. */
    public static void tick(VillagerEntityMCA villager) {
        if (villager.tickCount % 20 != 0) return;
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(PENDING)) return;
        CompoundTag pending = data.getCompound(PENDING);
        Reaction reaction = byState.get(ResourceLocation.tryParse(pending.getString("state")));
        if (reaction == null || !pending.hasUUID("curer")) {
            data.remove(PENDING);
            return;
        }
        Player curer = villager.level().getPlayerByUUID(pending.getUUID("curer"));
        if (curer == null || curer.distanceToSqr(villager) > RANGE * RANGE || !villager.hasLineOfSight(curer)) return;
        boolean resentful = pending.getBoolean("resentful");
        List<String> lines = resentful ? reaction.resentfulLines() : reaction.gratefulLines();
        if (!lines.isEmpty()) {
            villager.sendChatMessage(Component.translatable(lines.get(villager.getRandom().nextInt(lines.size()))), curer);
        }
        villager.getVillagerBrain().getMemoriesForPlayer(curer)
                .modHearts(resentful ? reaction.resentfulHearts() : reaction.gratefulHearts());
        data.remove(PENDING);
    }
}
