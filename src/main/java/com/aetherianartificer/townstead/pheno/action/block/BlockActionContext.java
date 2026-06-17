package com.aetherianartificer.townstead.pheno.action.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The block a {@link BlockAction} runs at: a {@link ServerLevel} and {@link BlockPos},
 * plus the optional {@code cause} entity that triggered it (for command source /
 * spawned-entity ownership). The {@code offset} and {@code area_of_effect} metas derive
 * new positions via {@link #at}.
 */
public final class BlockActionContext {

    private final ServerLevel level;
    private final BlockPos pos;
    private final LivingEntity cause;

    public BlockActionContext(ServerLevel level, BlockPos pos) {
        this(level, pos, null);
    }

    public BlockActionContext(ServerLevel level, BlockPos pos, @Nullable LivingEntity cause) {
        this.level = level;
        this.pos = pos;
        this.cause = cause;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    @Nullable
    public LivingEntity cause() {
        return cause;
    }

    /** The same context at a different block (used by offset / area-of-effect). */
    public BlockActionContext at(BlockPos newPos) {
        return new BlockActionContext(level, newPos, cause);
    }
}
