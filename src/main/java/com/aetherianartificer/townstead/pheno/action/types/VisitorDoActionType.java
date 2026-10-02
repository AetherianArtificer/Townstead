package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.visitor.Visitors;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * Runs an action on the nearest visitor with {@code role}, with the one who ran it as the other
 * entity. Fails when no such visitor is near.
 * <pre>
 * { "type": "pheno:visitor_action", "role": "vampire_couple_thrall", "action": { "type": "pheno:release_thrall" } }
 * </pre>
 */
public final class VisitorDoActionType implements ActionType {
    public static final String KEY = "pheno:visitor_action";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        String role = GsonHelper.getAsString(json, "role", "");
        double radius = GsonHelper.getAsDouble(json, "radius", 32);
        Action action = Actions.parse(json.get("action"));
        if (role.isBlank() || action == null) return null;
        return ctx -> {
            LivingEntity around = ctx.other() != null ? ctx.other() : ctx.entity();
            VillagerEntityMCA visitor = around == null ? null : Visitors.near(around, role, radius);
            if (visitor == null) {
                ctx.fail();
                return;
            }
            action.run(new ActionContext(visitor, ctx.entity()));
        };
    }
}
