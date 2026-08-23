package com.aetherianartificer.townstead.work.job;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One data-authored villager job. The job {@code type} owns its semantic fields: a block
 * interaction has a {@code target}, while entity delivery has a {@code source} and
 * {@code destination}. Content nouns remain resource values rather than structural keys.
 */
public record WorkJobDef(
        ResourceLocation id,
        ResourceLocation task,
        ResourceLocation type,
        @Nullable EntitySource source,
        @Nullable BlockTarget destination,
        @Nullable BlockTarget target) {

    public static final String SCHEMA = "townstead:job/v1";
    public static final ResourceLocation ENTITY_DELIVERY = id("townstead:entity_delivery");
    public static final ResourceLocation BLOCK_INTERACTION = id("townstead:block_interaction");

    /**
     * The Chronicle counter for this Job. The resource identity is already unique and stable, so
     * authors never repeat it as a separate history key.
     */
    public String activityKey() {
        return id.toString();
    }

    public record Placement(
            int x,
            int y,
            int z,
            Map<String, String> properties,
            List<String> copyProperties) {

        public static final Placement DEFAULT = new Placement(0, 0, 0, Map.of(), List.of());
    }

    public record EntitySource(
            List<String> buildings,
            Map<ResourceLocation, ResourceLocation> results) {

        public boolean matchesBuilding(@Nullable String buildingType) {
            return matchesBuildingPattern(buildings, buildingType);
        }
    }

    public record BlockTarget(
            List<String> buildings,
            Set<ResourceLocation> blocks,
            Placement placement,
            @Nullable BlockCondition condition,
            List<Interaction> interactions) {

        public boolean matchesBuilding(@Nullable String buildingType) {
            return matchesBuildingPattern(buildings, buildingType);
        }

        public boolean ready(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
            return condition == null || condition.test(level, pos);
        }
    }

    /** One real block-use route offered by a generic block-interaction job. */
    public record Interaction(String item, BlockAction action, Set<ResourceLocation> outputs, int xp) {

        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            ResourceLocation id = ResourceLocation.tryParse(
                    item.startsWith("#") ? item.substring(1) : item);
            if (id == null) return false;
            return item.startsWith("#")
                    ? stack.is(TagKey.create(Registries.ITEM, id))
                    : stack.is(BuiltInRegistries.ITEM.get(id));
        }

        public Set<ResourceLocation> outputIds() {
            return outputs;
        }
    }

    static @Nullable WorkJobDef parse(ResourceLocation definitionId, JsonObject json) {
        ResourceLocation task = resource(json, "task");
        ResourceLocation type = resource(json, "type");
        if (task == null || type == null) return null;

        if (BLOCK_INTERACTION.equals(type)) {
            BlockTarget target = parseBlockTarget(json.get("target"), true);
            return target == null ? null
                    : new WorkJobDef(definitionId, task, type, null, null, target);
        }
        if (ENTITY_DELIVERY.equals(type)) {
            EntitySource source = parseEntitySource(json.get("source"));
            BlockTarget destination = parseBlockTarget(json.get("destination"), false);
            return source == null || destination == null ? null
                    : new WorkJobDef(definitionId, task, type, source, destination, null);
        }
        return null;
    }

    public @Nullable ResourceLocation resultFor(ResourceLocation entityType) {
        return source == null ? null : source.results().get(entityType);
    }

    public boolean producesBlock(ResourceLocation block) {
        return source != null && source.results().containsValue(block);
    }

    private static @Nullable EntitySource parseEntitySource(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        List<String> buildings = strings(json.get("buildings"));
        if (buildings == null || !json.has("results") || !json.get("results").isJsonObject()) {
            return null;
        }
        Map<ResourceLocation, ResourceLocation> results = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("results").entrySet()) {
            ResourceLocation from = id(entry.getKey());
            ResourceLocation to = entry.getValue().isJsonPrimitive()
                    ? id(entry.getValue().getAsString()) : null;
            if (from == null || to == null) return null;
            results.put(from, to);
        }
        return results.isEmpty() ? null
                : new EntitySource(List.copyOf(buildings), Map.copyOf(results));
    }

    private static @Nullable BlockTarget parseBlockTarget(
            @Nullable JsonElement element, boolean interactionsRequired) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        List<String> buildings = strings(json.get("buildings"));
        if (buildings == null) return null;

        Set<ResourceLocation> blocks = new LinkedHashSet<>();
        if (json.has("block")) {
            ResourceLocation block = resource(json, "block");
            if (block == null) return null;
            blocks.add(block);
        }
        if (json.has("blocks")) {
            List<String> rawBlocks = strings(json.get("blocks"));
            if (rawBlocks == null) return null;
            for (String raw : rawBlocks) {
                ResourceLocation block = id(raw);
                if (block == null || raw.startsWith("#")) return null;
                blocks.add(block);
            }
        }
        if (blocks.isEmpty()) return null;

        Placement placement = json.has("placement")
                ? parsePlacement(json.get("placement")) : Placement.DEFAULT;
        if (placement == null) return null;
        BlockCondition condition = json.has("condition")
                ? BlockConditions.parse(json.get("condition")) : null;
        if (json.has("condition") && condition == null) return null;
        int xp = integer(json, "xp", 1);
        if (xp < 1) return null;
        List<Interaction> interactions = parseInteractions(json.get("interactions"), xp);
        if (interactions == null || (interactionsRequired && interactions.isEmpty())) return null;
        return new BlockTarget(List.copyOf(buildings), Set.copyOf(blocks), placement,
                condition, List.copyOf(interactions));
    }

    private static @Nullable List<Interaction> parseInteractions(
            @Nullable JsonElement element, int defaultXp) {
        if (element == null || element.isJsonNull()) return List.of();
        if (!element.isJsonArray()) return null;
        List<Interaction> interactions = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonObject()) return null;
            JsonObject json = child.getAsJsonObject();
            String item = string(json, "item");
            ResourceLocation selector = item == null ? null : id(
                    item.startsWith("#") ? item.substring(1) : item);
            if (selector == null) return null;

            JsonElement actionJson;
            if (json.has("action")) {
                actionJson = json.get("action");
            } else {
                JsonObject useBlock = new JsonObject();
                useBlock.addProperty("type", "pheno:use_block");
                useBlock.addProperty("item", "item");
                actionJson = useBlock;
            }
            BlockAction action = BlockActions.parse(actionJson);
            if (action == null) return null;

            Set<ResourceLocation> outputs = new LinkedHashSet<>();
            if (json.has("output")) {
                ResourceLocation output = resource(json, "output");
                if (output == null) return null;
                outputs.add(output);
            }
            if (json.has("outputs")) {
                List<String> rawOutputs = strings(json.get("outputs"));
                if (rawOutputs == null) return null;
                for (String raw : rawOutputs) {
                    ResourceLocation output = id(raw);
                    if (output == null || raw.startsWith("#")) return null;
                    outputs.add(output);
                }
            }
            int xp = integer(json, "xp", defaultXp);
            if (outputs.isEmpty() || xp < 1) return null;
            interactions.add(new Interaction(item, action, Set.copyOf(outputs), xp));
        }
        return List.copyOf(interactions);
    }

    private static @Nullable Placement parsePlacement(JsonElement element) {
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        int x = 0;
        int y = 0;
        int z = 0;
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

    private static boolean matchesBuildingPattern(List<String> buildings,
                                                  @Nullable String buildingType) {
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

    private static int integer(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive()
                    ? json.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return Integer.MIN_VALUE;
        }
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
