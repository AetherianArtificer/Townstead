package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.CultureClothing;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingTraditionsPickerTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse("pack:" + path);
    }

    private static Culture root(String culture, ResourceLocation tradition) {
        return new Culture(id(culture), Component.literal(culture), tradition);
    }

    private static Culture sub(String culture, String parent, ResourceLocation tradition) {
        return new Culture(id(culture), Component.literal(parent), tradition, null, CultureClothing.NONE, null, null,
                id(parent), Map.of(), List.of());
    }

    @Test void aTraditionACultureHoldsIsPickedThroughThatCulture() {
        Set<ResourceLocation> held = NamingTraditions.heldByCultures(List.of(root("coast", id("coast"))));
        assertTrue(held.contains(id("coast")));
    }

    @Test void aSubcultureOwnTraditionIsNotOfferedOnItsOwn() {
        Set<ResourceLocation> held = NamingTraditions.heldByCultures(
                List.of(root("coast", id("coast")), sub("merchants", "coast", id("merchants"))));
        assertTrue(held.contains(id("merchants")));
    }

    @Test void anUnheldTraditionIsStillOffered() {
        Set<ResourceLocation> held = NamingTraditions.heldByCultures(List.of(root("coast", id("coast"))));
        assertFalse(held.contains(id("shared_file")));
    }

    @Test void anMcaBucketTraditionIsAlwaysOffered() {
        ResourceLocation bucket = ResourceLocation.tryParse("mca:japan");
        assertFalse(NamingTraditions.heldByCultures(List.of(root("off", bucket))).contains(bucket));
    }
}
