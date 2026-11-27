package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.root.ability.AbilityToggles;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * Switches one of the focus's toggles on or off, so a held stance can release itself when its
 * resource runs out. {@code power} is the id of the toggle power (a gene id or a skill id).
 *
 * <p>JSON: {@code { "type":"pheno:set_toggle", "power":"my_pack:stance", "enabled":false }}</p>
 */
public final class SetToggleActionType implements ActionType {

    public static final String KEY = "pheno:set_toggle";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation power = DataPackLang.parseId(GsonHelper.getAsString(json, "power", ""));
        if (power == null) return null;
        boolean on = GsonHelper.getAsBoolean(json, "enabled", false);
        return ctx -> {
            if (ctx.level().isClientSide()) return;
            AbilityToggles.set(ctx.entity(), power, on);
            AbilityToggles.syncEntity(ctx.entity());
            if (ctx.entity() instanceof ServerPlayer player) AbilityToggles.syncTo(player);
        };
    }
}
