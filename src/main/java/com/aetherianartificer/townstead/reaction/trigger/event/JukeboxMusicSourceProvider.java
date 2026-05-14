package com.aetherianartificer.townstead.reaction.trigger.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Map;

/**
 * Reports {@code near_music = true} when any actively-spinning jukebox
 * sits within the given radius of the villager. Iterates loaded chunks
 * in range (small set even at the default 12-block radius) and checks
 * their block-entity maps for jukeboxes whose blockstate has
 * {@code HAS_RECORD = true}.
 */
public final class JukeboxMusicSourceProvider implements MusicSourceProvider {
    @Override
    public boolean hasMusicNear(ServerLevel level, BlockPos pos, double radius) {
        if (level == null || pos == null || radius <= 0) return false;
        double radiusSq = radius * radius;
        int r = (int) Math.ceil(radius);
        int minCx = (pos.getX() - r) >> 4;
        int maxCx = (pos.getX() + r) >> 4;
        int minCz = (pos.getZ() - r) >> 4;
        int maxCz = (pos.getZ() + r) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkAccess access = level.getChunkSource().getChunkNow(cx, cz);
                if (!(access instanceof LevelChunk chunk)) continue;
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockEntity be = entry.getValue();
                    if (!(be instanceof JukeboxBlockEntity)) continue;
                    BlockPos bePos = entry.getKey();
                    if (bePos.distSqr(pos) > radiusSq) continue;
                    BlockState state = be.getBlockState();
                    if (state.hasProperty(JukeboxBlock.HAS_RECORD) && state.getValue(JukeboxBlock.HAS_RECORD)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
