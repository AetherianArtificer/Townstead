package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderProductsTest {

    @Test
    void potionProductKeyRoundTripsBottleFormAndNamespacedPotion() {
        ResourceLocation form = id("minecraft:splash_potion");
        ResourceLocation potion = id("example:family/strong_remedy");

        ResourceLocation key = OrderProducts.potionKey(form, potion);
        OrderProducts.PotionParts decoded = OrderProducts.decodePotion(key);

        assertEquals(form, decoded.form());
        assertEquals(potion, decoded.potion());
    }

    @Test
    void exactProductUsesTheContextsProductCountRatherThanAllPotions() {
        ResourceLocation item = id("minecraft:potion");
        ResourceLocation healing = OrderProducts.potionKey(item, id("minecraft:healing"));
        Order order = new Order(item, Order.Mode.KEEP_STOCKED, 5);
        order.setProduct(healing, "Potion of Healing");

        OrderContext context = new OrderContext() {
            @Override public int stockOf(ResourceLocation ignored, Order.CountScope scope) {
                return 99;
            }
            @Override public int stockOfTag(ResourceLocation ignored, Order.CountScope scope) {
                return 0;
            }
            @Override public int stockOf(Order candidate, Order.CountScope scope) {
                return candidate.product().equals(healing) ? 2 : 0;
            }
            @Override public int villagerCount() { return 1; }
            @Override public boolean mayWork(Order ignored) { return true; }
        };

        assertEquals(3, order.outstanding(context));
        assertTrue(order.exactProduct());
        assertFalse(order.matches(item),
                "an exact potion line must not match every stack sharing its item id");
    }

    @Test
    void mergingWorksitesKeepsDifferentPotionsThatShareOneItem() {
        ResourceLocation item = id("minecraft:potion");
        Order healing = new Order(item, Order.Mode.MAKE, 1);
        healing.setProduct(OrderProducts.potionKey(item, id("minecraft:healing")),
                "Potion of Healing");
        Order strength = new Order(item, Order.Mode.MAKE, 1);
        strength.setProduct(OrderProducts.potionKey(item, id("minecraft:strength")),
                "Potion of Strength");
        OrderList destination = new OrderList();
        destination.add(healing);
        OrderList source = new OrderList();
        source.add(strength);

        assertEquals(1, destination.absorb(source));
        assertEquals(2, destination.size(),
                "exact product identity, not the shared bottle item, defines duplicates");
    }

    @Test
    void pizzaStationOutputIsDistinctFromItsBlankRawPizzaInput() {
        DiscoveredRecipe assembled = new DiscoveredRecipe(
                id("townstead:protocol/townstead/pizza_station/0"),
                StationType.PASSIVE_STATION, 1, id("pizzadelight:raw_pizza"), 1, 120,
                false, null, 0, List.of(), false, false, null);

        assertEquals(OrderProducts.assembledPizzaKey(), OrderProducts.key(assembled));
        assertTrue(OrderProducts.exact(OrderProducts.key(assembled), assembled.output()),
                "a blank base in storage must not satisfy an assembled-pizza order");
    }

    private static ResourceLocation id(String raw) {
        //? if >=1.21 {
        return ResourceLocation.parse(raw);
        //?} else {
        /*return new ResourceLocation(raw);
        *///?}
    }
}
