package com.aetherianartificer.townstead.work.job;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.data.ConfigGate;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.pheno.condition.item.ItemCondition;
import com.aetherianartificer.townstead.pheno.condition.item.ItemConditions;
import com.aetherianartificer.townstead.pheno.selector.BlockSelector;
import com.aetherianartificer.townstead.pheno.selector.BlockSelectors;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
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

    public static final String SCHEMA = "townstead:job/v3";
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
            List<String> copyProperties,
            @Nullable BlockAction action) {

        public static final Placement DEFAULT = new Placement(0, 0, 0, Map.of(), List.of(), null);
    }

    public record EntitySource(
            List<String> buildings,
            Map<ResourceLocation, ResourceLocation> results,
            @Nullable String item,
            @Nullable Condition condition,
            Action action,
            double range,
            int interval,
            TunableInt cooldown,
            int xp) {

        public boolean matchesBuilding(@Nullable String buildingType) {
            return matchesBuildingPattern(buildings, buildingType);
        }

        public boolean matches(ItemStack stack) {
            return item != null && matchesItem(item, stack);
        }

        /** Whether this entity is one of the declared inputs and satisfies the authored policy. */
        public boolean matches(LivingEntity entity) {
            if (entity == null || !results.containsKey(
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) return false;
            return condition == null || condition.test(new ConditionContext(entity));
        }

        public int cooldown(ServerLevel level) {
            return cooldown.resolve(level);
        }
    }

    /** An integer that may be literal or read from a TOML value with a data-authored fallback. */
    public record TunableInt(int fallback, @Nullable JsonObject config) {
        public int resolve(ServerLevel level) {
            double value = config == null ? fallback : ConfigGate.number(config, level, fallback);
            return Math.max(0, (int) Math.round(value));
        }
    }

    public record BlockTarget(
            List<String> buildings,
            Set<ResourceLocation> blocks,
            List<ResourceLocation> blockTags,
            Placement placement,
            @Nullable BlockCondition condition,
            List<ManagedRequirement> requirements,
            List<Interaction> interactions) {

        public boolean matchesBuilding(@Nullable String buildingType) {
            return matchesBuildingPattern(buildings, buildingType);
        }

        public boolean ready(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
            return condition == null || condition.test(level, pos);
        }

        public boolean matches(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (blocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) return true;
            for (ResourceLocation tag : blockTags) {
                if (state.is(TagKey.create(Registries.BLOCK, tag))) return true;
            }
            return false;
        }
    }

    /** A world fact which may already hold or may be established temporarily for one work session. */
    public record ManagedRequirement(String id, BlockCondition satisfiedWhen,
                                     @Nullable Provision provision) {
        public boolean satisfied(net.minecraft.world.level.Level level,
                                 net.minecraft.core.BlockPos target) {
            return satisfiedWhen.test(level, target);
        }
    }

    /** How a worker establishes a requirement and reverses only Townstead-managed preparation. */
    public record Provision(BlockSelector at, @Nullable String item,
                            @Nullable ItemCondition itemCondition, BlockAction start,
                            @Nullable BlockCondition managedWhen, BlockAction stop) {
        public boolean requiresItem() { return item != null; }

        public boolean matches(net.minecraft.server.level.ServerLevel level,
                               net.minecraft.core.BlockPos pos, ItemStack stack) {
            return item != null && matchesItem(item, stack)
                    && (itemCondition == null || itemCondition.test(level, stack))
                    && start.canRun(new com.aetherianartificer.townstead.pheno.action.block.BlockActionContext(
                    level, pos).withItemRole("item", stack));
        }

        public boolean sourceManaged(net.minecraft.world.level.Level level,
                                     net.minecraft.core.BlockPos pos) {
            return managedWhen == null || managedWhen.test(level, pos);
        }
    }

    /** One real block-use route offered by a generic block-interaction job. */
    public record Interaction(@Nullable String item, @Nullable ItemCondition itemCondition,
                              @Nullable BlockCondition condition,
                              BlockAction action, Set<ResourceLocation> outputs,
                              int expectedCount, int xp) {

        public boolean matches(ItemStack stack) {
            return item != null && matchesItem(item, stack);
        }

        public boolean requiresItem() {
            return item != null;
        }

        public Set<ResourceLocation> outputIds() {
            return outputs;
        }

        public boolean ready(net.minecraft.world.level.Level level,
                             net.minecraft.core.BlockPos pos) {
            if (condition != null && !condition.test(level, pos)) return false;
            return action.canRun(new com.aetherianartificer.townstead.pheno.action.block.BlockActionContext(
                    (net.minecraft.server.level.ServerLevel) level, pos));
        }

        public boolean matches(net.minecraft.server.level.ServerLevel level,
                               net.minecraft.core.BlockPos pos, ItemStack stack) {
            if (item == null) return itemCondition == null && ready(level, pos);
            if (!matches(stack) || (itemCondition != null && !itemCondition.test(level, stack))
                    || (condition != null && !condition.test(level, pos))) return false;
            return action.canRun(new com.aetherianartificer.townstead.pheno.action.block.BlockActionContext(
                    level, pos).withItemRole("item", stack));
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
        String item = string(json, "item");
        if (json.has("item") && !validSelector(item)) return null;
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        if (json.has("condition") && condition == null) return null;
        Action action = json.has("action") ? Actions.parse(json.get("action")) : null;
        if (action == null) return null;
        double range = number(json, "range", 2.0);
        int interval = integer(json, "interval", 20);
        TunableInt cooldown = tunableInt(json.get("cooldown"), 0);
        int xp = integer(json, "xp", 1);
        if (range <= 0.0 || interval < 1 || cooldown == null || xp < 1) return null;
        return results.isEmpty() ? null
                : new EntitySource(List.copyOf(buildings), Map.copyOf(results), item, condition,
                action, range, interval, cooldown, xp);
    }

    private static @Nullable BlockTarget parseBlockTarget(
            @Nullable JsonElement element, boolean interactionsRequired) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        List<String> buildings = strings(json.get("buildings"));
        if (buildings == null) return null;

        Set<ResourceLocation> blocks = new LinkedHashSet<>();
        List<ResourceLocation> blockTags = new ArrayList<>();
        if (json.has("block")) {
            String raw = string(json, "block");
            boolean tag = raw != null && raw.startsWith("#");
            ResourceLocation block = id(tag ? raw.substring(1) : raw);
            if (block == null) return null;
            if (tag) blockTags.add(block);
            else blocks.add(block);
        }
        if (json.has("blocks")) {
            List<String> rawBlocks = strings(json.get("blocks"));
            if (rawBlocks == null) return null;
            for (String raw : rawBlocks) {
                boolean tag = raw.startsWith("#");
                ResourceLocation block = id(tag ? raw.substring(1) : raw);
                if (block == null) return null;
                if (tag) blockTags.add(block);
                else blocks.add(block);
            }
        }
        if (blocks.isEmpty() && blockTags.isEmpty()) return null;

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
        List<ManagedRequirement> requirements = parseRequirements(json.get("requirements"));
        if (requirements == null) return null;
        return new BlockTarget(List.copyOf(buildings), Set.copyOf(blocks), List.copyOf(blockTags), placement,
                condition, List.copyOf(requirements), List.copyOf(interactions));
    }

    private static @Nullable List<ManagedRequirement> parseRequirements(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        if (!element.isJsonArray()) return null;
        List<ManagedRequirement> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonObject()) return null;
            JsonObject json = child.getAsJsonObject();
            String requirementId = string(json, "id");
            if (requirementId == null || !requirementId.matches("[a-z0-9_.-]+")
                    || !ids.add(requirementId) || !json.has("satisfied_when")) return null;
            BlockCondition satisfied = BlockConditions.parse(json.get("satisfied_when"));
            if (satisfied == null) return null;
            Provision provision = json.has("provision") ? parseProvision(json.get("provision")) : null;
            if (json.has("provision") && provision == null) return null;
            result.add(new ManagedRequirement(requirementId, satisfied, provision));
        }
        return List.copyOf(result);
    }

    private static @Nullable Provision parseProvision(JsonElement element) {
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        BlockSelector at = json.has("at") ? BlockSelectors.parse(json.get("at")) : null;
        if (at == null || !json.has("start") || !json.has("stop")) return null;
        String item = string(json, "item");
        if (json.has("item") && !validSelector(item)) return null;
        ItemCondition itemCondition = json.has("item_condition")
                ? ItemConditions.parse(json.get("item_condition")) : null;
        if (json.has("item_condition") && (itemCondition == null || item == null)) return null;
        BlockAction start = BlockActions.parse(json.get("start"));
        BlockAction stop = BlockActions.parse(json.get("stop"));
        if (start == null || stop == null) return null;
        BlockCondition managedWhen = json.has("managed_when")
                ? BlockConditions.parse(json.get("managed_when")) : null;
        if (json.has("managed_when") && managedWhen == null) return null;
        return new Provision(at, item, itemCondition, start, managedWhen, stop);
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
            if (item != null && selector == null) return null;

            boolean explicitAction = json.has("action");
            JsonElement actionJson;
            if (explicitAction) {
                actionJson = json.get("action");
            } else {
                JsonObject useBlock = new JsonObject();
                useBlock.addProperty("type", "pheno:use_block");
                useBlock.addProperty("item", "item");
                actionJson = useBlock;
            }
            BlockAction action = BlockActions.parse(actionJson);
            if (action == null) return null;

            BlockCondition condition = json.has("condition")
                    ? BlockConditions.parse(json.get("condition")) : null;
            if (json.has("condition") && condition == null) return null;
            ItemCondition itemCondition = json.has("item_condition")
                    ? ItemConditions.parse(json.get("item_condition")) : null;
            if (json.has("item_condition") && (itemCondition == null || item == null)) return null;

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
            int expectedCount = integer(json, "expected_count", 1);
            if (xp < 1 || expectedCount < 1 || (item == null && !explicitAction)
                    || (!explicitAction && outputs.isEmpty())) return null;
            interactions.add(new Interaction(item, itemCondition, condition, action,
                    Set.copyOf(outputs), expectedCount, xp));
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
        BlockAction action = json.has("action") ? BlockActions.parse(json.get("action")) : null;
        if (json.has("action") && action == null) return null;
        return new Placement(x, y, z, Map.copyOf(properties), List.copyOf(copied), action);
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

    private static double number(JsonObject json, String key, double fallback) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive()
                    ? json.get(key).getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return Double.NaN;
        }
    }

    private static @Nullable TunableInt tunableInt(@Nullable JsonElement element, int fallback) {
        if (element == null || element.isJsonNull()) return new TunableInt(fallback, null);
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                int value = element.getAsInt();
                return value < 0 ? null : new TunableInt(value, null);
            }
            if (ConfigGate.validNumber(element)) {
                int value = element.getAsJsonObject().has("default")
                        ? element.getAsJsonObject().get("default").getAsInt() : fallback;
                return value < 0 ? null : new TunableInt(value, element.getAsJsonObject().deepCopy());
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
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

    private static boolean validSelector(@Nullable String selector) {
        if (selector == null) return false;
        return id(selector.startsWith("#") ? selector.substring(1) : selector) != null;
    }

    private static boolean matchesItem(String selector, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !validSelector(selector)) return false;
        ResourceLocation id = ResourceLocation.tryParse(
                selector.startsWith("#") ? selector.substring(1) : selector);
        return selector.startsWith("#")
                ? stack.is(TagKey.create(Registries.ITEM, id))
                : stack.is(BuiltInRegistries.ITEM.get(id));
    }
}
