package com.aetherianartificer.townstead.culture;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Reloadable settlement-name pools addressable by culture JSON. */
public final class SettlementNamePools {
    private static volatile Map<ResourceLocation, SettlementNamePool> pools = Map.of();

    private SettlementNamePools() {}

    public static void replace(Map<ResourceLocation, SettlementNamePool> loaded) {
        pools = loaded == null ? Map.of() : Map.copyOf(loaded);
    }

    public static @Nullable SettlementNamePool get(@Nullable ResourceLocation id) {
        return id == null ? null : pools.get(id);
    }
}
