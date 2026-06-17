package com.aetherianartificer.townstead.pheno.condition.biome;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Parses a biome-condition JSON into a {@link BiomeCondition}. The Apoli subset that has
 * a uniform public API on both branches: {@code in_tag} (biome tag membership),
 * {@code temperature} ({@code min}/{@code max} on the base temperature), and
 * {@code precipitation} ({@code none}/{@code rain}/{@code snow} at the position).
 * {@code "inverted":true} negates. ({@code category} is deprecated and {@code high_humidity}
 * has no uniform public accessor in modern MC, so neither is included; compose several
 * {@code biome} entity conditions with the entity-level {@code and}/{@code or} instead.)
 */
public final class BiomeConditions {

    private BiomeConditions() {}

    @Nullable
    public static BiomeCondition parse(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        BiomeCondition condition = switch (stripNamespace(GsonHelper.getAsString(json, "type", ""))) {
            case "in_tag" -> {
                ResourceLocation tagId = DataPackLang.parseId(GsonHelper.getAsString(json, "tag", ""));
                if (tagId == null) yield null;
                TagKey<Biome> tag = TagKey.create(Registries.BIOME, tagId);
                yield (biome, pos) -> biome.is(tag);
            }
            case "temperature" -> {
                float min = GsonHelper.getAsFloat(json, "min", -Float.MAX_VALUE);
                float max = GsonHelper.getAsFloat(json, "max", Float.MAX_VALUE);
                yield (biome, pos) -> {
                    float t = biome.value().getBaseTemperature();
                    return t >= min && t <= max;
                };
            }
            case "precipitation" -> {
                Biome.Precipitation target = parsePrecipitation(GsonHelper.getAsString(json, "precipitation", "rain"));
                if (target == null) yield null;
                yield (biome, pos) -> biome.value().getPrecipitationAt(pos) == target;
            }
            default -> null;
        };
        if (condition == null) return null;
        return GsonHelper.getAsBoolean(json, "inverted", false) ? condition.negate() : condition;
    }

    @Nullable
    private static Biome.Precipitation parsePrecipitation(String raw) {
        try {
            return Biome.Precipitation.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }
}
