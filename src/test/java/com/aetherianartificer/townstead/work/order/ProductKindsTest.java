package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductKindsTest {

    private static final ResourceLocation DRAUGHT = id("example:draught");
    private static final ResourceLocation COLD_DRAUGHT = id("example_product:draught/cold");

    /** A kind that names one draught recipe as its own product. */
    private static final ProductKinds.Kind DRAUGHTS = new ProductKinds.Kind() {
        @Override public ResourceLocation keyOf(ItemStack stack) {
            return null;
        }
        @Override public ResourceLocation keyForRecipe(ResourceLocation recipeId) {
            return recipeId.getPath().startsWith("taught/") ? COLD_DRAUGHT : null;
        }
        @Override public boolean owns(ResourceLocation product) {
            return product.getNamespace().equals("example_product");
        }
        @Override public ResourceLocation item(ResourceLocation product) {
            return DRAUGHT;
        }
        @Override public String label(ResourceLocation product) {
            return "Draught for a Cold";
        }
    };

    @AfterEach
    void forget() {
        ProductKinds.clear();
    }

    private static DiscoveredRecipe recipe(String id) {
        return new DiscoveredRecipe(id(id), StationType.PASSIVE_STATION, 1, DRAUGHT, 1, 100,
                false, null, 0, List.of(), false, false, null);
    }

    @Test
    void aKindsRecipeMakesItsOwnProductAndOthersKeepTheirItem() {
        ProductKinds.register(DRAUGHTS);
        assertEquals(COLD_DRAUGHT, OrderProducts.key(recipe("example:taught/cold")));
        assertEquals(DRAUGHT, OrderProducts.key(recipe("example:plain")),
                "a recipe the kind does not claim keeps its output item as its product");
    }

    @Test
    void aKindsProductNamesItsItemAndItsLabel() {
        ProductKinds.register(DRAUGHTS);
        assertEquals(DRAUGHT, OrderProducts.fallbackItem(COLD_DRAUGHT));
        assertTrue(OrderProducts.exact(COLD_DRAUGHT, DRAUGHT), "a kind's product is narrower than its item");
        assertEquals("Draught for a Cold", OrderProducts.label(COLD_DRAUGHT, DRAUGHT));
    }

    @Test
    void aFailingKindDoesNotStopTheOthers() {
        ProductKinds.register(new ProductKinds.Kind() {
            @Override public ResourceLocation keyOf(ItemStack stack) {
                throw new IllegalStateException("broken");
            }
            @Override public ResourceLocation keyForRecipe(ResourceLocation recipeId) {
                throw new IllegalStateException("broken");
            }
            @Override public boolean owns(ResourceLocation product) {
                throw new IllegalStateException("broken");
            }
            @Override public ResourceLocation item(ResourceLocation product) {
                return DRAUGHT;
            }
        });
        ProductKinds.register(DRAUGHTS);
        assertEquals(COLD_DRAUGHT, OrderProducts.key(recipe("example:taught/cold")));
        assertEquals(DRAUGHT, OrderProducts.fallbackItem(COLD_DRAUGHT));
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
