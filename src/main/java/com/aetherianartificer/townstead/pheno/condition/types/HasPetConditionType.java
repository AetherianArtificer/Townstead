package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pet.VillagerPets;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * True when the entity keeps a pet that is loaded nearby, optionally of one type, and optionally
 * only one that is downed ({@code "downed": true}) or up ({@code "downed": false}). With {@code "named"}, only one that has (or lacks) a name tag.
 * <pre>
 *   { "type": "pheno:has_pet", "entity_type": "minecraft:wolf", "radius": 32 }
 *   { "type": "pheno:has_pet", "entity_type": "minecraft:wolf", "downed": true }
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
        Boolean downed = json.has("downed") ? GsonHelper.getAsBoolean(json, "downed") : null;
        Boolean named = json.has("named") ? GsonHelper.getAsBoolean(json, "named") : null;
        return ctx -> ctx.entity() != null && VillagerPets.petsOf(ctx.entity(), radius).stream()
                .anyMatch(pet -> (type == null || type.equals(BuiltInRegistries.ENTITY_TYPE.getKey(pet.getType())))
                        && (downed == null || downed == com.aetherianartificer.townstead.pet.PetDowned.isDowned(pet))
                        && (named == null || named == pet.hasCustomName()));
    }
}
