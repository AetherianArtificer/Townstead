package com.aetherianartificer.townstead.work;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One data-authored mapping from authoritative output-stack data to a stable appraisal. */
public record OutputAppraisalDef(ResourceLocation id, Set<ResourceLocation> items,
                                 Set<ResourceLocation> tags, List<String> path,
                                 List<Tier> tiers) {
    public static final String SCHEMA = "townstead:output_appraisal/v1";

    public record Tier(double min, double max, int quality, String label, boolean orderWorthy) {
        boolean matches(double value) { return value >= min && value <= max; }
    }

    static @Nullable OutputAppraisalDef parse(ResourceLocation id, JsonObject json) {
        if (id == null || json == null || !json.has("path") || !json.has("tiers")) return null;
        List<String> path = strings(json.get("path"), false);
        // V1 intentionally reads one exact custom-data key. Dotted keys remain literal (Brewery
        // uses "brewery.beer_quality"); structural traversal belongs in a later schema version.
        if (path == null || path.size() != 1) return null;

        Set<ResourceLocation> items = new LinkedHashSet<>();
        Set<ResourceLocation> tags = new LinkedHashSet<>();
        if (json.has("items")) {
            List<String> selectors = strings(json.get("items"), true);
            if (selectors == null || selectors.isEmpty()) return null;
            for (String selector : selectors) {
                boolean tag = selector.startsWith("#");
                ResourceLocation parsed = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
                if (parsed == null) return null;
                (tag ? tags : items).add(parsed);
            }
        }

        if (!json.get("tiers").isJsonArray() || json.getAsJsonArray("tiers").isEmpty()) return null;
        List<Tier> tiers = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("tiers")) {
            if (!element.isJsonObject()) return null;
            JsonObject tier = element.getAsJsonObject();
            double min;
            double max;
            if (tier.has("value")) {
                if (tier.has("min") || tier.has("max")) return null;
                if (!number(tier.get("value"))) return null;
                min = max = tier.get("value").getAsDouble();
            } else {
                if (!number(tier.get("min")) || !number(tier.get("max"))) return null;
                min = tier.get("min").getAsDouble();
                max = tier.get("max").getAsDouble();
            }
            if (!Double.isFinite(min) || !Double.isFinite(max) || min > max
                    || !tier.has("quality") || !number(tier.get("quality"))) return null;
            double rawQuality = tier.get("quality").getAsDouble();
            int quality = (int) rawQuality;
            if (rawQuality != quality || quality < 1 || quality > 100) return null;
            if (!tier.has("label") || !tier.get("label").isJsonPrimitive()
                    || !tier.getAsJsonPrimitive("label").isString()
                    || !tier.get("label").getAsString().matches("[a-z0-9_./-]+")) return null;
            if (tier.has("order_worthy") && (!tier.get("order_worthy").isJsonPrimitive()
                    || !tier.getAsJsonPrimitive("order_worthy").isBoolean())) return null;
            boolean worthy = !tier.has("order_worthy") || tier.get("order_worthy").getAsBoolean();
            for (Tier existing : tiers) {
                if (min <= existing.max() && max >= existing.min()) return null;
            }
            tiers.add(new Tier(min, max, quality, tier.get("label").getAsString(), worthy));
        }
        return new OutputAppraisalDef(id, Set.copyOf(items), Set.copyOf(tags), List.copyOf(path),
                List.copyOf(tiers));
    }

    public @Nullable OutputAppraisal.Appraisal appraise(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !matchesItem(stack)) return null;
        Double value = numericValue(customData(stack), path);
        return value == null ? null : appraise(value);
    }

    public @Nullable OutputAppraisal.Appraisal appraise(double value) {
        for (Tier tier : tiers) {
            if (tier.matches(value)) {
                return new OutputAppraisal.Appraisal(tier.quality(), tier.label(), tier.orderWorthy(), id);
            }
        }
        return null;
    }

    private boolean matchesItem(ItemStack stack) {
        if (items.isEmpty() && tags.isEmpty()) return true;
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (items.contains(item)) return true;
        for (ResourceLocation tag : tags) {
            if (stack.is(TagKey.create(Registries.ITEM, tag))) return true;
        }
        return false;
    }

    static @Nullable Double numericValue(@Nullable CompoundTag root, List<String> path) {
        if (root == null || path == null || path.size() != 1 || !root.contains(path.get(0))) return null;
        return (double) root.getInt(path.get(0));
    }

    private static @Nullable CompoundTag customData(ItemStack stack) {
        //? if >=1.21 {
        net.minecraft.world.item.component.CustomData data =
                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
        //?} else {
        /*return stack.getTag();
        *///?}
    }

    private static boolean number(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber();
    }

    private static @Nullable List<String> strings(JsonElement element, boolean scalarAllowed) {
        if (element == null) return null;
        List<String> out = new ArrayList<>();
        if (scalarAllowed && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            out.add(element.getAsString());
            return out;
        }
        if (!element.isJsonArray()) return null;
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonPrimitive() || !child.getAsJsonPrimitive().isString()
                    || child.getAsString().isBlank()) return null;
            out.add(child.getAsString());
        }
        return out;
    }
}
