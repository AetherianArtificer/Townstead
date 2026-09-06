package com.aetherianartificer.townstead.needs;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.food.ConsumptionPolicy;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data-pack supplied effects and planner facts for consumed items.
 *
 * <p>A definition with {@code "fallback": true} only supplies hydration when the selected
 * thirst backend has no value of its own. Ordinary definitions remain deliberate datapack
 * overrides. Fallback definitions are hydration-only so deferring cannot discard unrelated
 * effects.</p>
 */
public final class Consumables {
    public static final String SCHEMA = "townstead:consumable/v1";
    public static final String SCHEMA_V2 = "townstead:consumable/v2";
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/Consumables");
    private static volatile List<Definition> DEFINITIONS = List.of();

    private Consumables() {}

    public record Definition(ResourceLocation id, Set<ResourceLocation> items,
                             Set<ResourceLocation> tags, @Nullable Action effects,
                             boolean fallback, NeedEffectProjection projection,
                             @Nullable ConsumptionPolicy transaction) {
        boolean matches(ItemStack stack) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (items.contains(itemId)) return true;
            for (ResourceLocation tag : tags) {
                if (stack.is(TagKey.create(Registries.ITEM, tag))) return true;
            }
            return false;
        }

        boolean exact(ItemStack stack) {
            return items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
    }

    /** Item-expanded form sent to clients and installed into optional thirst backends. */
    public record ResolvedEffect(NeedEffectProjection projection, boolean fallback) {}

    public static @Nullable Definition resolve(ItemStack stack) {
        return resolve(stack, null);
    }

    /** Resolves the definition authored for one consumer audience. */
    public static @Nullable Definition resolve(ItemStack stack,
                                               ConsumptionPolicy.Consumer consumer) {
        return resolve(DEFINITIONS, stack, consumer);
    }

    static @Nullable Definition resolve(List<Definition> definitions, ItemStack stack,
                                        ConsumptionPolicy.Consumer consumer) {
        if (stack == null || stack.isEmpty()) return null;
        Definition exact = resolveExact(definitions,
                BuiltInRegistries.ITEM.getKey(stack.getItem()), consumer);
        if (exact != null) return exact;
        Definition tagMatch = null;
        for (Definition definition : definitions) {
            if (consumer != null && !permits(definition, consumer)) continue;
            if (tagMatch == null && definition.matches(stack)) tagMatch = definition;
        }
        return tagMatch;
    }

    /** Pure exact-selector path used by runtime resolution and registry-free unit tests. */
    static @Nullable Definition resolveExact(List<Definition> definitions, ResourceLocation item,
                                             ConsumptionPolicy.Consumer consumer) {
        if (item == null) return null;
        for (Definition definition : definitions) {
            if ((consumer == null || permits(definition, consumer))
                    && definition.items().contains(item)) return definition;
        }
        return null;
    }

    public static boolean hasEffects(ItemStack stack) {
        Definition definition = resolveEffects(stack);
        return definition != null && definition.effects() != null;
    }

    public static boolean hasEffects(ItemStack stack, ConsumptionPolicy.Consumer consumer) {
        Definition definition = resolve(stack, consumer);
        return definition != null && definition.effects() != null;
    }

    /** Selects an effects-bearing definition independent of its transaction audience. */
    public static @Nullable Definition resolveEffects(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Definition tagMatch = null;
        for (Definition definition : DEFINITIONS) {
            if (definition.effects() == null) continue;
            if (definition.exact(stack)) return definition;
            if (tagMatch == null && definition.matches(stack)) tagMatch = definition;
        }
        return tagMatch;
    }

    /** Maximum portion count across consumer-specific transaction views of this item. */
    public static int servings(ItemStack stack) {
        int servings = 0;
        for (Definition definition : DEFINITIONS) {
            if (definition.matches(stack) && definition.transaction() != null) {
                servings = Math.max(servings, definition.transaction().servings());
            }
        }
        return servings;
    }

    public static List<Definition> all() { return DEFINITIONS; }

    /**
     * Expands every exact item and item-tag selector into the authoritative item-level view used
     * by clients and optional-mod compatibility registries. Exact selectors retain the same
     * precedence as {@link #resolve(ItemStack)}; otherwise the first matching tag definition wins.
     * This work only runs on data reload / sync, never in a villager tick.
     */
    public static Map<ResourceLocation, ResolvedEffect> resolvedEffects() {
        Map<ResourceLocation, ResolvedEffect> exact = new LinkedHashMap<>();
        for (Definition definition : DEFINITIONS) {
            if (definition.effects() == null) continue;
            for (ResourceLocation item : definition.items()) {
                if (BuiltInRegistries.ITEM.containsKey(item)) {
                    exact.putIfAbsent(item, new ResolvedEffect(definition.projection(), definition.fallback()));
                }
            }
        }

        Map<ResourceLocation, ResolvedEffect> resolved = new LinkedHashMap<>(exact);
        for (Definition definition : DEFINITIONS) {
            if (definition.effects() == null) continue;
            for (ResourceLocation tagId : definition.tags()) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
                BuiltInRegistries.ITEM.getTag(tag).ifPresent(holders -> holders.forEach(holder -> {
                    ResourceLocation item = BuiltInRegistries.ITEM.getKey(holder.value());
                    resolved.putIfAbsent(item, new ResolvedEffect(definition.projection(), definition.fallback()));
                }));
            }
        }
        return Map.copyOf(resolved);
    }

    public static Map<ResourceLocation, NeedEffectProjection> resolvedProjections() {
        Map<ResourceLocation, NeedEffectProjection> projections = new LinkedHashMap<>();
        resolvedEffects().forEach((item, effect) -> projections.put(item, effect.projection()));
        return Map.copyOf(projections);
    }

    public static NeedEffectProjection projection(ItemStack stack) {
        Definition definition = resolveEffects(stack);
        return definition == null ? NeedEffectProjection.NONE : definition.projection();
    }


    public static NeedEffectProjection projection(ItemStack stack,
                                                  ConsumptionPolicy.Consumer consumer) {
        Definition definition = resolve(stack, consumer);
        return definition == null ? NeedEffectProjection.NONE : definition.projection();
    }

    public static boolean apply(VillagerEntityMCA villager, ItemStack consumed) {
        Definition definition = resolve(consumed, ConsumptionPolicy.Consumer.VILLAGER);
        if (definition == null || definition.effects() == null) return false;
        if (definition.fallback() && nativeThirstRecognizes(consumed)) return false;
        ActionContext context = new ActionContext(villager);
        definition.effects().run(context);
        return context.succeeded();
    }

    /** True when this definition, rather than the thirst backend, owns hydration for the item. */
    public static boolean suppliesHydration(ItemStack stack) {
        Definition definition = resolveEffects(stack);
        return definition != null && definition.projection().hydrates()
                && (!definition.fallback() || !nativeThirstRecognizes(stack));
    }


    public static boolean suppliesHydration(ItemStack stack,
                                            ConsumptionPolicy.Consumer consumer) {
        Definition definition = resolve(stack, consumer);
        return definition != null && definition.projection().hydrates()
                && (!definition.fallback() || !nativeThirstRecognizes(stack));
    }

    private static boolean nativeThirstRecognizes(ItemStack stack) {
        ThirstCompatBridge bridge = ThirstBridgeResolver.getNative();
        return bridge != null && bridge.itemRestoresThirst(stack);
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : manager
                    .listResources("consumable", id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + path.substring("consumable/".length(), path.length() - ".json".length()));
                if (id == null) continue;
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                } catch (Exception exception) {
                    LOGGER.warn("Failed to read consumable {}: {}", file, exception.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager manager,
                             ProfilerFiller profiler) {
            List<Definition> loaded = new ArrayList<>();
            List<Map.Entry<ResourceLocation, JsonObject>> entries = new ArrayList<>(prepared.entrySet());
            entries.sort(Comparator.comparing(entry -> entry.getKey().toString()));
            for (Map.Entry<ResourceLocation, JsonObject> entry : entries) {
                JsonObject json = entry.getValue();
                try { validateSchema(json); }
                catch (RuntimeException exception) {
                    LOGGER.warn("Consumable {} rejected: {}", entry.getKey(), exception.getMessage());
                    continue;
                }
                if (json.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(json.get("mods")))) continue;
                Definition definition;
                try { definition = parse(entry.getKey(), json); }
                catch (RuntimeException exception) {
                    LOGGER.warn("Consumable {} rejected: {}", entry.getKey(), exception.getMessage());
                    continue;
                }
                if (definition == null) {
                    LOGGER.warn("Invalid consumable {} (needs item selectors and valid v1/v2 content)",
                            entry.getKey());
                    continue;
                }
                loaded.add(definition);
            }
            loaded = withoutAmbiguities(loaded);
            DEFINITIONS = List.copyOf(loaded);
            com.aetherianartificer.townstead.compat.thirst.DataDrivenThirstCompat.refresh();
            LOGGER.info("Loaded {} consumable definitions", loaded.size());
        }
    }

    static @Nullable Definition parse(ResourceLocation id, JsonObject json) {
        validateSchema(json);
        if (!json.has("items") || !json.get("items").isJsonArray()) return null;
        boolean v2 = SCHEMA_V2.equals(json.has("schema")
                ? json.getAsJsonPrimitive("schema").getAsString() : "");
        if (!v2 && !json.has("effects")) return null;
        Set<ResourceLocation> items = new LinkedHashSet<>();
        Set<ResourceLocation> tags = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray("items")) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
            String selector = element.getAsString();
            boolean tag = selector.startsWith("#");
            ResourceLocation target = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
            if (target == null) return null;
            (tag ? tags : items).add(target);
        }
        if (items.isEmpty() && tags.isEmpty()) return null;
        Action effects = json.has("effects") ? Actions.parse(json.get("effects")) : null;
        if (json.has("effects") && effects == null) return null;
        NeedEffectProjection projection = json.has("effects")
                ? NeedEffectProjection.project(json.get("effects")) : NeedEffectProjection.NONE;
        boolean fallback = GsonHelper.getAsBoolean(json, "fallback", false);
        if (fallback && (!projection.hydrates() || projection.energizes())) {
            LOGGER.warn("Consumable {} uses fallback with non-hydration effects; fallback definitions must be hydration-only", id);
            return null;
        }
        ConsumptionPolicy transaction = json.has("transaction")
                ? ConsumptionPolicy.parse(json.get("transaction")) : null;
        if (!v2 && transaction != null) return null;
        return new Definition(id, Set.copyOf(items), Set.copyOf(tags), effects, fallback,
                projection, transaction);
    }

    /**
     * Rejects the later of two definitions that claim the same selector for an overlapping
     * consumer audience. Resource-id ordering makes rejection stable across resource managers.
     * Player and villager views of the same item are deliberately allowed when disjoint.
     */
    static List<Definition> withoutAmbiguities(List<Definition> definitions) {
        List<Definition> accepted = new ArrayList<>();
        for (Definition candidate : definitions) {
            Definition conflict = null;
            for (Definition existing : accepted) {
                if (selectorsOverlap(existing, candidate)
                        && !java.util.Collections.disjoint(consumers(existing), consumers(candidate))) {
                    conflict = existing;
                    break;
                }
            }
            if (conflict == null) {
                accepted.add(candidate);
            } else {
                LOGGER.warn("Consumable {} rejected: selector and consumer audience overlap with {}",
                        candidate.id(), conflict.id());
            }
        }
        return List.copyOf(accepted);
    }

    private static boolean selectorsOverlap(Definition first, Definition second) {
        return !java.util.Collections.disjoint(first.items(), second.items())
                || !java.util.Collections.disjoint(first.tags(), second.tags());
    }

    private static Set<ConsumptionPolicy.Consumer> consumers(Definition definition) {
        return definition.transaction() == null
                ? Set.copyOf(EnumSet.allOf(ConsumptionPolicy.Consumer.class))
                : definition.transaction().consumers();
    }

    private static boolean permits(Definition definition, ConsumptionPolicy.Consumer consumer) {
        return definition.transaction() == null || definition.transaction().permits(consumer);
    }

    private static void validateSchema(JsonObject json) {
        if (!json.has("schema")) return;
        if (!json.get("schema").isJsonPrimitive()
                || !json.getAsJsonPrimitive("schema").isString()) {
            throw new IllegalArgumentException("'schema' must be a string");
        }
        String schema = json.get("schema").getAsString();
        if (!SCHEMA.equals(schema) && !SCHEMA_V2.equals(schema)) {
            throw new IllegalArgumentException("Expected schema '" + SCHEMA + "' or '"
                    + SCHEMA_V2 + "', got '" + schema + "'");
        }
    }
}
