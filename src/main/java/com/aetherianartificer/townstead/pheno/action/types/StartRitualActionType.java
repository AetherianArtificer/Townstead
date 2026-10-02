package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.ritual.RitualService;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * Starts a ritual with the action's entity as the candidate. Fails when it cannot begin; a player
 * candidate is told why.
 * <pre>
 * { "type": "pheno:start_ritual", "ritual": "townstead:hunter_oath" }
 * </pre>
 */
public final class StartRitualActionType implements ActionType {
    public static final String KEY = "pheno:start_ritual";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation ritual = DataPackLang.parseId(GsonHelper.getAsString(json, "ritual", ""));
        if (ritual == null) return null;
        return ctx -> {
            if (!(ctx.entity().level() instanceof ServerLevel level)) return;
            String refused = RitualService.start(level, ritual, ctx.entity());
            if (refused == null) return;
            if (ctx.entity() instanceof ServerPlayer player) player.displayClientMessage(Component.translatable(refused), false);
            ctx.fail();
        };
    }
}
