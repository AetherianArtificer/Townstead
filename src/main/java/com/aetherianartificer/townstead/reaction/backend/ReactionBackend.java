package com.aetherianartificer.townstead.reaction.backend;

import com.aetherianartificer.townstead.reaction.ReactionContext;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * Pluggable animation backend. The {@link #key()} returns the prefix used
 * in {@code ref} strings (e.g. {@code emotecraft}); the dispatcher
 * resolves the backend by that prefix and hands over the parsed list of
 * ids that share it. Backends choose a single id (uniform, weighted, or
 * otherwise) and trigger their animation playback. {@code play} must be
 * non-blocking: backends with durations own their own timeout state.
 *
 * <p>Return value is the ref id the backend actually picked (or
 * {@link Optional#empty()} on failure). The dispatcher uses this to
 * propagate a gesture event for mirroring: nearby villagers see the same
 * emote name as if the source had been a player typing {@code /emote}.</p>
 */
public interface ReactionBackend {
    String key();

    Optional<String> play(ServerLevel level, LivingEntity villager, List<String> refIds, Optional<JsonObject> args,
            ReactionContext context);
}
