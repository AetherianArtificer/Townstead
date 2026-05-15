package com.aetherianartificer.townstead.reaction.backend;

import com.aetherianartificer.townstead.reaction.ReactionBinding;
import com.aetherianartificer.townstead.reaction.ReactionContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * Pluggable animation backend. The {@link #key()} returns the prefix used
 * in {@code ref} strings (e.g. {@code emotecraft}); the dispatcher
 * resolves the backend by that prefix and hands over the whole
 * {@link ReactionBinding} so the backend can read its
 * {@code allow_movement}, {@code parts_skip}, {@code args}, etc.
 *
 * <p>Return value is the ref id the backend actually picked (or
 * {@link Optional#empty()} on failure). The dispatcher uses this to
 * propagate a gesture event for mirroring.</p>
 */
public interface ReactionBackend {
    String key();

    Optional<String> play(ServerLevel level, LivingEntity villager, ReactionBinding binding, ReactionContext context);
}
