package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Pushes the target block one step away from the cause entity, along the cause's facing, as a
 * piston would: a row of up to {@code max} blocks moves together (default 1). The push fails when
 * there is no cause, the row is longer than {@code max}, the space behind it is not free, or any
 * block in the row has a block entity, cannot be pushed by a piston, or cannot be broken.
 *
 * <p>JSON: {@code { "type":"pheno:shove", "max":3 }}</p>
 */
public final class ShoveBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:shove";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        int max = Math.max(1, Math.min(12, GsonHelper.getAsInt(json, "max", 1)));
        return ctx -> {
            ServerLevel level = ctx.level();
            LivingEntity cause = ctx.cause();
            if (cause == null) {
                ctx.fail();
                return;
            }
            Direction way = cause.getDirection();
            List<BlockPos> row = new ArrayList<>();
            BlockPos cursor = ctx.pos();
            while (true) {
                BlockState state = level.getBlockState(cursor);
                if (state.isAir() || state.canBeReplaced()) break;
                if (row.size() >= max || !movable(level, cursor, state)) {
                    ctx.fail();
                    return;
                }
                row.add(cursor);
                cursor = cursor.relative(way);
            }
            if (row.isEmpty() || !level.isInWorldBounds(cursor)) {
                ctx.fail();
                return;
            }
            for (int i = row.size() - 1; i >= 0; i--) {
                BlockPos from = row.get(i);
                BlockState state = level.getBlockState(from);
                level.setBlock(from.relative(way), state, Block.UPDATE_ALL);
                level.removeBlock(from, false);
            }
        };
    }

    private static boolean movable(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) != null) return false;
        if (state.getDestroySpeed(level, pos) < 0f) return false;
        PushReaction reaction = state.getPistonPushReaction();
        return reaction == PushReaction.NORMAL || reaction == PushReaction.PUSH_ONLY;
    }
}
