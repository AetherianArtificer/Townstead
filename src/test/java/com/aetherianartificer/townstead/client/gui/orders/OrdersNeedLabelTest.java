package com.aetherianartificer.townstead.client.gui.orders;

import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrdersNeedLabelTest {
    @Test
    void exactProductLabelOverridesSharedPhysicalItemName() {
        ResourceLocation rawPizza = id("pizzadelight:raw_pizza");
        var preparedPizza = new OrdersSnapshotS2CPayload.Need(
                List.of(rawPizza), 1, "Prepared Pizza");

        assertEquals("Prepared Pizza",
                OrdersScreen.displayedNeedName(preparedPizza, rawPizza));
        assertEquals("Prepared Pizza",
                OrdersScreen.stackRowName(null, preparedPizza.label()));
    }

    private static ResourceLocation id(String raw) {
        //? if >=1.21 {
        return ResourceLocation.parse(raw);
        //?} else {
        /*return new ResourceLocation(raw);
        *///?}
    }
}
