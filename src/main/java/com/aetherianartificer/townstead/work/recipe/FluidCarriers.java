package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which item a villager carries to deliver a fluid.
 *
 * <p>A recipe wanting a bucket of water is really a recipe wanting someone to fetch a water
 * bucket, and that mapping is the only thing standing between a fluid requirement and an ordinary
 * errand. Vanilla water is registered by default; a mod whose recipes drink something stranger
 * registers its own carrier.</p>
 *
 * <p>An unregistered fluid has no carrier, which is treated as "no villager can bring this"
 * rather than guessed at — a wrong guess would send someone to fetch the wrong bucket forever.</p>
 */
public final class FluidCarriers {

    private static final Map<ResourceLocation, ResourceLocation> CARRIERS = new ConcurrentHashMap<>();

    private FluidCarriers() {}

    public static void bootstrap() {
        //? if >=1.21 {
        register(ResourceLocation.parse("minecraft:water"), ResourceLocation.parse("minecraft:water_bucket"));
        register(ResourceLocation.parse("minecraft:flowing_water"), ResourceLocation.parse("minecraft:water_bucket"));
        register(ResourceLocation.parse("minecraft:milk"), ResourceLocation.parse("minecraft:milk_bucket"));
        //?} else {
        /*register(new ResourceLocation("minecraft", "water"), new ResourceLocation("minecraft", "water_bucket"));
        register(new ResourceLocation("minecraft", "flowing_water"), new ResourceLocation("minecraft", "water_bucket"));
        register(new ResourceLocation("minecraft", "milk"), new ResourceLocation("minecraft", "milk_bucket"));
        *///?}
    }

    public static void register(ResourceLocation fluid, ResourceLocation carrierItem) {
        if (fluid == null || carrierItem == null) return;
        CARRIERS.put(fluid, carrierItem);
    }

    /** The item that delivers this fluid, or null when nothing known can carry it. */
    public static @Nullable ResourceLocation carrierFor(@Nullable ResourceLocation fluid) {
        return fluid == null ? null : CARRIERS.get(fluid);
    }

    /**
     * Whether this item is one a villager fetches fluid in. Stations that take both a fluid and a
     * vessel through the same slot need to tell a bucket of water from an empty bowl, and which
     * items carry fluid is exactly what this registry already knows.
     */
    public static boolean isCarrier(@Nullable ResourceLocation item) {
        return item != null && CARRIERS.containsValue(item);
    }

    /** Every fluid a villager can actually fetch, for stations that must pick a workable base. */
    public static Collection<ResourceLocation> carriedFluids() {
        return List.copyOf(CARRIERS.keySet());
    }
}
