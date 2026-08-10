package com.aetherianartificer.townstead.work.job;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One data-authored villager job. Unlike a workstation, a job may reserve several independent
 * things in the world and move between them. Role names are author-owned labels; executors select
 * roles by {@link RoleKind}, so neither {@code hook} nor any other mod-specific noun becomes part
 * of Townstead's Java vocabulary.
 */
public record WorkJobDef(
        ResourceLocation id,
        ResourceLocation task,
        ResourceLocation executor,
        Map<String, Role> roles) {

    public static final String SCHEMA = "townstead:job/v1";
    public static final ResourceLocation ENTITY_DELIVERY = id("townstead:entity_delivery");

    public enum RoleKind { ENTITY, BLOCK }

    public record Placement(
            int x,
            int y,
            int z,
            Map<String, String> properties,
            List<String> copyProperties) {

        public static final Placement DEFAULT = new Placement(0, 0, 0, Map.of(), List.of());
    }

    public record Role(
            RoleKind kind,
            List<String> buildings,
            Map<ResourceLocation, ResourceLocation> results,
            Set<ResourceLocation> blocks,
            Placement placement) {

        public boolean matchesBuilding(@Nullable String buildingType) {
            if (buildingType == null || buildings.isEmpty()) return false;
            for (String pattern : buildings) {
                if (pattern.endsWith("*")) {
                    if (buildingType.startsWith(pattern.substring(0, pattern.length() - 1))) return true;
                } else if (pattern.equals(buildingType)) {
                    return true;
                }
            }
            return false;
        }
    }

    static @Nullable WorkJobDef parse(ResourceLocation definitionId, JsonObject json) {
        ResourceLocation task = resource(json, "task");
        ResourceLocation executor = resource(json, "executor");
        if (task == null || executor == null || !json.has("roles") || !json.get("roles").isJsonObject()) {
            return null;
        }

        Map<String, Role> roles = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("roles").entrySet()) {
            if (!entry.getValue().isJsonObject()) return null;
            Role role = parseRole(entry.getValue().getAsJsonObject());
            if (role == null) return null;
            roles.put(entry.getKey(), role);
        }
        if (roles.isEmpty()) return null;

        WorkJobDef def = new WorkJobDef(definitionId, task, executor, Map.copyOf(roles));
        if (ENTITY_DELIVERY.equals(executor)
                && (def.first(RoleKind.ENTITY) == null || def.first(RoleKind.BLOCK) == null)) {
            return null;
        }
        return def;
    }

    public @Nullable Role first(RoleKind kind) {
        for (Role role : roles.values()) if (role.kind() == kind) return role;
        return null;
    }

    public @Nullable ResourceLocation resultFor(ResourceLocation entityType) {
        Role source = first(RoleKind.ENTITY);
        return source == null ? null : source.results().get(entityType);
    }

    public boolean producesBlock(ResourceLocation block) {
        Role source = first(RoleKind.ENTITY);
        return source != null && source.results().containsValue(block);
    }

    private static @Nullable Role parseRole(JsonObject json) {
        String rawKind = string(json, "kind");
        RoleKind kind;
        if ("entity".equals(rawKind)) kind = RoleKind.ENTITY;
        else if ("block".equals(rawKind)) kind = RoleKind.BLOCK;
        else return null;

        List<String> buildings = strings(json.get("buildings"));
        if (buildings == null) return null;

        Map<ResourceLocation, ResourceLocation> results = new LinkedHashMap<>();
        if (json.has("results")) {
            if (!json.get("results").isJsonObject()) return null;
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("results").entrySet()) {
                ResourceLocation from = id(entry.getKey());
                ResourceLocation to = entry.getValue().isJsonPrimitive()
                        ? id(entry.getValue().getAsString()) : null;
                if (from == null || to == null) return null;
                results.put(from, to);
            }
        }

        Set<ResourceLocation> blocks = new LinkedHashSet<>();
        if (json.has("blocks")) {
            List<String> rawBlocks = strings(json.get("blocks"));
            if (rawBlocks == null) return null;
            for (String raw : rawBlocks) {
                ResourceLocation block = id(raw);
                if (block == null || raw.startsWith("#")) return null;
                blocks.add(block);
            }
        }

        Placement placement = json.has("placement")
                ? parsePlacement(json.get("placement")) : Placement.DEFAULT;
        if (placement == null) return null;
        if (kind == RoleKind.ENTITY && results.isEmpty()) return null;
        if (kind == RoleKind.BLOCK && blocks.isEmpty()) return null;
        return new Role(kind, List.copyOf(buildings), Map.copyOf(results), Set.copyOf(blocks), placement);
    }

    private static @Nullable Placement parsePlacement(JsonElement element) {
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        int x = 0, y = 0, z = 0;
        if (json.has("offset")) {
            JsonElement offset = json.get("offset");
            if (!offset.isJsonArray() || offset.getAsJsonArray().size() != 3) return null;
            JsonArray values = offset.getAsJsonArray();
            for (JsonElement value : values) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
            }
            x = values.get(0).getAsInt();
            y = values.get(1).getAsInt();
            z = values.get(2).getAsInt();
        }
        Map<String, String> properties = new LinkedHashMap<>();
        if (json.has("properties")) {
            if (!json.get("properties").isJsonObject()) return null;
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("properties").entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) return null;
                properties.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        List<String> copied = strings(json.get("copy_properties"));
        if (copied == null) return null;
        return new Placement(x, y, z, Map.copyOf(properties), List.copyOf(copied));
    }

    private static @Nullable List<String> strings(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        if (!element.isJsonArray()) return null;
        List<String> out = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonPrimitive() || !child.getAsJsonPrimitive().isString()) return null;
            out.add(child.getAsString());
        }
        return out;
    }

    private static @Nullable ResourceLocation resource(JsonObject json, String key) {
        return id(string(json, key));
    }

    private static @Nullable String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private static @Nullable ResourceLocation id(@Nullable String raw) {
        return raw == null ? null : ResourceLocation.tryParse(raw);
    }
}
