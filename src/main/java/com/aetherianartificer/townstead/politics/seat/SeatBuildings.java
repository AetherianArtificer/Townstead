package com.aetherianartificer.townstead.politics.seat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a building type provides as a Seat, from the {@code seat} block of its
 * {@code extended_buildings} entry. A building type without one gives a Meeting Place.
 */
public final class SeatBuildings {
    public static final ResourceLocation RECORDS = id("records");
    public static final ResourceLocation AUDIENCE = id("audience");
    public static final Spec MEETING_PLACE = new Spec(1, List.of(RECORDS, AUDIENCE), Set.of("polity", "organization"), Map.of());

    public record Spec(int tier, List<ResourceLocation> functions, Set<String> actors,
                       Map<ResourceLocation, Integer> affinity) {
        public Spec {
            functions = List.copyOf(functions);
            actors = Set.copyOf(actors);
            affinity = Map.copyOf(affinity);
        }
    }

    private static volatile Map<String, Spec> BY_TYPE = Map.of();

    private SeatBuildings() {}

    public static void replaceAll(Map<String, Spec> next) {
        BY_TYPE = Map.copyOf(next);
    }

    public static Map<String, Spec> snapshot() {
        return BY_TYPE;
    }

    public static Spec forType(String buildingType) {
        return buildingType == null ? MEETING_PLACE : BY_TYPE.getOrDefault(buildingType, MEETING_PLACE);
    }

    public static boolean isSeatBuilding(String buildingType) {
        return buildingType != null && BY_TYPE.containsKey(buildingType);
    }

    public static Spec parse(JsonObject json) {
        int tier = Math.max(1, Math.min(4, GsonHelper.getAsInt(json, "tier", 1)));
        List<ResourceLocation> functions = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "functions", new com.google.gson.JsonArray())) {
            ResourceLocation function = ResourceLocation.tryParse(element.getAsString());
            if (function == null) throw new IllegalArgumentException("invalid seat function '" + element + "'");
            if (!functions.contains(function)) functions.add(function);
        }
        if (functions.isEmpty()) functions.addAll(MEETING_PLACE.functions());
        Set<String> actors = new LinkedHashSet<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "actors", new com.google.gson.JsonArray())) {
            actors.add(element.getAsString());
        }
        if (actors.isEmpty()) actors.add("polity");
        Map<ResourceLocation, Integer> affinity = new LinkedHashMap<>();
        if (json.has("affinity")) {
            for (var entry : GsonHelper.getAsJsonObject(json, "affinity").entrySet()) {
                ResourceLocation form = ResourceLocation.tryParse(entry.getKey());
                if (form != null) affinity.put(form, entry.getValue().getAsInt());
            }
        }
        return new Spec(tier, functions, actors, affinity);
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("townstead", path);
        //?} else {
        /*return new ResourceLocation("townstead", path);
        *///?}
    }
}
