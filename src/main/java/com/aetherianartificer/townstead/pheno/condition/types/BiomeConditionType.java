package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.biome.Biome;

/**
 * True when the entity stands in a given biome or biome tag, or when a nested
 * biome {@code condition} (in_tag / temperature / precipitation) holds for the biome
 * here.
 *
 * <p>JSON: {@code { "type":"pheno:biome", "biome":"minecraft:desert" }},
 * {@code { ..., "biome_tag":"minecraft:is_nether" }}, or {@code { ..., "condition":{
 * "type":"pheno:temperature", "min":1.0 } }}</p>
 */
public final class BiomeConditionType implements ConditionType {

    public static final String KEY = "pheno:biome";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        if (json.has("condition")) {
            com.aetherianartificer.townstead.pheno.condition.biome.BiomeCondition biome =
                    com.aetherianartificer.townstead.pheno.condition.biome.BiomeConditions.parse(json.get("condition"));
            if (biome == null) return null;
            return ctx -> biome.test(ctx.level().getBiome(ctx.pos()), ctx.pos());
        }
        if (json.has("biome_tag")) {
            ResourceLocation tagId = DataPackLang.parseId(GsonHelper.getAsString(json, "biome_tag", ""));
            if (tagId == null) return null;
            TagKey<Biome> tag = TagKey.create(Registries.BIOME, tagId);
            return ctx -> ctx.level().getBiome(ctx.pos()).is(tag);
        }
        ResourceLocation biomeId = DataPackLang.parseId(GsonHelper.getAsString(json, "biome", ""));
        if (biomeId == null) return null;
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, biomeId);
        return ctx -> ctx.level().getBiome(ctx.pos()).is(key);
    }
}
