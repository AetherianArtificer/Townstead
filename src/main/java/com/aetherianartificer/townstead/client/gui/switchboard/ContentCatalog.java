package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.switchboard.WorldKeys;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The server's Roots and cultures as the Switchboard shows them, parsed from the open packet. */
final class ContentCatalog {
    record RootRow(String id, Component name, Map<String, String> groups, @Nullable String discoveredBy) {}

    record CultureRow(String id, Component name) {}

    final List<RootRow> roots = new ArrayList<>();
    final List<CultureRow> cultures = new ArrayList<>();
    private final Map<String, Map<String, Component>> groupNames = new LinkedHashMap<>();

    ContentCatalog(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("roots")) {
                JsonObject row = e.getAsJsonObject();
                Map<String, String> groups = new LinkedHashMap<>();
                row.getAsJsonObject("groups").entrySet().forEach(g -> groups.put(g.getKey(), g.getValue().getAsString()));
                roots.add(new RootRow(row.get("id").getAsString(), name(row.getAsJsonObject("name")), groups,
                        row.has("discoveredBy") ? row.get("discoveredBy").getAsString() : null));
            }
            JsonObject names = root.getAsJsonObject("groupNames");
            for (String dimension : WorldKeys.DIMENSIONS) {
                Map<String, Component> byId = new LinkedHashMap<>();
                if (names.has(dimension)) {
                    names.getAsJsonObject(dimension).entrySet()
                            .forEach(n -> byId.put(n.getKey(), name(n.getValue().getAsJsonObject())));
                }
                groupNames.put(dimension, byId);
            }
            for (JsonElement e : root.getAsJsonArray("cultures")) {
                JsonObject row = e.getAsJsonObject();
                cultures.add(new CultureRow(row.get("id").getAsString(), name(row.getAsJsonObject("name"))));
            }
        } catch (RuntimeException ignored) {}
        roots.sort(Comparator.comparing(r -> r.name().getString(), String.CASE_INSENSITIVE_ORDER));
        cultures.sort(Comparator.comparing(c -> c.name().getString(), String.CASE_INSENSITIVE_ORDER));
    }

    Component groupName(String dimension, String id) {
        Component named = groupNames.getOrDefault(dimension, Map.of()).get(id);
        if (named != null) return named;
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return Component.literal(SettingLabels.pretty(path));
    }

    /** Every content key this catalog can show, so presets cover loaded content even when unset. */
    Set<String> keys() {
        Set<String> out = new LinkedHashSet<>();
        for (RootRow root : roots) {
            out.add(WorldKeys.rootState(root.id()));
            out.add(WorldKeys.rootRate(root.id()));
            root.groups().forEach((dimension, id) -> {
                out.add(WorldKeys.groupOn(dimension, id));
                out.add(WorldKeys.groupRate(dimension, id));
            });
        }
        for (CultureRow culture : cultures) {
            out.add(WorldKeys.cultureOn(culture.id()));
            out.add(WorldKeys.cultureRate(culture.id()));
        }
        return out;
    }

    Component label(String key) {
        String subject = WorldKeys.subject(key);
        String dimension = WorldKeys.groupDimension(key);
        if (dimension != null) {
            return Component.translatable("townstead.switchboard.dimension." + dimension)
                    .append(": ").append(groupName(dimension, subject));
        }
        if (key.startsWith(WorldKeys.CULTURE_ON) || key.startsWith(WorldKeys.CULTURE_RATE)) {
            for (CultureRow c : cultures) if (c.id().equals(subject)) return c.name();
        } else {
            for (RootRow r : roots) if (r.id().equals(subject)) return r.name();
        }
        return Component.literal(subject);
    }

    private static Component name(JsonObject name) {
        String text = name.has("text") ? name.get("text").getAsString() : "";
        return name.has("key") ? Component.translatableWithFallback(name.get("key").getAsString(), text)
                : Component.literal(text);
    }
}
