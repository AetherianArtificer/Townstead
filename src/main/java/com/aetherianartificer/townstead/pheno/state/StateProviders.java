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

        /**
         * The reading if {@code entity} stood at {@code pos}. A provider that depends on place
         * overrides this; one about the entity alone answers the same everywhere.
         */
        default @Nullable Double readAt(LivingEntity entity, net.minecraft.core.BlockPos pos) {
            return read(entity);
        }
    }

    /** A provider whose reading depends on where the entity stands. */
    public static Provider positional(java.util.function.BiFunction<LivingEntity, net.minecraft.core.BlockPos, Double> at) {
        return new Provider() {
            @Override public @Nullable Double read(LivingEntity entity) { return at.apply(entity, entity.blockPosition()); }
            @Override public @Nullable Double readAt(LivingEntity entity, net.minecraft.core.BlockPos pos) { return at.apply(entity, pos); }
        };
    }

    public static void register(ResourceLocation id, Provider provider) {
        PROVIDERS.put(id, provider);
    }

    /** The reading at {@code pos}; the same as {@link #read} for a provider that ignores place. */
    public static @Nullable Double readAt(@Nullable ResourceLocation id, LivingEntity entity, net.minecraft.core.BlockPos pos) {
        Provider provider = id == null ? null : PROVIDERS.get(id);
        if (provider == null) return null;
        try {
            Double value = provider.readAt(entity, pos);
            return value == null || !Double.isFinite(value) ? null : value;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static @Nullable Double read(@Nullable ResourceLocation id, LivingEntity entity) {
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
