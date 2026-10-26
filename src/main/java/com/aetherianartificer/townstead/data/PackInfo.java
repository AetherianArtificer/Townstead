package com.aetherianartificer.townstead.data;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What a namespace calls itself, from {@code data/<ns>/townstead/pack.json}: a {@code "name"}
 * ("Runic Roots") for places that list packs, and the short {@code "qualifier"} ("Runic") that
 * also settles name collisions. Both are text components, so they translate. A pack without the
 * file is named after its namespace ({@link NameCollisions#defaultQualifier}); without a name the
 * qualifier stands in.
 */
public final class PackInfo {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/PackInfo");
    private static final Gson GSON = new Gson();

    public record Info(Component name, Component qualifier) {}

    private static volatile Map<String, Info> BY_NAMESPACE = Map.of();

    private PackInfo() {}

    /** Read the pack file of every namespace given, replacing what was recorded before. */
    public static void record(ResourceManager manager, Collection<String> namespaces, Map<String, String> lang) {
        Map<String, Info> out = new HashMap<>();
        for (String namespace : namespaces) out.put(namespace, read(manager, namespace, lang));
        BY_NAMESPACE = Map.copyOf(out);
    }

    /** The recorded info for a namespace, or one derived from the namespace itself. */
    public static Info of(String namespace) {
        Info info = BY_NAMESPACE.get(namespace);
        return info != null ? info : fallback(namespace);
    }

    public static void setForTesting(Map<String, Info> infos) {
        BY_NAMESPACE = Map.copyOf(infos);
    }

    static Info fallback(String namespace) {
        Component qualifier = Component.literal(NameCollisions.defaultQualifier(namespace));
        return new Info(qualifier, qualifier);
    }

    static Info parse(String namespace, JsonObject json, Map<String, String> lang) {
        Info fallback = fallback(namespace);
        Component qualifier = component(json.get(NameCollisions.QUALIFIER), fallback.qualifier(), lang);
        Component name = component(json.get("name"), qualifier, lang);
        return new Info(name, qualifier);
    }

    private static Component component(JsonElement element, Component fallback, Map<String, String> lang) {
        if (element == null || element.isJsonNull()) return fallback;
        try {
            return DataPackLang.parseComponent(element, "pack.json", lang);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static Info read(ResourceManager manager, String namespace, Map<String, String> lang) {
        ResourceLocation location = DataPackLang.parseId(namespace + ":" + NameCollisions.PACK_FILE);
        if (location == null) return fallback(namespace);
        Optional<Resource> resource = manager.getResource(location);
        if (resource.isEmpty()) return fallback(namespace);
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            return json == null ? fallback(namespace) : parse(namespace, json, lang);
        } catch (Exception ex) {
            LOGGER.warn("Failed to read {}: {}", location, ex.getMessage());
            return fallback(namespace);
        }
    }
}
