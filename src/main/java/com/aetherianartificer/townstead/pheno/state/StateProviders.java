package com.aetherianartificer.townstead.pheno.state;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Readings another mod owns, registered by its bridge and observed by {@code pheno:provider}
 * backings. A backing naming an unregistered provider reads nothing, so data can ship for a
 * mod that is absent.
 */
public final class StateProviders {
    private static final Map<ResourceLocation, Provider> PROVIDERS = new ConcurrentHashMap<>();

    private StateProviders() {}

    @FunctionalInterface
    public interface Provider {
        /** The current reading for {@code entity}, or null when the provider has nothing to report. */
        @Nullable Double read(LivingEntity entity);
    }

    public static void register(ResourceLocation id, Provider provider) {
        PROVIDERS.put(id, provider);
    }

    static @Nullable Double read(@Nullable ResourceLocation id, LivingEntity entity) {
        Provider provider = id == null ? null : PROVIDERS.get(id);
        if (provider == null) return null;
        try {
            Double value = provider.read(entity);
            return value == null || !Double.isFinite(value) ? null : value;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
