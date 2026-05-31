package com.aetherianartificer.townstead.client.skin;

import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Client-side registry of {@link SkinTintProvider}s. The skin-render hook calls
 * {@link #resolve} to get a per-entity RGB tint (or empty to keep MCA's native
 * skin). Providers are consulted in registration order and the first that returns
 * a colour wins. The built-in origin provider is registered here; other features
 * or mods may add their own.
 */
public final class SkinTintRegistry {

    private static final List<SkinTintProvider> PROVIDERS = new ArrayList<>();

    static {
        register(new OriginSkinTintProvider());
    }

    private SkinTintRegistry() {}

    public static void register(SkinTintProvider provider) {
        PROVIDERS.add(provider);
    }

    public static OptionalInt resolve(LivingEntity entity) {
        for (SkinTintProvider provider : PROVIDERS) {
            OptionalInt colour = provider.resolve(entity);
            if (colour.isPresent()) return colour;
        }
        return OptionalInt.empty();
    }
}
