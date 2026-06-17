package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Spawns {@code entity} centered on the target block (Apoli's block {@code spawn_entity}).
 *
 * <p>JSON: {@code { "type":"pheno:spawn_entity", "entity":"minecraft:bee" }}</p>
 */
public final class SpawnEntityBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:spawn_entity";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "entity", ""));
        if (id == null) return null;
        return ctx -> {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (type == null) return;
            Entity entity = type.create(ctx.level());
            if (entity == null) return;
            entity.setPos(ctx.pos().getX() + 0.5, ctx.pos().getY(), ctx.pos().getZ() + 0.5);
            ctx.level().addFreshEntity(entity);
        };
    }
}
