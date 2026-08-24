package com.aetherianartificer.townstead.pheno.resource;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A resource id that is either literal or derived from the block being acted on.
 * Transforms keep optional-mod naming conventions in data without teaching Pheno
 * what a carcass, machine tier, crop variety, or any other content noun means.
 */
@FunctionalInterface
public interface ResourceResolver {

    @Nullable ResourceLocation resolve(BlockActionContext context);

    static @Nullable ResourceResolver parse(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            ResourceLocation literal = DataPackLang.parseId(element.getAsString());
            return literal == null ? null : context -> literal;
        }
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        if (!json.has("from") || !json.get("from").isJsonPrimitive()
                || !"block".equals(json.get("from").getAsString())) return null;

        Map<ResourceLocation, ResourceLocation> mappings = new LinkedHashMap<>();
        if (json.has("map")) {
            if (!json.get("map").isJsonObject()) return null;
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("map").entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) return null;
                ResourceLocation source = DataPackLang.parseId(entry.getKey());
                ResourceLocation target = DataPackLang.parseId(entry.getValue().getAsString());
                if (source == null || target == null) return null;
                mappings.put(source, target);
            }
        }

        Pattern pattern = null;
        String replacement = null;
        if (json.has("pattern") || json.has("replace")) {
            if (!json.has("pattern") || !json.has("replace")) return null;
            try {
                pattern = Pattern.compile(json.get("pattern").getAsString());
                replacement = json.get("replace").getAsString();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (json.has("fallback") && !json.get("fallback").isJsonPrimitive()) return null;
        ResourceLocation fallback = json.has("fallback")
                ? DataPackLang.parseId(json.get("fallback").getAsString()) : null;
        if (mappings.isEmpty() && pattern == null && fallback == null) return null;

        Map<ResourceLocation, ResourceLocation> exact = Map.copyOf(mappings);
        Pattern transform = pattern;
        String replace = replacement;
        ResourceLocation otherwise = fallback;
        return context -> {
            ResourceLocation source = BuiltInRegistries.BLOCK.getKey(
                    context.level().getBlockState(context.pos()).getBlock());
            ResourceLocation mapped = exact.get(source);
            if (mapped != null) return mapped;
            if (transform != null) {
                var matcher = transform.matcher(source.toString());
                if (matcher.matches()) return DataPackLang.parseId(matcher.replaceFirst(replace));
            }
            return otherwise;
        };
    }
}
