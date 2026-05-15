package com.aetherianartificer.townstead.reaction.trigger.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Pluggable detector for "music is playing near this spot". Registered
 * implementations vote on whether a villager should pick up the
 * {@code near_music} context tag. The built-in jukebox provider always
 * registers; mod compat providers (Immersive Melodies, etc.) register
 * themselves when their mod is present.
 */
@FunctionalInterface
public interface MusicSourceProvider {
    boolean hasMusicNear(ServerLevel level, BlockPos pos, double radius);
}
