package com.aetherianartificer.townstead.pheno.action.item.types;

import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionType;
import com.aetherianartificer.townstead.pheno.action.item.ItemActions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs the wrapped item action with probability {@code chance} (0-1), else the optional
 * {@code else_action}: the item-domain twin of the entity {@code pheno:chance}.
 *
 * <p>JSON: {@code { "type":"pheno:chance", "chance":0.25,
 * "action":{ "type":"pheno:enchant", "enchantment":"minecraft:unbreaking" } }}</p>
 */
public final class ChanceItemActionType implements ItemActionType {

    public static final String KEY = "pheno:chance";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ItemAction parse(JsonObject json) {
        float chance = GsonHelper.getAsFloat(json, "chance", 1f);
        ItemAction inner = ItemActions.parse(json.get("action"));
        if (inner == null) return null;
        ItemAction otherwise = json.has("else_action") ? ItemActions.parse(json.get("else_action")) : null;
        if (json.has("else_action") && otherwise == null) return null;
        return ctx -> {
            float roll = ctx.holder() != null ? ctx.holder().getRandom().nextFloat()
                    : ThreadLocalRandom.current().nextFloat();
            if (roll < chance) inner.run(ctx);
            else if (otherwise != null) otherwise.run(ctx);
        };
    }
}
