package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Combo Skill: the lateral relationship between flat careers. It names two or more
 * professions with a minimum level each; a character whose career history meets every
 * threshold has it — automatically, no point cost, for players and villagers alike (career
 * records persist across job changes, so a cook who spent years as a butcher qualifies).
 * Grants ride the same capability layer as ordinary skill grants.
 */
public record ComboSkillDef(
        ResourceLocation id,
        Component displayName,
        @Nullable Component description,
        @Nullable ResourceLocation icon,
        Map<ResourceLocation, Integer> thresholds,
        List<SkillGrant> grants) {

    public ComboSkillDef {
        thresholds = Map.copyOf(thresholds);
        grants = List.copyOf(grants);
    }

    @Nullable
    static ComboSkillDef parse(ResourceLocation id, JsonObject obj, Map<String, String> lang,
                               Diagnostics diag) {
        if (!obj.has("professions") || !obj.get("professions").isJsonObject()) return null;
        Map<ResourceLocation, Integer> thresholds = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("professions").entrySet()) {
            ResourceLocation career = ResourceLocation.tryParse(entry.getKey());
            if (career == null || !entry.getValue().isJsonPrimitive()) return null;
            int level = entry.getValue().getAsInt();
            if (level < 1) return null;
            thresholds.put(career, level);
        }
        // One profession at level N is just a skill; a combo needs at least two histories.
        if (thresholds.size() < 2) return null;

        Component name = obj.has("display_name")
                ? com.aetherianartificer.townstead.data.DataPackLang.parseComponent(
                        obj.get("display_name"), id.toString(), lang)
                : Component.literal(id.getPath());
        Component description = obj.has("description")
                ? com.aetherianartificer.townstead.data.DataPackLang.parseComponent(
                        obj.get("description"), id + ".description", lang)
                : null;
        ResourceLocation icon = obj.has("icon")
                ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "icon", "")) : null;
        return new ComboSkillDef(id, name, description, icon, thresholds,
                ProfessionDataLoader.parseGrants(obj, diag));
    }
}
