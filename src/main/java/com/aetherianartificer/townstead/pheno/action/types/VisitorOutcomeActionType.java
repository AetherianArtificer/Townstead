package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.visitor.Visitors;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * Decides what becomes of the nearest visitor with {@code role}: {@code pheno:visitor_stay} settles
 * them (and their party) as residents, {@code pheno:visitor_leave} walks them out of town for good.
 * <pre>
 * { "type": "pheno:visitor_stay", "role": "fledgling" }
 * </pre>
 */
public final class VisitorOutcomeActionType implements ActionType {
    public static final String STAY = "pheno:visitor_stay";
    public static final String LEAVE = "pheno:visitor_leave";
    private final boolean stay;

    public VisitorOutcomeActionType(boolean stay) {
        this.stay = stay;
    }

    @Override
    public String key() {
        return stay ? STAY : LEAVE;
    }

    @Override
    public Action parse(JsonObject json) {
        String role = GsonHelper.getAsString(json, "role", "");
        double radius = GsonHelper.getAsDouble(json, "radius", 32);
        if (role.isBlank()) return null;
        return ctx -> {
            LivingEntity around = ctx.other() != null ? ctx.other() : ctx.entity();
            VillagerEntityMCA visitor = around == null ? null : Visitors.near(around, role, radius);
            if (visitor == null) {
                ctx.fail();
                return;
            }
            if (stay) Visitors.settle(visitor);
            else Visitors.dismiss(visitor);
        };
    }
}
