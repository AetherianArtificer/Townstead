package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One independently addressable contribution whose directory owns its Profession or Path. */
public final class ProfessionTradeDocument {

    public static final String SCHEMA = "townstead:profession_trade/v1";
    private static final Set<String> METADATA = Set.of("schema", "replace", "mods");

    private ProfessionTradeDocument() {
    }

    /**
     * Distinct files merge by merchant level. A contribution with {@code replace: true} first
     * removes the existing offers for its own root-Profession or Path target.
     */
    public static void apply(JsonObject profession, JsonObject document) {
        apply(profession, document, null);
    }

    /** The enclosing directory supplies {@code path}; root trade directories pass {@code null}. */
    public static void apply(JsonObject profession, JsonObject document, String path) {
        TownsteadSchema.validateRequired(document, SCHEMA);
        if (document.has("path")) {
            throw new IllegalArgumentException(
                    "'path' is derived from the enclosing Path directory; remove it");
        }
        if (path != null && !declaredPaths(profession).contains(path)) {
            throw new IllegalArgumentException("Unknown Profession Path '" + path + "'");
        }
        boolean replace = GsonHelper.getAsBoolean(document, "replace", false);

        JsonObject contribution = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : document.entrySet()) {
            if (METADATA.contains(entry.getKey())) continue;
            int level = merchantLevel(entry.getKey());
            if (!entry.getValue().isJsonArray()) {
                throw new IllegalArgumentException("Merchant level '" + level + "' must be an array");
            }
            JsonArray offers = new JsonArray();
            for (JsonElement offer : entry.getValue().getAsJsonArray()) {
                if (!offer.isJsonObject()) {
                    throw new IllegalArgumentException("Every merchant offer must be an object");
                }
                JsonObject normalized = offer.getAsJsonObject().deepCopy();
                if (normalized.has("path")) {
                    throw new IllegalArgumentException("Offer 'path' belongs on the trade file");
                }
                if (path != null) normalized.addProperty("path", path);
                offers.add(normalized);
            }
            contribution.add(entry.getKey(), offers);
        }

        JsonObject merged = profession.has("trades") && profession.get("trades").isJsonObject()
                ? profession.getAsJsonObject("trades").deepCopy() : new JsonObject();
        if (replace) removeTarget(merged, path);
        for (Map.Entry<String, JsonElement> level : contribution.entrySet()) {
            JsonArray offers = merged.has(level.getKey()) && merged.get(level.getKey()).isJsonArray()
                    ? merged.getAsJsonArray(level.getKey()) : new JsonArray();
            for (JsonElement offer : level.getValue().getAsJsonArray()) offers.add(offer.deepCopy());
            merged.add(level.getKey(), offers);
        }
        profession.add("trades", merged);
    }

    private static int merchantLevel(String key) {
        int level;
        try {
            level = Integer.parseInt(key);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Unknown field '" + key + "'");
        }
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("Merchant level must be between 1 and 5");
        }
        return level;
    }

    private static void removeTarget(JsonObject trades, String path) {
        for (Map.Entry<String, JsonElement> level : trades.entrySet()) {
            if (!level.getValue().isJsonArray()) continue;
            JsonArray kept = new JsonArray();
            for (JsonElement offer : level.getValue().getAsJsonArray()) {
                if (!offer.isJsonObject()) continue;
                JsonObject object = offer.getAsJsonObject();
                String offerPath = object.has("path") && object.get("path").isJsonPrimitive()
                        ? object.get("path").getAsString() : null;
                if (!java.util.Objects.equals(path, offerPath)) kept.add(object.deepCopy());
            }
            level.setValue(kept);
        }
    }

    private static Set<String> declaredPaths(JsonObject profession) {
        Set<String> paths = new LinkedHashSet<>();
        if (!profession.has("paths") || !profession.get("paths").isJsonArray()) return paths;
        for (JsonElement element : profession.getAsJsonArray("paths")) {
            if (!element.isJsonObject()) continue;
            JsonObject path = element.getAsJsonObject();
            if (path.has("id") && path.get("id").isJsonPrimitive()) {
                paths.add(path.get("id").getAsString());
            }
        }
        return paths;
    }
}
