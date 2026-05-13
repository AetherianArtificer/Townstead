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
 */
public interface ReactionBackend {
    String key();

    boolean play(ServerLevel level, LivingEntity villager, List<String> refIds, Optional<JsonObject> args,
            ReactionContext context);
}
