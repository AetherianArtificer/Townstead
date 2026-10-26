package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.delivery.Deliveries;
import com.google.gson.JsonObject;

/**
 * Delivers the item in focus to the entity, from {@code other}: a letter, a parcel or a quest
 * hand-in, read by whichever system owns that kind of item. When no system takes it, the action
 * fails and {@code fallback} runs instead.
 *
 * <pre>
 * { "type": "pheno:deliver", "fallback": { "type": "pheno:play_sound", "sound": "minecraft:entity.villager.no" } }
 * </pre>
 */
public final class DeliverActionType implements ActionType {
    public static final String KEY = "pheno:deliver";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Action fallback = json.has("fallback") ? Actions.parse(json.get("fallback")) : null;
        return ctx -> {
            if (Deliveries.deliver(ctx.other(), ctx.entity(), ctx.item())) return;
            ctx.fail();
            if (fallback != null) fallback.run(ctx);
        };
    }
}
