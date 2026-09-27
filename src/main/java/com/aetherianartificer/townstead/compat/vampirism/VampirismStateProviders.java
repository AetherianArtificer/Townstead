package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.pheno.state.StateProviders;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reflection-only bridge exposing a Vampirism player's faction and lord levels as state
 * providers, so Pheno can react to what Vampirism runs without owning it. The handler lookup
 * returns {@code Optional} on 1.21.1 and {@code LazyOptional} on 1.20.1; both are unwrapped
 * here, and the rest of the API is identical across versions.
 */
public final class VampirismStateProviders {
    public static final String MOD_ID = "vampirism";
    private static final ResourceLocation VAMPIRE = ResourceLocation.tryParse("vampirism:vampire");
    private static final ResourceLocation HUNTER = ResourceLocation.tryParse("vampirism:hunter");

    private static volatile boolean probeAttempted;
    private static volatile boolean probeOk;
    private static Method getHandler;
    private static Method currentFaction;
    private static Method currentLevel;
    private static Method lordLevel;
    private static Method factionId;

    private VampirismStateProviders() {}

    public static void register() {
        if (!ModCompat.isLoaded(MOD_ID)) return;
        StateProviders.register(id("vampire_level"), entity -> level(entity, VAMPIRE, false));
        StateProviders.register(id("hunter_level"), entity -> level(entity, HUNTER, false));
        StateProviders.register(id("vampire_lord_level"), entity -> level(entity, VAMPIRE, true));
        StateProviders.register(id("hunter_lord_level"), entity -> level(entity, HUNTER, true));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse(MOD_ID + ":" + path);
    }

    /** The player's level in {@code faction}, or null when they are not in it (or at level 0). */
    private static @Nullable Double level(LivingEntity entity, ResourceLocation faction, boolean lord) {
        if (!(entity instanceof Player player) || !ensureProbe()) return null;
        try {
            Object handler = unwrap(getHandler.invoke(null, player));
            if (handler == null) return null;
            Object current = currentFaction.invoke(handler);
            if (current == null || !faction.equals(factionId.invoke(current))) return null;
            int value = ((Number) (lord ? lordLevel : currentLevel).invoke(handler)).intValue();
            return value > 0 ? (double) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object unwrap(@Nullable Object holder) throws ReflectiveOperationException {
        if (holder == null) return null;
        if (holder instanceof Optional<?> optional) return optional.orElse(null);
        // LazyOptional#resolve() yields a plain Optional.
        Object resolved = holder.getClass().getMethod("resolve").invoke(holder);
        return resolved instanceof Optional<?> optional ? optional.orElse(null) : null;
    }

    private static boolean ensureProbe() {
        if (probeAttempted) return probeOk;
        synchronized (VampirismStateProviders.class) {
            if (probeAttempted) return probeOk;
            try {
                Class<?> api = Class.forName("de.teamlapen.vampirism.api.VampirismAPI");
                Class<?> handler = Class.forName("de.teamlapen.vampirism.api.entity.factions.IFactionPlayerHandler");
                Class<?> faction = Class.forName("de.teamlapen.vampirism.api.entity.factions.IFaction");
                getHandler = api.getMethod("getFactionPlayerHandler", Player.class);
                currentFaction = handler.getMethod("getCurrentFaction");
                currentLevel = handler.getMethod("getCurrentLevel");
                lordLevel = handler.getMethod("getLordLevel");
                factionId = faction.getMethod("getID");
                probeOk = true;
            } catch (Throwable ignored) {
                probeOk = false;
            }
            probeAttempted = true;
            return probeOk;
        }
    }
}
