package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pet.VillagerPets;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * True when the entity keeps a pet that is loaded nearby, optionally of one type.
 * <pre>
 *   { "type": "pheno:has_pet", "entity_type": "minecraft:wolf", "radius": 32 }
 * </pre>
 */
public final class HasPetConditionType implements com.aetherianartificer.townstead.pheno.condition.ConditionType {
    public static final String KEY = "pheno:has_pet";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        double radius = GsonHelper.getAsDouble(json, "radius", 32);
        ResourceLocation type = json.has("entity_type") ? DataPackLang.parseId(GsonHelper.getAsString(json, "entity_type")) : null;
        return ctx -> ctx.entity() != null && VillagerPets.petsOf(ctx.entity(), radius).stream()
                .anyMatch(pet -> type == null || type.equals(BuiltInRegistries.ENTITY_TYPE.getKey(pet.getType())));
    }
}
