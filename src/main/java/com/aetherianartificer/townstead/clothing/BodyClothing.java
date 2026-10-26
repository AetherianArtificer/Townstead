package com.aetherianartificer.townstead.clothing;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * What fits this body, authored on species, ancestries, lineages, and heritages beside hair
 * policy, and composed most general first. Sets of pieces authored for a rig's proportions, and
 * the base skins a non-MCA rig can show. Never taste: what a people likes to wear is culture.
 *
 * <pre>{@code
 * "body_clothing": ["townstead_webster:fitted"]
 * "body_clothing": { "replace": true, "sets": ["townstead_webster:fitted"] }
 * }</pre>
 */
public record BodyClothing(List<ResourceLocation> sets, boolean replace) {

    public static final BodyClothing INHERIT = new BodyClothing(List.of(), false);

    public BodyClothing {
        sets = sets == null ? List.of() : List.copyOf(sets);
    }

    public boolean isEmpty() {
        return sets.isEmpty() && !replace;
    }

    /** Layers a more specific node over this one: sets accumulate unless the node replaces. */
    public BodyClothing mergedWith(@Nullable BodyClothing over) {
        if (over == null || over.isEmpty()) return this;
        if (over.replace) return new BodyClothing(over.sets, false);
        LinkedHashSet<ResourceLocation> merged = new LinkedHashSet<>(sets);
        merged.addAll(over.sets);
        return new BodyClothing(new ArrayList<>(merged), false);
    }

    public static BodyClothing parse(@Nullable JsonObject owner) {
        JsonElement element = owner == null ? null : owner.get("body_clothing");
        if (element == null) return INHERIT;
        boolean replace = false;
        JsonElement setsElement = element;
        if (element.isJsonObject()) {
            JsonObject json = element.getAsJsonObject();
            replace = GsonHelper.getAsBoolean(json, "replace", false);
            setsElement = json.get("sets");
        }
        List<ResourceLocation> sets = new ArrayList<>();
        for (String raw : ClothingQuery.strings(setsElement)) {
            ResourceLocation id = DataPackLang.parseId(raw);
            if (id != null) sets.add(id);
        }
        return sets.isEmpty() && !replace ? INHERIT : new BodyClothing(sets, replace);
    }
}
