package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A quantity of fluid a recipe consumes or produces, in millibuckets.
 *
 * <p>Villagers never hold fluid. A keg ferments water into beer and a Caupona pot simmers stock,
 * but what a villager can carry is a bucket, a bottle, or the finished bowl. So a fluid amount
 * describes what happens <em>inside</em> a station, and the engine's job is to know which item to
 * bring so the fluid can get in, and which item to take away so it can come out.</p>
 *
 * <p>The bucket-equivalent is the useful unit for planning: a recipe wanting 1000mB of water needs
 * one bucket's worth, and one wanting 250mB needs a quarter of one, which still means bringing a
 * full container.</p>
 */
public record FluidAmount(ResourceLocation fluid, int millibuckets) {

    /** One vanilla bucket. */
    public static final int BUCKET = 1000;

    public FluidAmount {
        if (millibuckets < 0) millibuckets = 0;
    }

    /** How many full containers of {@code containerSize} it takes to satisfy this amount. */
    public int containersNeeded(int containerSize) {
        if (containerSize <= 0) return 0;
        return (millibuckets + containerSize - 1) / containerSize;
    }

    /** How many portions of {@code portionSize} this amount yields, rounding down. */
    public int portions(int portionSize) {
        if (portionSize <= 0) return 0;
        return millibuckets / portionSize;
    }

    public boolean isEmpty() {
        return millibuckets <= 0;
    }

    public static @Nullable FluidAmount of(@Nullable ResourceLocation fluid, int millibuckets) {
        return fluid == null || millibuckets <= 0 ? null : new FluidAmount(fluid, millibuckets);
    }
}
