package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Makes the entity jump: the semantic verb over a raw {@code add_velocity}. Uses the vanilla jump
 * impulse (so Jump Boost still counts) scaled by {@code strength}, and clears fall distance so a
 * mid-air jump does not bank fall damage. {@code strength} 1.0 is a normal jump.
 *
 * <p>It always applies the impulse regardless of being grounded (it <i>sets</i> the upward velocity
 * rather than calling the grounded-gated vanilla jump), so it works in mid-air for multi-jumps.
 * Whether the entity is <i>allowed</i> to jump (on the ground, has charges left) is the carrying
 * gene's condition, not this action's.</p>
 *
 * <p>JSON: {@code { "type":"pheno:jump", "strength":0.9 }}</p>
 */
public final class JumpActionType implements ActionType {

    public static final String KEY = "pheno:jump";

    private static final double BASE_JUMP_POWER = 0.42;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        float strength = GsonHelper.getAsFloat(json, "strength", 1.0f);
        return ctx -> {
            LivingEntity entity = ctx.entity();
            double power = BASE_JUMP_POWER;
            MobEffectInstance boost = entity.getEffect(MobEffects.JUMP);
            if (boost != null) power += 0.1 * (boost.getAmplifier() + 1);
            Vec3 m = entity.getDeltaMovement();
            entity.setDeltaMovement(m.x, power * strength, m.z);
            entity.resetFallDistance();
            entity.hasImpulse = true;
        };
    }
}
