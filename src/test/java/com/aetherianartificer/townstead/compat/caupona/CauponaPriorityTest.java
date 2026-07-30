package com.aetherianartificer.townstead.compat.caupona;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caupona decides which soup a potful makes by priority, first match winning. So two recipes that
 * would both accept the same item are not two options — the stronger one always takes it, and
 * offering the weaker one would promise a soup that never appears.
 */
class CauponaPriorityTest {

    @Test
    void strongerRecipeTakesTheContestedItem() {
        List<CauponaFluidRecipes.Brew> resolved = CauponaFluidRecipes.resolveByPriority(List.of(
                brew("caupona:cooking/stock", 0, "minecraft:water", "caupona:stock",
                        "minecraft:bone", "minecraft:carrot"),
                brew("caupona:cooking/gelatin", 1088, "minecraft:water", "caupona:bone_gelatin",
                        "minecraft:bone")));

        assertEquals(2, resolved.size(), "both soups stay workable, on different items");
        CauponaFluidRecipes.Brew gelatin = byId(resolved, "caupona:cooking/gelatin");
        CauponaFluidRecipes.Brew stock = byId(resolved, "caupona:cooking/stock");
        assertEquals(Set.of(id("minecraft:bone")), gelatin.items());
        assertEquals(Set.of(id("minecraft:carrot")), stock.items(),
                "bone belongs to the higher-priority soup and must not be offered twice");
    }

    @Test
    void fullyOvershadowedRecipeIsDropped() {
        List<CauponaFluidRecipes.Brew> resolved = CauponaFluidRecipes.resolveByPriority(List.of(
                brew("caupona:cooking/weak", 0, "minecraft:water", "caupona:weak", "minecraft:bone"),
                brew("caupona:cooking/strong", 128, "minecraft:water", "caupona:strong", "minecraft:bone")));

        assertEquals(1, resolved.size(), "a soup with nothing left to cook is not a soup");
        assertEquals(id("caupona:cooking/strong"), resolved.get(0).id());
    }

    @Test
    void adifferentBaseIsAdifferentPot() {
        List<CauponaFluidRecipes.Brew> resolved = CauponaFluidRecipes.resolveByPriority(List.of(
                brew("caupona:cooking/borscht", 128, "minecraft:water", "caupona:borscht",
                        "minecraft:beetroot"),
                brew("caupona:cooking/borscht_cream", 128, "minecraft:milk", "caupona:borscht_cream",
                        "minecraft:beetroot")));

        assertEquals(2, resolved.size(), "the same item in milk makes a different soup");
        assertTrue(resolved.stream().allMatch(b -> b.items().contains(id("minecraft:beetroot"))));
    }

    private static CauponaFluidRecipes.Brew byId(List<CauponaFluidRecipes.Brew> brews, String id) {
        return brews.stream().filter(b -> b.id().equals(id(id))).findFirst().orElseThrow();
    }

    private static CauponaFluidRecipes.Brew brew(String id, int priority, String base, String output,
                                                 String... items) {
        Set<ResourceLocation> accepted = new LinkedHashSet<>();
        for (String item : items) accepted.add(id(item));
        return new CauponaFluidRecipes.Brew(id(id), priority, id(base), id(output), accepted, 4, 800);
    }

    private static ResourceLocation id(String raw) {
        //? if >=1.21 {
        return ResourceLocation.parse(raw);
        //?} else {
        /*return new ResourceLocation(raw);
        *///?}
    }
}
