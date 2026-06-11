package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;

/**
 * True when the entity is a given {@code entity_type} (id) or in a {@code tag} of entity
 * types (Apoli's {@code entity_type} / {@code in_tag}). Registered under both keys.
 */
public final class EntityTypeConditionType implements ConditionType {

    private final String key;

    public EntityTypeConditionType(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public Condition parse(JsonObject json) {
        if (json.has("tag")) {
            ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "tag", ""));
            if (id == null) return null;
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, id);
            return ctx -> ctx.entity().getType().builtInRegistryHolder().is(tag);
        }
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "entity_type", ""));
        if (id == null) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        return ctx -> ctx.entity().getType() == type;
    }
}
