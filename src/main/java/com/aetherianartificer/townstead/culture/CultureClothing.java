package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * How a culture dresses: the first Customs field on the culture record.
 *
 * <p>Every entry is a rate, never a rule. A skin bias weights MCA skins by pattern, a type bias
 * weights any clothing entry a query describes, sets are claimed wholesale, and a palette names
 * the dye colours the village reaches for. A rate may carry spirit axes; the village's share on
 * each named axis multiplies it, so a culture's fashion moves with what its town builds.</p>
 *
 * <pre>{@code
 * "clothing": {
 *   "skins":  [ { "pattern": "townstead_classic:highhold/", "rate": 6 } ],
 *   "types":  [ { "select": { "material": "wool", "layer": "outerwear" }, "rate": 4,
 *                 "spirit": ["pastoral"] } ],
 *   "sets":   [ "townstead_classic:highhold_dress" ],
 *   "palette": [ "#5A3E2B", "#C9B37E" ]
 * }
 * }</pre>
 */
public record CultureClothing(List<SkinBias> skins,
                              List<TypeBias> types,
                              List<ResourceLocation> sets,
                              List<Integer> palette) {

    public static final CultureClothing NONE = new CultureClothing(List.of(), List.of(), List.of(), List.of());

    public CultureClothing {
        skins = skins == null ? List.of() : List.copyOf(skins);
        types = types == null ? List.of() : List.copyOf(types);
        sets = sets == null ? List.of() : List.copyOf(sets);
        palette = palette == null ? List.of() : List.copyOf(palette);
    }

    public boolean isEmpty() {
        return skins.isEmpty() && types.isEmpty() && sets.isEmpty() && palette.isEmpty();
    }

    /** A weighted claim on skins matching a pattern; a trailing {@code *} or {@code /} is a prefix. */
    public record SkinBias(String pattern, float rate, Set<String> spirits) {
        public boolean matches(@Nullable String skinId) {
            if (skinId == null || pattern.isEmpty()) return false;
            if (pattern.endsWith("*")) return skinId.startsWith(pattern.substring(0, pattern.length() - 1));
            if (pattern.endsWith("/")) return skinId.startsWith(pattern);
            return pattern.equals(skinId);
        }
    }

    /** A weighted claim on every entry a query describes. */
    public record TypeBias(ClothingQuery select, float rate, Set<String> spirits) {}

    /**
     * The multiplier this culture puts on one entry, given the village's spirit shares (0..1 per
     * axis). One when the culture has no opinion; rates multiply when several claims apply.
     */
    public double rateFor(ClothingEntry entry, ToDoubleFunction<String> spiritShare) {
        if (entry == null) return 1.0;
        double rate = 1.0;
        for (TypeBias bias : types) {
            if (bias.select().test(entry)) rate *= spirited(bias.rate(), bias.spirits(), spiritShare);
        }
        return rate;
    }

    /** The multiplier this culture puts on one MCA skin. */
    public double rateForSkin(@Nullable String skinId, ToDoubleFunction<String> spiritShare) {
        double rate = 1.0;
        for (SkinBias bias : skins) {
            if (bias.matches(skinId)) rate *= spirited(bias.rate(), bias.spirits(), spiritShare);
        }
        return rate;
    }

    static double spirited(float rate, Set<String> spirits, ToDoubleFunction<String> spiritShare) {
        double value = rate;
        if (spirits != null && spiritShare != null) {
            for (String axis : spirits) value *= 1.0 + Math.max(0.0, spiritShare.applyAsDouble(axis));
        }
        return value;
    }

    /** Reads the {@code clothing} block of a culture; absent or malformed is {@link #NONE}. */
    public static CultureClothing parse(@Nullable JsonObject owner) {
        JsonElement element = owner == null ? null : owner.get("clothing");
        if (element == null || !element.isJsonObject()) return NONE;
        JsonObject json = element.getAsJsonObject();

        List<SkinBias> skins = new ArrayList<>();
        for (JsonObject entry : objects(json.get("skins"))) {
            String pattern = GsonHelper.getAsString(entry, "pattern", "").trim();
            float rate = GsonHelper.getAsFloat(entry, "rate", 1f);
            if (pattern.isEmpty() || rate <= 0f) continue;
            skins.add(new SkinBias(pattern, rate, ClothingQuery.strings(entry.get("spirit"))));
        }

        List<TypeBias> types = new ArrayList<>();
        for (JsonObject entry : objects(json.get("types"))) {
            float rate = GsonHelper.getAsFloat(entry, "rate", 1f);
            if (rate <= 0f) continue;
            types.add(new TypeBias(ClothingQuery.parse(entry.get("select")), rate,
                    ClothingQuery.strings(entry.get("spirit"))));
        }

        List<ResourceLocation> sets = new ArrayList<>();
        for (String raw : ClothingQuery.strings(json.get("sets"))) {
            ResourceLocation id = DataPackLang.parseId(raw);
            if (id != null) sets.add(id);
        }

        List<Integer> palette = new ArrayList<>();
        for (String raw : ClothingQuery.strings(json.get("palette"))) {
            Integer colour = parseColour(raw);
            if (colour != null) palette.add(colour);
        }

        CultureClothing parsed = new CultureClothing(skins, types, sets, palette);
        return parsed.isEmpty() ? NONE : parsed;
    }

    static @Nullable Integer parseColour(String raw) {
        String value = raw.startsWith("#") ? raw.substring(1) : raw;
        if (value.length() != 6) return null;
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<JsonObject> objects(@Nullable JsonElement element) {
        List<JsonObject> out = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return out;
        JsonArray array = element.getAsJsonArray();
        for (JsonElement item : array) {
            if (item != null && item.isJsonObject()) out.add(item.getAsJsonObject());
        }
        return out;
    }

    /** Convenience for callers holding a plain share map. */
    public static ToDoubleFunction<String> shares(Map<String, Double> shares) {
        return axis -> shares == null ? 0.0 : shares.getOrDefault(axis, 0.0);
    }
}
