package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Turns the entity into a zombie villager (Apugli's {@code zombify_villager}). Apugli only handled
 * vanilla villagers; this works on any {@code Mob}, so an MCA villager zombifies too (it becomes an
 * MCA zombie villager and keeps who they were). A villager immune to infection is not turned.
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
