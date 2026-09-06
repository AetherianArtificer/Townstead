package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, reload-local composition for independently gated Career providers.
 *
 * <p>A provider contributes to a stable Profession or Path identity; it never registers a
 * foreign villager profession. Arrays are stable unions, while scalar presentation fields use
 * priority then resource id as an explicit last-writer-wins order. All input is rebuilt from the
 * resource manager on every reload, so removing a pack or mod removes its contribution cleanly.</p>
 */
public final class CareerProviderContributions {

    public static final String SCHEMA = "townstead:career_provider/v1";

    /** Stable target used by provenance queries and provider-backed Path activation. */
    public record Target(ResourceLocation profession, String path) {
        public Target {
            path = path == null ? "" : path;
        }
    }

    /** One active, validated contribution. */
    public record Contribution(ResourceLocation source, Target target, int priority,
                               JsonObject contributes, List<ResourceLocation> aliases) {}

    /** Immutable plan built once per reload, before any Profession document is mutated. */
    public static final class Plan {
        private final List<Contribution> contributions;
        private final Map<Target, List<ResourceLocation>> provenance;

        private Plan(List<Contribution> contributions) {
            this.contributions = List.copyOf(contributions);
            Map<Target, List<ResourceLocation>> sources = new LinkedHashMap<>();
            for (Contribution contribution : contributions) {
                sources.computeIfAbsent(contribution.target(), ignored -> new ArrayList<>())
                        .add(contribution.source());
            }
            Map<Target, List<ResourceLocation>> frozen = new LinkedHashMap<>();
            sources.forEach((target, ids) -> frozen.put(target, List.copyOf(ids)));
            this.provenance = Map.copyOf(frozen);
        }

        public boolean hasProvider(ResourceLocation profession, String path) {
            return provenance.containsKey(new Target(profession, path));
        }

        public Map<Target, List<ResourceLocation>> provenance() {
            return provenance;
        }

        /** Compose Path-owned fields into its ordinary path.json before it is lowered. */
        public void applyPath(ResourceLocation profession, String path, JsonObject document) {
            for (Contribution contribution : contributions) {
                if (!contribution.target().equals(new Target(profession, path))) continue;
                JsonObject contributes = contribution.contributes();
                if (contributes.has("path")) {
                    mergePath(document, requireObject(contributes, "path"));
                }
            }
        }

        /** Compose Profession work/presentation fields after work.json establishes the base. */
        public void applyProfessions(Map<ResourceLocation, JsonObject> professions) {
            for (Contribution contribution : contributions) {
                JsonObject profession = professions.get(contribution.target().profession());
                if (profession == null) continue;
                JsonObject contributes = contribution.contributes();
                if (contributes.has("profession")) {
                    mergeProfession(profession, requireObject(contributes, "profession"));
                }
            }
        }

        /** Foreign identities supplied by providers, resolved without registering them. */
        public Map<ResourceLocation, ProfessionDefs.Resolution> aliases(Set<ResourceLocation> loaded,
                                                                         Map<ResourceLocation, JsonObject> docs) {
            Map<ResourceLocation, ProfessionDefs.Resolution> result = new LinkedHashMap<>();
            for (Contribution contribution : contributions) {
                Target target = contribution.target();
                if (!loaded.contains(target.profession())) continue;
                if (!target.path().isBlank()
                        && !declaredPathIds(docs.get(target.profession())).contains(target.path())) {
                    continue;
                }
                for (ResourceLocation alias : contribution.aliases()) {
                    if (loaded.contains(alias)) continue;
                    result.putIfAbsent(alias, new ProfessionDefs.Resolution(
                            target.profession(), target.path().isBlank() ? null : target.path()));
                }
            }
            return Map.copyOf(result);
        }
    }

    private CareerProviderContributions() {}

    /**
     * Parse active providers. Invalid documents fail independently; callers may report the
     * returned error text against the resource that supplied the malformed contribution.
     */
    public static Plan plan(Map<ResourceLocation, JsonObject> documents,
                            Map<ResourceLocation, JsonObject> professions,
                            Map<ResourceLocation, String> errors) {
        List<Contribution> active = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : documents.entrySet()) {
            try {
                Contribution parsed = parse(entry.getKey(), entry.getValue());
                if (parsed == null) continue;
                if (!professions.containsKey(parsed.target().profession())) {
                    throw new IllegalArgumentException("target profession '"
                            + parsed.target().profession() + "' is not active");
                }
                active.add(parsed);
            } catch (RuntimeException error) {
                errors.put(entry.getKey(), error.getMessage());
            }
        }
        active.sort(Comparator.comparingInt(Contribution::priority)
                .thenComparing(contribution -> contribution.source().toString()));
        return new Plan(active);
    }

    /** Returns null for a well-formed provider whose mod gate is unmet. */
    static Contribution parse(ResourceLocation source, JsonObject document) {
        TownsteadSchema.validateRequired(document, SCHEMA);
        validateKeys(document, Set.of("schema", "profession", "path", "priority", "mods",
                "aliases", "contributes"), "provider");
        if (document.has("mods")) {
            Boolean met = ModGate.evaluate(document.get("mods"));
            if (met == null) throw new IllegalArgumentException("invalid mods gate expression");
            if (!met) return null;
        }
        ResourceLocation profession = ResourceLocation.tryParse(string(document, "profession"));
        if (profession == null) throw new IllegalArgumentException("'profession' must be a namespaced id");
        String path = string(document, "path");
        if (path.contains(":") || path.contains("/")) {
            throw new IllegalArgumentException("'path' must be a local Path id");
        }
        if (!document.has("contributes") || !document.get("contributes").isJsonObject()) {
            throw new IllegalArgumentException("'contributes' must be an object");
        }
        JsonObject contributes = document.getAsJsonObject("contributes").deepCopy();
        for (String key : contributes.keySet()) {
            if (!Set.of("path", "profession").contains(key)) {
                throw new IllegalArgumentException("unknown contribution section '" + key + "'");
            }
        }
        if (contributes.has("path") && path.isBlank()) {
            throw new IllegalArgumentException("a 'path' contribution requires the provider 'path' field");
        }
        if (contributes.has("path")) validatePath(requireObject(contributes, "path"));
        if (contributes.has("profession")) validateProfession(requireObject(contributes, "profession"));

        List<ResourceLocation> aliases = new ArrayList<>();
        if (document.has("aliases")) {
            if (!document.get("aliases").isJsonArray()) {
                throw new IllegalArgumentException("'aliases' must be an array");
            }
            for (JsonElement element : document.getAsJsonArray("aliases")) {
                ResourceLocation alias = element.isJsonPrimitive()
                        ? ResourceLocation.tryParse(element.getAsString()) : null;
                if (alias == null) throw new IllegalArgumentException("provider alias must be a namespaced id");
                aliases.add(alias);
            }
        }
        int priority = document.has("priority") ? document.get("priority").getAsInt() : 0;
        return new Contribution(source, new Target(profession, path), priority,
                contributes, List.copyOf(aliases));
    }

    private static void validatePath(JsonObject path) {
        Set<String> allowed = Set.of("skills", "worksites", "clothing", "work", "powers",
                "name", "title", "color", "backdrop", "storage");
        validateKeys(path, allowed, "path");
        validateArrays(path, Set.of("skills", "worksites", "clothing", "work", "powers"));
        if (path.has("storage") && !path.get("storage").isJsonObject()) {
            throw new IllegalArgumentException("path 'storage' must be an object");
        }
    }

    private static void validateProfession(JsonObject profession) {
        Set<String> allowed = Set.of("poi", "tasks", "clothing", "storage", "icon", "work_sound");
        validateKeys(profession, allowed, "profession");
        validateArrays(profession, Set.of("poi", "tasks", "clothing"));
    }

    private static void validateKeys(JsonObject object, Set<String> allowed, String section) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("unknown " + section + " contribution field '" + key + "'");
            }
        }
    }

    private static void validateArrays(JsonObject object, Set<String> fields) {
        for (String field : fields) {
            if (object.has(field) && !object.get(field).isJsonArray()) {
                throw new IllegalArgumentException("'" + field + "' must be an array");
            }
        }
    }

    private static void mergePath(JsonObject target, JsonObject contribution) {
        mergeArrays(target, contribution, Set.of("skills", "worksites", "clothing", "work", "powers"));
        replaceScalars(target, contribution, Set.of("name", "title", "color", "backdrop", "storage"));
    }

    private static void mergeProfession(JsonObject target, JsonObject contribution) {
        mergeArraysMapped(target, contribution, Map.of(
                "poi", "poi", "tasks", "work_tasks", "clothing", "clothing"));
        replaceScalars(target, contribution, Set.of("storage", "icon", "work_sound"));
    }

    private static void mergeArrays(JsonObject target, JsonObject contribution, Set<String> fields) {
        Map<String, String> identity = new LinkedHashMap<>();
        fields.forEach(field -> identity.put(field, field));
        mergeArraysMapped(target, contribution, identity);
    }

    private static void mergeArraysMapped(JsonObject target, JsonObject contribution,
                                          Map<String, String> fields) {
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (!contribution.has(field.getKey())) continue;
            JsonArray merged = target.has(field.getValue()) && target.get(field.getValue()).isJsonArray()
                    ? target.getAsJsonArray(field.getValue()).deepCopy() : new JsonArray();
            Set<String> seen = new LinkedHashSet<>();
            for (JsonElement element : merged) seen.add(element.toString());
            for (JsonElement element : contribution.getAsJsonArray(field.getKey())) {
                if (seen.add(element.toString())) merged.add(element.deepCopy());
            }
            target.add(field.getValue(), merged);
        }
    }

    private static void replaceScalars(JsonObject target, JsonObject contribution,
                                       Set<String> fields) {
        for (String field : fields) {
            if (contribution.has(field)) target.add(field, contribution.get(field).deepCopy());
        }
    }

    private static JsonObject requireObject(JsonObject parent, String field) {
        if (!parent.has(field) || !parent.get(field).isJsonObject()) {
            throw new IllegalArgumentException("'" + field + "' must be an object");
        }
        return parent.getAsJsonObject(field);
    }

    private static String string(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonPrimitive()
                && object.getAsJsonPrimitive(field).isString()
                ? object.get(field).getAsString().trim() : "";
    }

    private static Set<String> declaredPathIds(JsonObject profession) {
        Set<String> paths = new LinkedHashSet<>();
        if (profession == null || !profession.has("paths")
                || !profession.get("paths").isJsonArray()) return paths;
        for (JsonElement element : profession.getAsJsonArray("paths")) {
            if (!element.isJsonObject()) continue;
            String id = string(element.getAsJsonObject(), "id");
            if (!id.isBlank()) paths.add(id);
        }
        return paths;
    }
}
