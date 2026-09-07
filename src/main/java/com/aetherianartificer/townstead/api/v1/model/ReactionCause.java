package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;

/**
 * Why a reaction is being fired. {@link #source} is a lowercase word naming the trigger kind
 * ({@code context}, {@code task}, {@code gesture}, {@code command}); {@code command} bypasses
 * cooldown, lock and chance gates. Unknown sources are treated as {@code context}.
 */
public record ReactionCause(
        String source,
        Optional<ServerPlayer> player,
        Optional<BlockPos> location,
        Set<String> contextTags
) {
    public ReactionCause {
        source = source == null || source.isBlank() ? "context" : source;
        player = player == null ? Optional.empty() : player;
        location = location == null ? Optional.empty() : location;
        contextTags = contextTags == null ? Set.of() : Set.copyOf(contextTags);
    }

    public static ReactionCause context(ServerPlayer player) {
        return new ReactionCause("context", Optional.ofNullable(player), Optional.empty(), Set.of());
    }

    public static ReactionCause forced() {
        return new ReactionCause("command", Optional.empty(), Optional.empty(), Set.of());
    }
}
