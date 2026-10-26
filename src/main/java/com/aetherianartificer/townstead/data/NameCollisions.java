package com.aetherianartificer.townstead.data;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The collision rule for identity names. When documents of one kind from different namespaces
 * share a display name ("High Elf" from two packs), every one of them is qualified with its
 * pack's qualifier: "Classic High Elf" and "Runic High Elf". Names that do not clash are left
 * alone, so a pack reads naturally on its own and only disambiguates beside a rival.
 *
 * <p>A qualifier comes from, in order: the document's own {@code "qualifier"} field, the
 * {@code "qualifier"} in {@code data/<ns>/townstead/pack.json}, or the namespace itself with a
 * leading {@code townstead_} dropped and words capitalised ({@code townstead_classic} → Classic).
 * Qualifiers are text components, so they translate. The {@code display_name} and every
 * {@code demonym} form of a qualified document are rewritten to
 * {@code {"translate": "townstead.qualified_name", "with": [qualifier, name]}}.</p>
 *
 * <p>Names are compared on their English text, case-insensitively.</p>
 */
public final class NameCollisions {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/NameCollisions");
    private static final Gson GSON = new Gson();

    public static final String QUALIFIED_KEY = "townstead.qualified_name";
    public static final String QUALIFIER = "qualifier";
    public static final String PACK_FILE = "townstead/pack.json";

    private NameCollisions() {}

    /** Qualify clashing names among {@code documents} in place, reading pack qualifiers from {@code manager}. */
    public static void qualify(String kind, Map<ResourceLocation, JsonElement> documents, ResourceManager manager,
                               Map<String, String> englishLang) {
        Map<String, Optional<JsonElement>> packQualifiers = new HashMap<>();
        qualify(kind, documents, englishLang, namespace -> packQualifiers
                .computeIfAbsent(namespace, ns -> Optional.ofNullable(readPackQualifier(manager, ns)))
                .orElse(null));
    }

    /** As above with an explicit pack-qualifier lookup. Returns the ids that were qualified. */
    public static Set<ResourceLocation> qualify(String kind, Map<ResourceLocation, JsonElement> documents,
                                                Map<String, String> englishLang,
                                                Function<String, JsonElement> packQualifier) {
        Map<String, List<ResourceLocation>> byName = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : documents.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) continue;
            String name = englishText(entry.getValue().getAsJsonObject().get("display_name"), englishLang);
            if (name == null || name.isBlank()) continue;
            byName.computeIfAbsent(name.trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(entry.getKey());
        }
        Set<ResourceLocation> qualified = new LinkedHashSet<>();
        for (Map.Entry<String, List<ResourceLocation>> group : byName.entrySet()) {
            List<ResourceLocation> ids = group.getValue();
            Set<String> namespaces = new LinkedHashSet<>();
            for (ResourceLocation id : ids) namespaces.add(id.getNamespace());
            if (namespaces.size() < 2) continue;
            for (ResourceLocation id : ids) {
                JsonObject document = documents.get(id).getAsJsonObject();
                JsonElement qualifier = document.has(QUALIFIER) ? document.get(QUALIFIER) : packQualifier.apply(id.getNamespace());
                if (qualifier == null) qualifier = new JsonPrimitive(defaultQualifier(id.getNamespace()));
                applyQualifier(document, qualifier);
                qualified.add(id);
            }
            LOGGER.info("Qualified {} {} sharing the name \"{}\": {}", ids.size(), kind, group.getKey(), ids);
        }
        return qualified;
    }

    private static void applyQualifier(JsonObject document, JsonElement qualifier) {
        document.add("display_name", wrap(qualifier, document.get("display_name")));
        JsonElement demonym = document.get("demonym");
        if (demonym != null && demonym.isJsonObject()) {
            JsonObject d = demonym.getAsJsonObject();
            if (d.has("singular") && !d.has("plural")) d.add("plural", d.get("singular").deepCopy());
            for (String form : new String[]{"singular", "plural", "adjective"}) {
                if (d.has(form)) d.add(form, wrap(qualifier, d.get(form)));
            }
        }
    }

    private static JsonObject wrap(JsonElement qualifier, JsonElement name) {
        JsonObject out = new JsonObject();
        out.addProperty("translate", QUALIFIED_KEY);
        JsonArray with = new JsonArray();
        with.add(qualifier.deepCopy());
        with.add(name.deepCopy());
        out.add("with", with);
        return out;
    }

    /** {@code townstead_classic} → "Classic", {@code runic_roots} → "Runic Roots". */
    public static String defaultQualifier(String namespace) {
        String base = namespace.startsWith("townstead_") ? namespace.substring("townstead_".length()) : namespace;
        StringBuilder out = new StringBuilder();
        for (String word : base.split("[_\\-.]+")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? namespace : out.toString();
    }

    /** The English text of a component JSON as data-pack lang resolves it; null when unreadable. */
    static @Nullable String englishText(@Nullable JsonElement component, Map<String, String> englishLang) {
        if (component == null || component.isJsonNull()) return null;
        if (component.isJsonPrimitive()) return component.getAsString();
        if (!component.isJsonObject()) return null;
        JsonObject obj = component.getAsJsonObject();
        if (obj.has("translate")) {
            String key = obj.get("translate").getAsString();
            return englishLang.getOrDefault(key, key);
        }
        if (obj.has("text")) return obj.get("text").getAsString();
        return null;
    }

    private static @Nullable JsonElement readPackQualifier(ResourceManager manager, String namespace) {
        ResourceLocation location = DataPackLang.parseId(namespace + ":" + PACK_FILE);
        if (location == null) return null;
        Optional<Resource> resource = manager.getResource(location);
        if (resource.isEmpty()) return null;
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            return json == null ? null : json.get(QUALIFIER);
        } catch (Exception ex) {
            LOGGER.warn("Failed to read {}: {}", location, ex.getMessage());
            return null;
        }
    }
}
