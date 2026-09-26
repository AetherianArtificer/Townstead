package com.aetherianartificer.townstead.switchboard;

import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.root.Ancestry;
import com.aetherianartificer.townstead.root.AncestryRegistry;
import com.aetherianartificer.townstead.root.Lineage;
import com.aetherianartificer.townstead.root.LineageRegistry;
import com.aetherianartificer.townstead.root.Root;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.RootRules;
import com.aetherianartificer.townstead.root.Species;
import com.aetherianartificer.townstead.root.SpeciesRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The loaded Roots and cultures the Switchboard can configure, sent with the open packet: each Root's
 * group ids, and a translate key plus English text for every group and culture name.
 */
public final class SwitchboardCatalog {
    private SwitchboardCatalog() {}

    public static String build() {
        JsonObject root = new JsonObject();
        JsonArray roots = new JsonArray();
        JsonObject names = new JsonObject();
        for (String dimension : WorldKeys.DIMENSIONS) names.add(dimension, new JsonObject());
        for (Root r : RootRegistry.all()) {
            JsonObject row = new JsonObject();
            row.addProperty("id", r.id().toString());
            row.add("name", name(r.displayName()));
            JsonObject groups = new JsonObject();
            for (Map.Entry<String, String> group : RootRules.groups(r.id()).entrySet()) {
                groups.addProperty(group.getKey(), group.getValue());
                JsonObject dimensionNames = names.getAsJsonObject(group.getKey());
                if (!dimensionNames.has(group.getValue())) {
                    Component label = groupName(group.getKey(), group.getValue());
                    if (label != null) dimensionNames.add(group.getValue(), name(label));
                }
            }
            row.add("groups", groups);
            String discoveredBy = com.aetherianartificer.townstead.root.RootDiscovery.discoveredBy(r.id().toString());
            if (discoveredBy != null) row.addProperty("discoveredBy", discoveredBy);
            roots.add(row);
        }
        root.add("roots", roots);
        root.add("groupNames", names);

        JsonArray cultures = new JsonArray();
        for (ResourceLocation id : Cultures.allIds()) {
            Culture culture = Cultures.get(id);
            if (culture == null) continue;
            JsonObject row = new JsonObject();
            row.addProperty("id", id.toString());
            row.add("name", name(culture.displayName()));
            cultures.add(row);
        }
        root.add("cultures", cultures);
        return root.toString();
    }

    private static @Nullable Component groupName(String dimension, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        return switch (dimension) {
            case "species" -> {
                Species s = SpeciesRegistry.byId(rl);
                yield s == null ? null : s.displayName();
            }
            case "ancestry" -> {
                Ancestry a = AncestryRegistry.byId(rl);
                yield a == null ? null : a.displayName();
            }
            case "lineage" -> {
                Lineage l = LineageRegistry.byId(rl);
                yield l == null ? null : l.displayName();
            }
            default -> null;
        };
    }

    private static JsonObject name(Component component) {
        JsonObject out = new JsonObject();
        if (component.getContents() instanceof TranslatableContents tc) out.addProperty("key", tc.getKey());
        out.addProperty("text", component.getString());
        return out;
    }
}
