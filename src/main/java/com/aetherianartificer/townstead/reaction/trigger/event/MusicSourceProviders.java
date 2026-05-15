package com.aetherianartificer.townstead.reaction.trigger.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of {@link MusicSourceProvider}s consulted by {@link ContextResolver}
 * to decide whether the {@code near_music} tag applies to a villager.
 * Short-circuits on the first provider that says yes.
 */
public final class MusicSourceProviders {
    private static final List<MusicSourceProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private MusicSourceProviders() {}

    public static void register(MusicSourceProvider provider) {
        if (provider != null) PROVIDERS.add(provider);
    }

    public static boolean anyMusicNear(ServerLevel level, BlockPos pos, double radius) {
        if (level == null || pos == null) return false;
        for (MusicSourceProvider provider : PROVIDERS) {
            try {
                if (provider.hasMusicNear(level, pos, radius)) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }
}
