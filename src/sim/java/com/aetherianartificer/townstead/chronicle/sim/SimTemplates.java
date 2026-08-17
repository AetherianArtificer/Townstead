package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads chronicle event templates straight off disk, mirroring what
 * {@code ChronicleEventJsonLoader} does with a ResourceManager: same schema
 * check, same {@link ChronicleEventTemplate#parse}, same lang sidecar index.
 *
 * <p>Pheno conditions and effects need a registry that only exists in a running
 * game, so {@code when}/{@code effects} silently parse to null here. That is
 * reported as a warning rather than passed over: a template gated in game runs
 * ungated in the harness.</p>
 */
public final class SimTemplates {

    private static final Gson GSON = new Gson();
    private static final String EVENT_DIR = "chronicle_event";

    public record Loaded(Map<ResourceLocation, ChronicleEventTemplate> templates,
                         List<String> warnings) {}

    private SimTemplates() {}

    /** {@code dataRoot} is a data-pack {@code data/} directory holding namespace folders. */
    public static Loaded load(Path dataRoot) throws IOException {
        Map<ResourceLocation, ChronicleEventTemplate> templates = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        if (!Files.isDirectory(dataRoot)) {
            warnings.add("no data directory at " + dataRoot);
            return new Loaded(templates, warnings);
        }

        Map<String, String> allLang = new LinkedHashMap<>();
        try (Stream<Path> namespaces = Files.list(dataRoot)) {
            for (Path namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                allLang.putAll(loadLang(namespaceDir.resolve("lang").resolve("en_us.json")));
            }
        }
        warnings.addAll(SimBondKinds.load(dataRoot, allLang));
        warnings.addAll(SimCompetence.load(dataRoot));

        try (Stream<Path> namespaces = Files.list(dataRoot)) {
            for (Path namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                String namespace = namespaceDir.getFileName().toString();
                Map<String, String> lang = loadLang(namespaceDir.resolve("lang").resolve("en_us.json"));
                Path eventDir = namespaceDir.resolve(EVENT_DIR);
                if (!Files.isDirectory(eventDir)) continue;
                try (Stream<Path> files = Files.walk(eventDir)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                        loadOne(namespace, eventDir, file, lang, templates, warnings);
                    }
                }
            }
        }
        return new Loaded(templates, warnings);
    }

    private static void loadOne(String namespace, Path eventDir, Path file, Map<String, String> lang,
                                Map<ResourceLocation, ChronicleEventTemplate> templates,
                                List<String> warnings) {
        String path = eventDir.relativize(file).toString().replace('\\', '/');
        path = path.substring(0, path.length() - ".json".length());
        ResourceLocation id = DataPackLang.parseId(namespace + ":" + path);
        if (id == null) {
            warnings.add("unusable id for " + file);
            return;
        }
        try {
            JsonObject json = GsonHelper.convertToJsonObject(
                    GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonElement.class),
                    id.toString());
            TownsteadSchema.validate(json, "townstead:chronicle_event/v1");
            ChronicleEventTemplate template = ChronicleEventTemplate.parse(id, json, lang);
            templates.put(id, template);
            reportDropped(id, json, template, warnings);
        } catch (Exception ex) {
            warnings.add("failed to parse " + id + ": "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    private static void reportDropped(ResourceLocation id, JsonObject json,
                                      ChronicleEventTemplate template, List<String> warnings) {
        // A live-only template never reaches a fabricated past, so what its gates
        // need offline says nothing about it.
        if (!template.contexts().contains(ChronicleEventTemplate.Context.PREGEN)) return;
        if (json.has("roles")) {
            var raw = GsonHelper.getAsJsonArray(json, "roles");
            for (int i = 0; i < raw.size() && i < template.roles().size(); i++) {
                ChronicleEventTemplate.RoleSpec role = template.roles().get(i);
                if (!raw.get(i).getAsJsonObject().has("when")) continue;
                if (role.when() == null) {
                    warnings.add(id + ": role '" + role.id()
                            + "' uses a condition type this harness does not register, so the"
                            + " template runs ungated here");
                } else if (!role.when().supportsSubject()) {
                    warnings.add(id + ": role '" + role.id()
                            + "' is gated on live world state, so pre-history drops the template"
                            + " rather than guess");
                }
            }
        }
        if (json.has("effects") && template.effects().isEmpty()) {
            warnings.add(id + ": effects dropped (no pheno registry offline)");
        }
        for (String param : template.unfillablePregenParams()) {
            warnings.add(id + ": pre-history cannot fill display param '" + param
                    + "', so the fabricated headline renders blank");
        }
    }

    private static Map<String, String> loadLang(Path file) {
        Map<String, String> lang = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) return lang;
        try {
            JsonObject json = GsonHelper.convertToJsonObject(
                    GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonElement.class),
                    file.toString());
            for (String key : json.keySet()) {
                JsonElement value = json.get(key);
                if (value.isJsonPrimitive()) lang.put(key, value.getAsString());
            }
        } catch (Exception ignored) {
            // A missing or malformed sidecar only costs readable headlines.
        }
        return lang;
    }
}
