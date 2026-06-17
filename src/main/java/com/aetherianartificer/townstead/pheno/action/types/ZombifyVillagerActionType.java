package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Turns the entity into a zombie villager (Apugli's {@code zombify_villager}). Apugli only handled
 * vanilla villagers; this works on any {@code Mob}, so an MCA villager zombifies too (it becomes a
 * vanilla zombie villager, since that is the only zombie-villager type that exists).
 *
 * <p>JSON: {@code { "type":"pheno:zombify_villager" }}</p>
 */
public final class ZombifyVillagerActionType implements ActionType {

    public static final String KEY = "pheno:zombify_villager";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            if (ctx.entity().level().isClientSide) return;
            if (ctx.entity() instanceof Mob mob) {
                mob.convertTo(EntityType.ZOMBIE_VILLAGER, false);
            }
        };
    }
}
