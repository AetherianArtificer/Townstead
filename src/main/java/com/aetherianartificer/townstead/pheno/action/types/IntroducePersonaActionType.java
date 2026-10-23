package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.persona.PersonaService;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.village.TownRange;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Schedules another Persona to arrive in the village of the one who introduces them, some days
 * later, as when a Persona writes to a friend. {@code delay_days} is a number or a
 * {@code [min, max]} range. The player may be the action's focus or its {@code other}.
 * <pre>{ "type": "pheno:introduce_persona", "persona": "townstead:farmer", "delay_days": [1, 2] }</pre>
 */
public final class IntroducePersonaActionType implements ActionType {
    public static final String KEY = "pheno:introduce_persona";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        if (!json.has("persona")) return null;
        ResourceLocation persona = ResourceLocation.tryParse(json.get("persona").getAsString());
        if (persona == null) return null;
        int min = 1, max = 1;
        JsonElement delay = json.get("delay_days");
        if (delay != null && delay.isJsonArray() && delay.getAsJsonArray().size() == 2) {
            min = delay.getAsJsonArray().get(0).getAsInt();
            max = delay.getAsJsonArray().get(1).getAsInt();
        } else if (delay != null) {
            min = max = delay.getAsInt();
        }
        int low = Math.max(0, Math.min(min, max)), high = Math.max(0, Math.max(min, max));
        return ctx -> {
            ServerPlayer player = ctx.other() instanceof ServerPlayer p ? p : ctx.entity() instanceof ServerPlayer p ? p : null;
            var introducer = ctx.entity() instanceof ServerPlayer ? ctx.other() : ctx.entity();
            if (player == null || introducer == null || !(introducer.level() instanceof ServerLevel level)) {
                ctx.fail();
                return;
            }
            var village = TownRange.at(level, introducer.blockPosition()).orElse(null);
            int days = low + (high > low ? level.getRandom().nextInt(high - low + 1) : 0);
            if (village == null || !PersonaService.introduce(player, persona, level, village, days)) ctx.fail();
        };
    }
}
