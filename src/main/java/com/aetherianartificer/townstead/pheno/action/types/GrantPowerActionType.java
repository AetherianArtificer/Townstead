package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.power.TimedPowers;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Gives the focus a power for {@code duration} ticks, written inline, so a skill can leave something on someone else:
 * {@code { "type":"pheno:grant_power", "id":"my_pack:staunched", "duration":300,
 * "power":{ "type":"pheno:modifier", "target":"my_mod:injury_chance", "operation":"set", "value":0 } }}.
 * Granting the same {@code id} again renews it; {@code duration} 0 takes it away. Without an {@code id}, the
 * power is named by its own JSON, so the same power still renews itself.
 */
public final class GrantPowerActionType implements ActionType {

    public static final String KEY = "pheno:grant_power";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        int duration = Math.max(0, GsonHelper.getAsInt(json, "duration", 0));
        ResourceLocation id = json.has("id") ? DataPackLang.parseId(GsonHelper.getAsString(json, "id", "")) : null;
        if (json.has("id") && id == null) return null;
        com.aetherianartificer.townstead.pheno.power.PowerComponent component = null;
        if (json.has("power") && json.get("power").isJsonObject()) {
            component = com.aetherianartificer.townstead.root.gene.GeneTypes.get(GsonHelper.getAsString(json.getAsJsonObject("power"), "type", "")).map(type -> (com.aetherianartificer.townstead.pheno.power.PowerComponent) type.parse(json.getAsJsonObject("power"))).orElse(null);
            if (component == null) return null;
            if (id == null) id = ResourceLocation.tryParse(
                    "pheno:granted/" + Integer.toHexString(json.get("power").toString().hashCode()));
        }
        if (id == null || (component == null && duration > 0)) return null;
        ResourceLocation grantId = id;
        com.aetherianartificer.townstead.pheno.power.PowerComponent granted = component;
        return ctx -> {
            if (ctx.entity() != null && !ctx.level().isClientSide()) {
                TimedPowers.grant(ctx.entity(), grantId, granted, duration);
            }
        };
    }
}
