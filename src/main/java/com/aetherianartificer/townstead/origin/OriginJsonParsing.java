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

    /** Parse a {@code genome}/{@code genome_overrides} block ({@code genes} map + {@code tags} list). */
    static Genome genome(JsonObject obj, String key, String context, Logger log) {
        if (!obj.has(key) || !obj.get(key).isJsonObject()) return Genome.EMPTY;
        JsonObject g = obj.getAsJsonObject(key);

        Map<String, GeneRange> genes = new LinkedHashMap<>();
        if (g.has("genes") && g.get("genes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : g.getAsJsonObject("genes").entrySet()) {
                String normKey = OriginGenes.normalizeKey(e.getKey());
                if (!OriginGenes.isKnown(normKey)) {
                    log.warn("{} — unknown gene '{}', skipping", context, e.getKey());
                    continue;
                }
                if (!e.getValue().isJsonObject()) {
                    log.warn("{} — gene '{}' must be an object with min/max, skipping", context, e.getKey());
                    continue;
                }
                JsonObject range = e.getValue().getAsJsonObject();
                float min = GsonHelper.getAsFloat(range, "min", 0f);
                float max = GsonHelper.getAsFloat(range, "max", 1f);
                genes.put(normKey, new GeneRange(min, max));
            }
        }

        List<com.aetherianartificer.townstead.origin.gene.InheritedGene> inheritedGenes = new ArrayList<>();
        if (g.has("inherited_genes") && g.get("inherited_genes").isJsonArray()) {
            for (JsonElement t : g.getAsJsonArray("inherited_genes")) {
                if (t.isJsonObject()) {
                    JsonObject go = t.getAsJsonObject();
                    ResourceLocation gene = DataPackLang.parseId(GsonHelper.getAsString(go, "gene", ""));
                    if (gene == null) continue;
                    float occurrence = GsonHelper.getAsFloat(go, "occurrence", 1.0f);
                    inheritedGenes.add(new com.aetherianartificer.townstead.origin.gene.InheritedGene(gene, occurrence));
                } else if (t.isJsonPrimitive()) {
                    ResourceLocation gene = DataPackLang.parseId(t.getAsString());
                    if (gene != null) {
                        inheritedGenes.add(com.aetherianartificer.townstead.origin.gene.InheritedGene.of(gene));
                    }
                }
            }
        }

        return new Genome(genes, inheritedGenes);
    }
}
