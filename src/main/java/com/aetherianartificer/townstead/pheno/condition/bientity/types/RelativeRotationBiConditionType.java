package com.aetherianartificer.townstead.pheno.condition.bientity.types;

import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * True when the target lies within {@code max_angle} degrees of the actor's look
 * direction, i.e. the target is in the actor's view cone (Apoli's bi-entity
 * {@code relative_rotation}, simplified to a forward cone).
 *
 * <p>JSON: {@code { "type":"pheno:relative_rotation", "max_angle":45 }}</p>
 */
public final class RelativeRotationBiConditionType implements BiEntityConditionType {

    public static final String KEY = "pheno:relative_rotation";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BiEntityCondition parse(JsonObject json) {
        double maxAngle = GsonHelper.getAsDouble(json, "max_angle", 90);
        return (actor, target) -> {
            Vec3 dir = target.getEyePosition().subtract(actor.getEyePosition());
            if (dir.lengthSqr() < 1.0e-6) return true;
            double dot = Mth.clamp(actor.getLookAngle().dot(dir.normalize()), -1, 1);
            return Math.toDegrees(Math.acos(dot)) <= maxAngle;
        };
    }
}
