package com.aetherianartificer.townstead.api.resource;

import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Public registration point for read-only external resource HUD adapters. */
public final class ResourceHudProviders {

    private static final List<ResourceHudProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private ResourceHudProviders() {}

    public static void register(ResourceHudProvider provider) {
        if (provider != null) PROVIDERS.add(provider);
    }

    public static List<ResourceHudProvider.Meter> collect(LivingEntity entity) {
        List<ResourceHudProvider.Meter> out = new ArrayList<>();
        for (ResourceHudProvider provider : PROVIDERS) provider.collect(entity, out);
        return List.copyOf(out);
    }
}
