package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turning a two-stage fluid process into one recipe a villager can actually work.
 *
 * <p>Mods like Brewin' and Chewin' split brewing in half: one recipe ferments water and grain into
 * a tank of beer, and a second pours 250mB of that beer into a mug. Neither half is workable on
 * its own — the first ends with fluid nobody can carry, the second starts with fluid nobody has.
 * Joined on the fluid they share, the pair becomes an ordinary item-in, item-out recipe: grain and
 * a bucket of water go in, four mugs come out, and the engine never learns what a fluid is.</p>
 *
 * <p>That is deliberately the whole extent of fluid support. Modelling tanks and transfer would
 * mean teaching every planning path a second kind of quantity, for no gain a player would see.</p>
 */
public final class FluidRecipes {

    /** A recipe that fills a station with fluid: items and a base fluid in, fluid out. */
    public record Brew(
            ResourceLocation id,
            List<RecipeIngredient> inputs,
            @Nullable FluidAmount baseFluid,
            FluidAmount output,
            int timeTicks) {}

    /** A recipe that draws fluid back out as something carryable. */
    public record Pour(
            ResourceLocation id,
            ResourceLocation fluid,
            int amount,
            ResourceLocation outputItem,
            @Nullable ResourceLocation container) {}

    private FluidRecipes() {}

    /**
     * Joins each brew to the pour that empties it, yielding one recipe per pairing.
     *
     * <p>A brew with no matching pour is dropped rather than offered: its output would be a tank
     * of something the villager can never pick up, and a station that can be filled but not
     * emptied is a trap. Likewise a brew yielding less than one serving.</p>
     */
    public static List<DiscoveredRecipe> join(List<Brew> brews, List<Pour> pours,
                                              StationType stationType, int tier) {
        if (brews.isEmpty() || pours.isEmpty()) return List.of();

        Map<ResourceLocation, Pour> byFluid = new LinkedHashMap<>();
        for (Pour pour : pours) {
            if (pour == null || pour.fluid() == null || pour.amount() <= 0) continue;
            // Smallest pour wins: it divides a batch into the most servings, and a station that
            // can serve in small measures can always serve fewer.
            Pour existing = byFluid.get(pour.fluid());
            if (existing == null || pour.amount() < existing.amount()) {
                byFluid.put(pour.fluid(), pour);
            }
        }

        List<DiscoveredRecipe> out = new ArrayList<>();
        for (Brew brew : brews) {
            if (brew == null || brew.output() == null || brew.output().isEmpty()) continue;
            Pour pour = byFluid.get(brew.output().fluid());
            if (pour == null) continue;

            int servings = brew.output().portions(pour.amount());
            if (servings <= 0) continue;

            List<RecipeIngredient> inputs = new ArrayList<>(brew.inputs());

            // The base fluid has to arrive in something. With no known carrier the recipe is
            // unworkable, so it is dropped rather than offered as half-possible.
            if (brew.baseFluid() != null && !brew.baseFluid().isEmpty()) {
                ResourceLocation carrier = FluidCarriers.carrierFor(brew.baseFluid().fluid());
                if (carrier == null) continue;
                inputs.add(new RecipeIngredient(List.of(carrier),
                        brew.baseFluid().containersNeeded(FluidAmount.BUCKET)));
            }

            // Some pours need an empty vessel per serving; others hand back a filled item whose
            // container was already part of the pour.
            if (pour.container() != null) {
                inputs.add(new RecipeIngredient(List.of(pour.container()), servings));
            }

            out.add(new DiscoveredRecipe(
                    brew.id(), stationType, tier,
                    pour.outputItem(), servings,
                    Math.max(1, brew.timeTicks()),
                    false, null, 0,
                    List.copyOf(inputs),
                    false, true,
                    null));
        }
        return List.copyOf(out);
    }
}
