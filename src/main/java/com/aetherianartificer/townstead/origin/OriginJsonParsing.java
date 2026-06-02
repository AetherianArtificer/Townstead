package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared JSON parsing for the four origin loaders: ids, demonyms, and genomes.
 * Tolerant like the calendar loader — bad sub-entries are skipped with a warning
 * rather than failing the whole file.
 */
final class OriginJsonParsing {

    private OriginJsonParsing() {}

    /** Optional namespaced-id field; returns null if absent and warns if malformed. */
    @Nullable
    static ResourceLocation optionalId(JsonObject obj, String key, String context, Logger log) {
        if (!obj.has(key)) return null;
        String raw = GsonHelper.getAsString(obj, key);
        ResourceLocation id = DataPackLang.parseId(raw);
        if (id == null) {
            log.warn("{} — '{}' is not a valid id: {}", context, key, raw);
        }
        return id;
    }

    static List<ResourceLocation> idList(JsonObject obj, String key) {
        if (!obj.has(key)) return List.of();
        JsonArray arr = GsonHelper.getAsJsonArray(obj, key);
        List<ResourceLocation> out = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            ResourceLocation id = DataPackLang.parseId(el.getAsString());
            if (id != null) out.add(id);
        }
        return out;
    }

    @Nullable
    static Demonym demonym(JsonObject obj, String context, Map<String, String> langIndex) {
        if (!obj.has("demonym") || !obj.get("demonym").isJsonObject()) return null;
        JsonObject d = obj.getAsJsonObject("demonym");
        Component singular = DataPackLang.parseComponent(d.get("singular"), context + ".demonym.singular", langIndex);
        Component plural = d.has("plural")
                ? DataPackLang.parseComponent(d.get("plural"), context + ".demonym.plural", langIndex)
                : singular;
        Component adjective = d.has("adjective")
                ? DataPackLang.parseComponent(d.get("adjective"), context + ".demonym.adjective", langIndex)
                : null;
        return new Demonym(singular, plural, adjective);
    }

    @Nullable
    static Component backstory(JsonObject obj, String context, Map<String, String> langIndex) {
        if (!obj.has("backstory")) return null;
        return DataPackLang.parseComponent(obj.get("backstory"), context + ".backstory", langIndex);
    }

    /**
     * Parse the unified {@code genes} array: one list of gene references, each a
     * bare id string or {@code { "gene": id, "occurrence": x }}. Body metrics,
     * appearance, diet, life cycle and traits are all just gene ids here.
     */
    static Genome genes(JsonObject obj, String context, Logger log) {
        if (!obj.has("genes") || !obj.get("genes").isJsonArray()) return Genome.EMPTY;
        List<com.aetherianartificer.townstead.origin.gene.InheritedGene> genes = new ArrayList<>();
        for (JsonElement t : obj.getAsJsonArray("genes")) {
            if (t.isJsonObject()) {
                JsonObject go = t.getAsJsonObject();
                ResourceLocation gene = DataPackLang.parseId(GsonHelper.getAsString(go, "gene", ""));
                if (gene == null) {
                    log.warn("{} — gene entry has no valid 'gene' id, skipping", context);
                    continue;
                }
                float occurrence = GsonHelper.getAsFloat(go, "occurrence", 1.0f);
                genes.add(new com.aetherianartificer.townstead.origin.gene.InheritedGene(gene, occurrence));
            } else if (t.isJsonPrimitive()) {
                ResourceLocation gene = DataPackLang.parseId(t.getAsString());
                if (gene != null) {
                    genes.add(com.aetherianartificer.townstead.origin.gene.InheritedGene.of(gene));
                }
            }
        }
        return new Genome(genes);
    }

    /**
     * Parse the optional {@code spawn_bias} block: a {@code default} weight plus
     * {@code biomes}, {@code biome_tags} and {@code dimensions} id→weight maps.
     * Tag keys may be written with or without a leading {@code #}.
     */
    static SpawnBias spawnBias(JsonObject obj, String context, Logger log) {
        if (!obj.has("spawn_bias") || !obj.get("spawn_bias").isJsonObject()) return SpawnBias.EMPTY;
        JsonObject sb = obj.getAsJsonObject("spawn_bias");
        Float def = sb.has("default") ? GsonHelper.getAsFloat(sb, "default") : null;
        return new SpawnBias(def,
                weightMap(sb, "biomes", false, context, log),
                weightMap(sb, "biome_tags", true, context, log),
                weightMap(sb, "dimensions", false, context, log));
    }

    private static Map<ResourceLocation, Float> weightMap(JsonObject parent, String key, boolean stripHash,
                                                          String context, Logger log) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) return Map.of();
        Map<ResourceLocation, Float> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : parent.getAsJsonObject(key).entrySet()) {
            String raw = e.getKey();
            if (stripHash && raw.startsWith("#")) raw = raw.substring(1);
            ResourceLocation id = DataPackLang.parseId(raw);
            if (id == null) {
                log.warn("{} — spawn_bias.{} has invalid id '{}', skipping", context, key, e.getKey());
                continue;
            }
            try {
                out.put(id, e.getValue().getAsFloat());
            } catch (RuntimeException ex) {
                log.warn("{} — spawn_bias.{} '{}' is not a number, skipping", context, key, e.getKey());
            }
        }
        return out;
    }
}
