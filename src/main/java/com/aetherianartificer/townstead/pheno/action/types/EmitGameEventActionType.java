package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Emits a vanilla game event from the entity, so sculk sensors and other listeners react to it
 * (Apoli's {@code emit_game_event}).
 *
 * <p>JSON: {@code { "type":"pheno:emit_game_event", "event":"minecraft:eat" }}</p>
 */
public final class EmitGameEventActionType implements ActionType {

    public static final String KEY = "pheno:emit_game_event";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "event", ""));
        if (id == null) return null;
        return ctx -> {
            if (ctx.entity().level().isClientSide) return;
            //? if neoforge {
            BuiltInRegistries.GAME_EVENT.getHolder(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.GAME_EVENT, id)).ifPresent(h -> ctx.entity().gameEvent(h));
            //?} else {
            /*net.minecraft.world.level.gameevent.GameEvent event = BuiltInRegistries.GAME_EVENT.get(id);
            if (event != null) ctx.entity().gameEvent(event);
            *///?}
        };
    }
}
