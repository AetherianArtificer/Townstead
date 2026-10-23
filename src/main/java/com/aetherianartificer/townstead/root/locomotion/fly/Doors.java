package com.aetherianartificer.townstead.root.locomotion.fly;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Opens the wooden doors and fence gates on a pilot's own route, and closes them behind.
 * The route planner counts them as open; MCA's door handling only follows its own paths.
 */
final class Doors {

    private static final double CLOSE_DISTANCE_SQR = 2.5 * 2.5;

    private final List<BlockPos> opened = new ArrayList<>();

    /** Open anything in the body's way between here and a point ahead. */
    void openAlong(Mob mob, Vec3 ahead) {
        Level level = mob.level();
        Vec3 from = mob.position();
        int steps = Math.max(1, Mth.ceil(from.distanceTo(ahead) / 0.5));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(ahead, (double) i / steps);
            for (int dy = 0; dy < Math.max(1, Mth.ceil(mob.getBbHeight())); dy++) {
                BlockPos pos = BlockPos.containing(p.x, p.y + dy, p.z);
                if (!level.hasChunkAt(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.WOODEN_DOORS) && state.getBlock() instanceof DoorBlock door
                        && !state.getValue(BlockStateProperties.OPEN)) {
                    door.setOpen(mob, level, state, pos, true);
                    remember(state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                            == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? pos.below() : pos);
                } else if (state.getBlock() instanceof FenceGateBlock && !state.getValue(BlockStateProperties.OPEN)) {
                    level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 10);
                    level.gameEvent(mob, GameEvent.BLOCK_OPEN, pos);
                    remember(pos);
                }
            }
        }
    }

    /** Close what this pilot opened once the body is clear of it and nobody stands in it. */
    void closeBehind(Mob mob) {
        Level level = mob.level();
        for (Iterator<BlockPos> it = opened.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (mob.position().distanceToSqr(Vec3.atCenterOf(pos)) < CLOSE_DISTANCE_SQR) continue;
            if (!level.hasChunkAt(pos)) {
                it.remove();
                continue;
            }
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).expandTowards(0, 1, 0)).isEmpty()) continue;
            close(mob, level, pos);
            it.remove();
        }
    }

    /** Close everything still open (the trip ended). */
    void closeAll(Mob mob) {
        for (BlockPos pos : opened) {
            if (mob.level().hasChunkAt(pos)) close(mob, mob.level(), pos);
        }
        opened.clear();
    }

    private void remember(BlockPos pos) {
        if (!opened.contains(pos)) opened.add(pos.immutable());
    }

    private static void close(Mob mob, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door && state.getValue(BlockStateProperties.OPEN)) {
            door.setOpen(mob, level, state, pos, false);
        } else if (state.getBlock() instanceof FenceGateBlock && state.getValue(BlockStateProperties.OPEN)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, false), 10);
            level.gameEvent(mob, GameEvent.BLOCK_CLOSE, pos);
        }
    }
}
