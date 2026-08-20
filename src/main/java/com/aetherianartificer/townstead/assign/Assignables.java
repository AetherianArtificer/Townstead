package com.aetherianartificer.townstead.assign;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The providers, and dispatch across them.
 *
 * <p>Order is registration order, and it decides two things: where a source appears in the
 * catalogue's rail, and who gets first refusal on an id. Townstead registers first so its abilities
 * cannot be shadowed by a datapack claiming the same id, which is the one collision that would be
 * both easy to cause and confusing to debug.</p>
 */
public final class Assignables {

    private Assignables() {}

    private static final List<AssignableProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    public static void register(AssignableProvider provider) {
        if (provider != null && !PROVIDERS.contains(provider)) PROVIDERS.add(provider);
    }

    /**
     * Everything the player could assign, deduplicated by id.
     *
     * <p>First provider to claim an id keeps it. A later duplicate is dropped rather than merged,
     * because two providers disagreeing about what an id does is not something a UI can resolve.</p>
     */
    public static List<Assignable> collect(ServerPlayer player) {
        List<Assignable> out = new ArrayList<>();
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (AssignableProvider provider : PROVIDERS) {
            List<Assignable> batch = new ArrayList<>();
            try {
                provider.collect(player, batch);
            } catch (Exception ignored) {
                // A broken provider costs its own entries, not the whole catalogue.
                continue;
            }
            for (Assignable assignable : batch) {
                if (assignable != null && seen.add(assignable.id())) out.add(assignable);
            }
        }
        return List.copyOf(out);
    }

    /** Finds the single assignable with this id, or null. */
    public static Assignable byId(ServerPlayer player, ResourceLocation id) {
        if (id == null) return null;
        for (Assignable assignable : collect(player)) {
            if (assignable.id().equals(id)) return assignable;
        }
        return null;
    }

    /**
     * Hands the id to whichever provider owns it.
     *
     * <p>Every provider is asked in turn until one claims it, and none is told what the others
     * decided. That is what keeps a bridge from having to know about Townstead's ability layer.</p>
     */
    public static boolean invoke(ServerPlayer player, ResourceLocation id) {
        if (player == null || id == null) return false;
        for (AssignableProvider provider : PROVIDERS) {
            try {
                if (provider.invoke(player, id)) return true;
            } catch (Exception ignored) {
                // A provider that throws has refused; the next one may still own this id.
            }
        }
        return false;
    }
}
