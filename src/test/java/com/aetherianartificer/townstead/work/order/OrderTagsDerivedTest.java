package com.aetherianartificer.townstead.work.order;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTagsDerivedTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    @AfterEach
    void reset() {
        OrderTags.clearDerived();
        OrderTags.resolveWith(null);
    }

    @Test
    void derivedCategoryAnswersMembershipMembersAndListing() {
        // A resolver that knows nothing stands in for the registry, so only the derived side answers.
        OrderTags.resolveWith((tag, item) -> false);
        ResourceLocation warm = id("townstead:orders/warm_clothing");
        OrderTags.registerDerived(warm, () -> List.of(id("wp:sweater"), id("acc:tundra_hood")));

        assertTrue(OrderTags.contains(warm, id("wp:sweater")));
        assertFalse(OrderTags.contains(warm, id("acc:straw_hat")));
        assertFalse(OrderTags.contains(id("townstead:orders/cool_clothing"), id("wp:sweater")));
        assertEquals(List.of(id("wp:sweater"), id("acc:tundra_hood")), OrderTags.members(warm));
        assertEquals(List.of(warm), OrderTags.categories());
    }

    @Test
    void resolverStillAnswersUnderivedTags() {
        OrderTags.resolveWith((tag, item) -> tag.getPath().equals("orders/cooked_meats"));
        assertTrue(OrderTags.contains(id("townstead:orders/cooked_meats"), id("minecraft:cooked_beef")));
        assertTrue(OrderTags.members(id("townstead:orders/cooked_meats")).isEmpty());
    }
}
