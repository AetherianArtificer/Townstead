package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Spawns an entity at the actor's position (Apoli's entity {@code spawn_entity}).
 *
 * <p>JSON: {@code { "type":"pheno:spawn_entity", "entity":"minecraft:bat" }}</p>
 */
public final class SpawnEntityActionType implements ActionType {

    public static final String KEY = "pheno:spawn_entity";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "entity", ""));
        if (id == null) return null;
        return ctx -> {
            if (!(ctx.entity().level() instanceof ServerLevel level)) return;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (type == null) return;
            Entity spawned = type.create(level);
            if (spawned == null) return;
            spawned.moveTo(ctx.entity().getX(), ctx.entity().getY(), ctx.entity().getZ(),
                    ctx.entity().getYRot(), ctx.entity().getXRot());
            level.addFreshEntity(spawned);
        };
    }
}
