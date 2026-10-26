package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * True when a building can be built in this modpack: a loaded building type matches, and every
 * block it needs exists. A plain name matches every tier from every mod, so {@code "kitchen"}
 * matches {@code compat/farmersdelight/kitchen_l3}; a name with {@code /} is an exact type id.
 * With a list, when any of them can be built.
 * <pre>{ "type": "pheno:can_build", "building": "kitchen" }</pre>
 * <pre>{ "type": "pheno:can_build", "building": ["tavern", "cafe"] }</pre>
 */
public final class CanBuildConditionType implements ConditionType {
    public static final String KEY = "pheno:can_build";
    private static final Pattern TIER = Pattern.compile("_l\\d+$");

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        JsonElement raw = json.get("building");
        if (raw == null) return null;
        List<String> names = new ArrayList<>();
        if (raw.isJsonArray()) raw.getAsJsonArray().forEach(e -> names.add(e.getAsString().toLowerCase(Locale.ROOT)));
        else names.add(raw.getAsString().toLowerCase(Locale.ROOT));
        if (names.isEmpty() || names.stream().anyMatch(String::isBlank)) return null;
        return new Condition() {
            @Override public boolean test(ConditionContext ctx) { return names.stream().anyMatch(CanBuildConditionType::possible); }
            @Override public boolean supportsSubject() { return true; }
        };
    }

    public static boolean possible(String name) {
        for (Map.Entry<String, BuildingType> entry : BuildingTypes.getInstance().getBuildingTypes().entrySet()) {
            if (matches(entry.getKey(), name) && buildable(entry.getValue())) return true;
        }
        return false;
    }

    static boolean matches(String typeId, String name) {
        if (name.contains("/")) return typeId.equals(name);
        String base = typeId.substring(typeId.lastIndexOf('/') + 1);
        return base.equals(name) || TIER.matcher(base).replaceFirst("").equals(name);
    }

    private static boolean buildable(BuildingType type) {
        for (ResourceLocation group : type.getGroups().keySet()) {
            if (BuiltInRegistries.BLOCK.containsKey(group)) continue;
            TagKey<net.minecraft.world.level.block.Block> tag = TagKey.create(Registries.BLOCK, group);
            if (BuiltInRegistries.BLOCK.getTag(tag).map(set -> set.size() == 0).orElse(true)) return false;
        }
        return true;
    }
}
