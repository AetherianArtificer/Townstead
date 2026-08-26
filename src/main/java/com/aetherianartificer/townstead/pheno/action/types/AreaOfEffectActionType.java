package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * Runs the wrapped entity action on every living entity within {@code radius} of the
 * actor (Apoli's entity {@code area_of_effect}). The actor is excluded unless
 * {@code include_self} is set; each target becomes the inner action's {@code entity()}
 * with the actor as {@code other()}. A one-shot nova (pair with a trigger), distinct
 * from the periodic {@code aura} gene.
 *
 * <p>{@code target} provides the common {@code all}, {@code hostile}, and
 * {@code non_hostile} groups. {@code bientity_condition} can narrow that group further and is
 * evaluated with the actor against each candidate, for example to produce a forward cone.</p>
 *
 * <p>JSON: {@code { "type":"pheno:area_of_effect", "radius":4,
 * "target":"hostile", "bientity_condition":{ "type":"pheno:relative_rotation", "max_angle":60 },
 * "action":{ "type":"pheno:damage", "amount":4 } }}</p>
 */
public final class AreaOfEffectActionType implements ActionType {

    public static final String KEY = "pheno:area_of_effect";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        double radius = Math.max(0, GsonHelper.getAsDouble(json, "radius", 4));
        boolean includeSelf = GsonHelper.getAsBoolean(json, "include_self", false);
        String targetMode = switch (GsonHelper.getAsString(json, "target", "all")
                .toLowerCase(java.util.Locale.ROOT)) {
            case "hostile" -> "hostile";
            case "non_hostile" -> "non_hostile";
            default -> "all";
        };
        BiEntityCondition filter = json.has("bientity_condition")
                ? BiEntityConditions.parse(json.get("bientity_condition")) : null;
        Action inner = Actions.parse(json.get("action"));
        if (inner == null) return null;
        return ctx -> {
            LivingEntity self = ctx.entity();
            for (LivingEntity target : self.level().getEntitiesOfClass(LivingEntity.class,
                    self.getBoundingBox().inflate(radius))) {
                if (target == self && !includeSelf) continue;
                if (!targetMode.equals("all")) {
                    boolean hostile = target instanceof net.minecraft.world.entity.monster.Enemy
                            || (target instanceof net.minecraft.world.entity.Mob mob
                            && mob.getTarget() == self);
                    if (targetMode.equals("hostile") != hostile) continue;
                }
                // The actor is always the first argument, so "hostile" reads as "hostile to me".
                if (filter != null && !filter.test(self, target)) continue;
                inner.run(ctx.retarget(target, self));
            }
        };
    }
}
