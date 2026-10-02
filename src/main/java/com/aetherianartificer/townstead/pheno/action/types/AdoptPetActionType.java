package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pet.VillagerPets;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

/**
 * Gives the villager side of the action a new pet of {@code entity_type}, beside them. An
 * {@code essential} pet is downed instead of killed.
 * <pre>
 * { "type": "pheno:adopt", "entity_type": "minecraft:wolf", "essential": true }
 * </pre>
 */
public final class AdoptPetActionType implements ActionType {
    public static final String KEY = "pheno:adopt";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation type = DataPackLang.parseId(GsonHelper.getAsString(json, "entity_type", ""));
        if (type == null) return null;
        boolean essential = GsonHelper.getAsBoolean(json, "essential", false);
        return ctx -> {
            VillagerEntityMCA keeper = ctx.entity() instanceof VillagerEntityMCA v ? v
                    : ctx.other() instanceof VillagerEntityMCA v ? v : null;
            var entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(type).orElse(null);
            if (keeper == null || entityType == null || !(keeper.level() instanceof ServerLevel level)
                    || VillagerPets.adoptNew(level, entityType, keeper, essential) == null) {
                ctx.fail();
            }
        };
    }
}
