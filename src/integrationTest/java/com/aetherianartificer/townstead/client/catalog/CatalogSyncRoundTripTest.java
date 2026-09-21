package com.aetherianartificer.townstead.client.catalog;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogSyncRoundTripTest {
    @Test void variantsKeepAlternativeAnchorsAndHangoutsAcrossTheWire() {
        var variant = new CatalogSyncS2CPayload.Variant(List.of("minecraft:water", "#test:water"),
                List.of(new CatalogSyncS2CPayload.Ingredient("minecraft:cobblestone", 60),
                        new CatalogSyncS2CPayload.Ingredient("minecraft:oak_fence", 8)));
        var alternative = new CatalogSyncS2CPayload.Variant(List.of("minecraft:water"),
                List.of(new CatalogSyncS2CPayload.Ingredient("minecraft:smooth_sandstone", 80)));
        var set = new CatalogSyncS2CPayload.DecorationSummary(ResourceLocation.tryParse("townstead:well"),
                ResourceLocation.tryParse("minecraft:water_bucket"), 6, 2, List.of(variant, alternative), true);
        var source = new CatalogSyncS2CPayload(List.of(), Map.of(), CatalogDataLoader.Theme.DEFAULT,
                Map.of("dock_l3", Map.of("nautical", 20, "industrious", 10)), List.of(set), Set.of("inn", "grove"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            source.write(buffer);
            var result = CatalogSyncS2CPayload.read(buffer);
            assertEquals(source, result); assertEquals(0, buffer.readableBytes());
            CatalogDataLoader.applySynced(result);
            assertTrue(CatalogDataLoader.isHangout("inn")); assertFalse(CatalogDataLoader.isHangout("dock_l3"));
            assertEquals(2, DecorationCatalogClientStore.entries().get(0).variants().size());
        } finally { buffer.release(); }
    }
}
