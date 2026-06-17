package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Applies bonemeal to the target block if it is a valid bonemealable target (Apoli's
 * block {@code bonemeal}): grows crops, spreads grass, ages saplings, etc.
 *
 * <p>JSON: {@code { "type":"pheno:bonemeal" }}</p>
 */
public final class BonemealBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:bonemeal";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        return ctx -> {
            ServerLevel level = ctx.level();
            BlockState state = level.getBlockState(ctx.pos());
            if (!(state.getBlock() instanceof BonemealableBlock bonemealable)) return;
            //? if >=1.21 {
            boolean valid = bonemealable.isValidBonemealTarget(level, ctx.pos(), state);
            //?} else {
            /*boolean valid = bonemealable.isValidBonemealTarget(level, ctx.pos(), state, false);
            *///?}
            if (!valid) return;
            if (bonemealable.isBonemealSuccess(level, level.getRandom(), ctx.pos(), state)) {
                bonemealable.performBonemeal(level, level.getRandom(), ctx.pos(), state);
            }
        };
    }
}
