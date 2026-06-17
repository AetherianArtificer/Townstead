package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;

/**
 * Detonates an explosion at the target block (Apoli's block {@code explode}).
 * {@code power} is the radius, {@code fire} leaves flames, {@code destroy} breaks blocks.
 * The {@code cause} entity (if any) is credited as the source. Same six-arg overload on
 * both branches.
 *
 * <p>JSON: {@code { "type":"pheno:explode", "power":3.0, "fire":false,
 * "destroy":true }}</p>
 */
public final class ExplodeBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:explode";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        float power = GsonHelper.getAsFloat(json, "power", 2.0f);
        boolean fire = GsonHelper.getAsBoolean(json, "fire", false);
        boolean destroy = GsonHelper.getAsBoolean(json, "destroy", true);
        return ctx -> ctx.level().explode(ctx.cause(),
                ctx.pos().getX() + 0.5, ctx.pos().getY() + 0.5, ctx.pos().getZ() + 0.5,
                power, fire,
                destroy ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
    }
}
