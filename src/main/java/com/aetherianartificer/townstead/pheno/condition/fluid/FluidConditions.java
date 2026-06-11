package com.aetherianartificer.townstead.pheno.condition.fluid;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a fluid-condition JSON into a {@link FluidCondition}: {@code empty} (no fluid),
 * {@code in_tag} (fluid tag), {@code still} (a source rather than flowing), plus
 * {@code and}/{@code or}/{@code constant}. {@code "inverted":true} negates. All over the
 * version-uniform {@code FluidState} API.
 */
public final class FluidConditions {

    private FluidConditions() {}

    @Nullable
    public static FluidCondition parse(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        FluidCondition condition = build(stripNamespace(GsonHelper.getAsString(json, "type", "")), json);
        if (condition == null) return null;
        return GsonHelper.getAsBoolean(json, "inverted", false) ? condition.negate() : condition;
    }

    @Nullable
    private static FluidCondition build(String type, JsonObject json) {
        switch (type) {
            case "empty":
                return FluidState::isEmpty;
            case "still":
                return FluidState::isSource;
            case "in_tag": {
                ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "tag", ""));
                if (id == null) return null;
                TagKey<Fluid> tag = TagKey.create(Registries.FLUID, id);
                return state -> state.is(tag);
            }
            case "and": {
                List<FluidCondition> all = parseList(json);
                if (all == null) return null;
                return state -> all.stream().allMatch(c -> c.test(state));
            }
            case "or": {
                List<FluidCondition> any = parseList(json);
                if (any == null) return null;
                return state -> any.stream().anyMatch(c -> c.test(state));
            }
            case "constant":
                boolean value = GsonHelper.getAsBoolean(json, "value", true);
                return state -> value;
            default:
                return null;
        }
    }

    @Nullable
    private static List<FluidCondition> parseList(JsonObject json) {
        if (!json.has("conditions") || !json.get("conditions").isJsonArray()) return null;
        List<FluidCondition> out = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("conditions")) {
            FluidCondition condition = parse(element);
            if (condition == null) return null;
            out.add(condition);
        }
        return out.isEmpty() ? null : out;
    }

    private static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }
}
