package com.aetherianartificer.townstead.clothing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A question over every loaded clothing entry: "any warm wool body piece", "any hat a scholar
 * would wear". Sets, culture biases, and policies all ask it in the same shape, so a new mod's
 * garments join every set that describes them without anyone editing the set.
 *
 * <p>Every field is optional. A field given is a constraint; a list is any-of; the empty query
 * matches everything.</p>
 */
public record ClothingQuery(@Nullable ClothingLayer layer,
                            @Nullable ClothingChannel slot,
                            Set<String> materials,
                            Set<String> spirits,
                            Set<String> occasions,
                            Thermal thermal,
                            Set<String> sources) {

    public enum Thermal { ANY, WARM, COOL }

    public static final ClothingQuery ANY = new ClothingQuery(null, null, Set.of(), Set.of(), Set.of(),
            Thermal.ANY, Set.of());

    public ClothingQuery {
        materials = materials == null ? Set.of() : Set.copyOf(materials);
        spirits = spirits == null ? Set.of() : Set.copyOf(spirits);
        occasions = occasions == null ? Set.of() : Set.copyOf(occasions);
        sources = sources == null ? Set.of() : Set.copyOf(sources);
        thermal = thermal == null ? Thermal.ANY : thermal;
    }

    public boolean isEmpty() {
        return layer == null && slot == null && materials.isEmpty() && spirits.isEmpty()
                && occasions.isEmpty() && thermal == Thermal.ANY && sources.isEmpty();
    }

    public boolean test(ClothingEntry entry) {
        if (entry == null) return false;
        if (layer != null && entry.layer() != layer) return false;
        if (slot != null && entry.slot() != slot && entry.slot() != ClothingChannel.ALL) return false;
        if (!materials.isEmpty() && !intersects(materials, entry.materials())) return false;
        if (!spirits.isEmpty() && !intersects(spirits, entry.spirits())) return false;
        if (!occasions.isEmpty() && !intersects(occasions, entry.occasions())) return false;
        if (thermal == Thermal.WARM && !entry.isWarm()) return false;
        if (thermal == Thermal.COOL && !entry.isCool()) return false;
        if (!sources.isEmpty() && !sources.contains(entry.source())) return false;
        return true;
    }

    private static boolean intersects(Set<String> wanted, Set<String> have) {
        for (String value : wanted) {
            if (have.contains(value)) return true;
        }
        return false;
    }

    /** Reads a query object; a null or non-object element is the empty query. */
    public static ClothingQuery parse(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return ANY;
        JsonObject json = element.getAsJsonObject();
        ClothingLayer layer = ClothingLayer.parse(GsonHelper.getAsString(json, "layer", null));
        ClothingChannel slot = ClothingChannel.parse(GsonHelper.getAsString(json, "slot", null));
        Thermal thermal = Thermal.ANY;
        String thermalRaw = GsonHelper.getAsString(json, "thermal", "any");
        if ("warm".equalsIgnoreCase(thermalRaw)) thermal = Thermal.WARM;
        else if ("cool".equalsIgnoreCase(thermalRaw)) thermal = Thermal.COOL;
        return new ClothingQuery(layer, slot, strings(json.get("material")), strings(json.get("spirit")),
                strings(json.get("occasion")), thermal, strings(json.get("source")));
    }

    /** A string or an array of strings, trimmed, without empties. */
    public static Set<String> strings(@Nullable JsonElement element) {
        Set<String> out = new LinkedHashSet<>();
        if (element == null) return out;
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            if (!value.isEmpty()) out.add(value);
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                if (item != null && item.isJsonPrimitive()) {
                    String value = item.getAsString().trim();
                    if (!value.isEmpty()) out.add(value);
                }
            }
        }
        return out;
    }
}
