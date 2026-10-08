package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.persona.PersonaService;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;

/**
 * The villager tells the player their family name. A Persona that keeps it back
 * ({@code "family_name": "told"} in persona.json) shows it to this player from now on, on the
 * nameplate and in the dialogue screen. Either may be the action's focus.
 * <pre>{ "type": "pheno:share_family_name" }</pre>
 */
public final class ShareFamilyNameActionType implements ActionType {
    public static final String KEY = "pheno:share_family_name";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            VillagerEntityMCA villager = ctx.entity() instanceof VillagerEntityMCA v ? v : ctx.other() instanceof VillagerEntityMCA v ? v : null;
            ServerPlayer player = ctx.other() instanceof ServerPlayer p ? p : ctx.entity() instanceof ServerPlayer p ? p : null;
            if (villager == null || player == null) {
                ctx.fail();
                return;
            }
            PersonaService.tellFamilyName(villager, player);
        };
    }
}
