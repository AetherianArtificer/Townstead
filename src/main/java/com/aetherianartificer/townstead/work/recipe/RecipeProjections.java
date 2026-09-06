package com.aetherianartificer.townstead.work.recipe;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Data-authored, versioned semantic views over otherwise private foreign recipe fields. */
public final class RecipeProjections {
    public static final String SCHEMA = "townstead:recipe_projection/v1";
    private static final Gson GSON = new GsonBuilder().create();

    /** The deliberately small public vocabulary consumed by planners and station protocols. */
    public static final Set<String> SEMANTIC_FIELDS = Set.of(
            "inputs", "catalysts", "tools", "input_fluid", "output_fluid",
            "fluid_amount", "time", "environment", "output", "byproducts",
            "readiness", "conditions", "allow", "deny", "base", "density",
            "priority", "before", "after", "container");

    public record Definition(ResourceLocation id, List<ResourceLocation> recipeTypes,
                             @Nullable JsonElement mods, String domain,
                             Map<String, RecipeProjectionAccess.Accessor> fields,
                             int priority, String source) {
        public Definition {
            recipeTypes = List.copyOf(recipeTypes);
            mods = mods == null ? null : mods.deepCopy();
            fields = Map.copyOf(fields);
        }
    }

    public enum FailureKind {
        NO_PROJECTION, MOD_GATE_FALSE, MOD_GATE_INVALID, REQUIRED_FIELD_MISSING,
        READINESS_FALSE
    }

    public record Diagnostic(FailureKind kind, String semanticField, String detail) {}

    public record Provenance(ResourceLocation definition, ResourceLocation recipe,
                             ResourceLocation recipeType, String source,
                             Map<String, String> selectedAliases) {
        public Provenance { selectedAliases = Map.copyOf(selectedAliases); }
    }

    public record View(boolean succeeded, String domain, Map<String, Object> values,
                       Map<String, Object> rawFields,
                       Map<String, List<String>> rawFieldFailures,
                       @Nullable Provenance provenance, List<Diagnostic> diagnostics) {
        public View {
            values = Map.copyOf(values);
            rawFields = Map.copyOf(rawFields);
            rawFieldFailures = Map.copyOf(rawFieldFailures);
            diagnostics = List.copyOf(diagnostics);
        }

        public @Nullable Object value(String field) { return values.get(field); }
        public int intValue(String field, int fallback) {
            Object value = values.get(field);
            return value instanceof Number number ? number.intValue() : fallback;
        }
        public double numberValue(String field, double fallback) {
            Object value = values.get(field);
            return value instanceof Number number ? number.doubleValue() : fallback;
        }
        public @Nullable ResourceLocation idValue(String field) {
            Object value = values.get(field);
            return value instanceof ResourceLocation id ? id : null;
        }
        @SuppressWarnings("unchecked")
        public List<Object> listValue(String field) {
            Object value = values.get(field);
            return value instanceof List<?> list ? (List<Object>) list : List.of();
        }
        public boolean ready() {
            Object value = values.get("readiness");
            return !(value instanceof Boolean ready) || ready;
        }
        public String failureSummary() {
            return diagnostics.stream().map(d -> d.kind() + (d.semanticField().isBlank()
                    ? "" : "[" + d.semanticField() + "]") + ": " + d.detail())
                    .reduce((left, right) -> left + "; " + right).orElse("");
        }
    }

    private static volatile List<Definition> definitions = List.of();
    private RecipeProjections() {}

    public static View project(ResourceLocation recipe, ResourceLocation recipeType,
                               @Nullable Object source) {
        List<Definition> candidates = definitions.stream()
                .filter(definition -> definition.recipeTypes().contains(recipeType))
                .sorted(Comparator.comparingInt(Definition::priority).reversed()
                        .thenComparing(definition -> definition.id().toString()))
                .toList();
        if (candidates.isEmpty()) return failed(FailureKind.NO_PROJECTION, "",
                "no projection declares recipe type " + recipeType);

        List<Diagnostic> gateFailures = new ArrayList<>();
        for (Definition definition : candidates) {
            if (definition.mods() != null) {
                Boolean enabled = ModGate.evaluate(definition.mods());
                if (enabled == null) {
                    gateFailures.add(new Diagnostic(FailureKind.MOD_GATE_INVALID, "mods",
                            definition.id() + " has an invalid mod gate"));
                    continue;
                }
                if (!enabled) {
                    gateFailures.add(new Diagnostic(FailureKind.MOD_GATE_FALSE, "mods",
                            definition.id() + " is unavailable because its mod gate is false"));
                    continue;
                }
            }
            Map<String, Object> values = new LinkedHashMap<>();
            Map<String, Object> rawFields = new LinkedHashMap<>();
            Map<String, List<String>> rawFailures = new LinkedHashMap<>();
            Map<String, String> aliases = new LinkedHashMap<>();
            List<Diagnostic> failures = new ArrayList<>();
            for (Map.Entry<String, RecipeProjectionAccess.Accessor> field
                    : definition.fields().entrySet()) {
                RecipeProjectionAccess.Read read = RecipeProjectionAccess.read(source, field.getValue());
                rawFailures.put(field.getKey(), read.failures());
                if (read.found()) {
                    values.put(field.getKey(), read.value());
                    rawFields.put(field.getKey(), read.rawValue());
                    aliases.put(field.getKey(), read.selectedAlias());
                } else if (field.getValue().required()) {
                    failures.add(new Diagnostic(FailureKind.REQUIRED_FIELD_MISSING, field.getKey(),
                            String.join(" | ", read.failures())));
                }
            }
            Provenance provenance = new Provenance(definition.id(), recipe, recipeType,
                    definition.source(), aliases);
            if (!failures.isEmpty()) return new View(false, definition.domain(), values, rawFields,
                    rawFailures, provenance, failures);
            Object readiness = values.get("readiness");
            if (readiness instanceof Boolean ready && !ready) {
                return new View(false, definition.domain(), values, rawFields, rawFailures, provenance,
                        List.of(new Diagnostic(FailureKind.READINESS_FALSE, "readiness",
                                "projected recipe readiness is false")));
            }
            return new View(true, definition.domain(), values, rawFields, rawFailures, provenance, List.of());
        }
        return new View(false, "", Map.of(), Map.of(), Map.of(), null, gateFailures);
    }

    /** Recipes without a projection retain their normal public-recipe availability. */
    public static boolean ready(DiscoveredRecipe recipe) {
        ResourceLocation type = WorkRecipeRegistry.recipeTypeId(recipe);
        if (type == null || definitions.stream().noneMatch(d -> d.recipeTypes().contains(type))) return true;
        View view = project(recipe.id(), type, recipe.source() == null ? null :
                //? if >=1.21 {
                recipe.source().value()
                //?} else {
                /*recipe.source()
                *///?}
        );
        return view.succeeded() && view.ready();
    }

    private static View failed(FailureKind kind, String field, String detail) {
        return new View(false, "", Map.of(), Map.of(), Map.of(), null,
                List.of(new Diagnostic(kind, field, detail)));
    }

    static Definition parse(ResourceLocation id, JsonObject json, String source) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        for (String key : json.keySet()) {
            if (!List.of("schema", "recipe_types", "mods", "domain", "fields", "priority")
                    .contains(key)) throw new IllegalArgumentException("unknown recipe projection field '" + key + "'");
        }
        if (!json.has("recipe_types") || !json.get("recipe_types").isJsonArray()) {
            throw new IllegalArgumentException("recipe_types must be a non-empty array");
        }
        LinkedHashSet<ResourceLocation> types = new LinkedHashSet<>();
        for (JsonElement value : json.getAsJsonArray("recipe_types")) {
            ResourceLocation type = value.isJsonPrimitive()
                    ? ResourceLocation.tryParse(value.getAsString()) : null;
            if (type == null) throw new IllegalArgumentException("recipe_types contains an invalid id");
            types.add(type);
        }
        if (types.isEmpty()) throw new IllegalArgumentException("recipe_types must not be empty");
        String domain = json.has("domain") ? json.get("domain").getAsString() : "generic";
        if (domain.isBlank()) throw new IllegalArgumentException("domain must not be blank");
        if (!json.has("fields") || !json.get("fields").isJsonObject()) {
            throw new IllegalArgumentException("fields must be a non-empty object");
        }
        Map<String, RecipeProjectionAccess.Accessor> fields = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> field : json.getAsJsonObject("fields").entrySet()) {
            if (!SEMANTIC_FIELDS.contains(field.getKey())) {
                throw new IllegalArgumentException("unknown semantic recipe field '" + field.getKey() + "'");
            }
            fields.put(field.getKey(), RecipeProjectionAccess.parse(field.getValue()));
        }
        if (fields.isEmpty()) throw new IllegalArgumentException("fields must not be empty");
        JsonElement mods = json.has("mods") ? json.get("mods") : null;
        if (mods != null && ModGate.evaluate(mods, ignored -> true) == null) {
            throw new IllegalArgumentException("mods is not a valid mod gate");
        }
        int priority = json.has("priority") ? json.get("priority").getAsInt() : 0;
        return new Definition(id, List.copyOf(types), mods, domain, fields, priority, source);
    }

    static void replaceAll(List<Definition> loaded) {
        definitions = List.copyOf(loaded);
    }

    public static List<Definition> all() { return definitions; }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(GSON, "recipe_projection"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager,
                             ProfilerFiller profiler) {
            List<Definition> loaded = new ArrayList<>();
            resources.entrySet().stream().sorted(Map.Entry.comparingByKey(
                    Comparator.comparing(ResourceLocation::toString))).forEach(entry -> {
                try {
                    if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("root must be an object");
                    loaded.add(parse(entry.getKey(), entry.getValue().getAsJsonObject(),
                            "data/" + entry.getKey().getNamespace() + "/recipe_projection/"
                                    + entry.getKey().getPath() + ".json"));
                } catch (RuntimeException failure) {
                    Townstead.LOGGER.warn("Recipe projection {} rejected: {}", entry.getKey(), failure.getMessage());
                }
            });
            replaceAll(loaded);
            Townstead.LOGGER.info("Loaded {} recipe projections covering {} recipe-type aliases",
                    loaded.size(), loaded.stream().flatMap(def -> def.recipeTypes().stream()).distinct().count());
        }
    }
}
