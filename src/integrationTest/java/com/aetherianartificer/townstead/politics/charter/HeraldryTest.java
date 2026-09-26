package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.heraldry.*;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HeraldryTest {
    @Test
    void publicationPersistenceAndMalformedRecipes() {
        var saved = new HeraldrySavedData(); var author = UUID.randomUUID();
        var first = EmblemRecipe.DEFAULT;
        var second = new EmblemRecipe(14, "", 0, "minecraft:circle", 4);
        assertTrue(saved.publish("polity:test:one", first, 0, author, 1), "First publication rejected");
        assertTrue(!saved.publish("polity:test:one", second, 0, author, 2), "Stale draft overwrote published design");
        assertTrue(saved.get("organization:test:one").revision() == 0, "Faction publication leaked into organization");
        assertTrue(saved.publish("polity:test:one", second, 1, author, 2), "Current revision rejected");
        assertTrue(saved.publish("polity:test:one", second, 2, author, 3) && saved.get("polity:test:one").revision() == 2, "Repeated identical publication advanced revision");
        //? if >=1.21 {
        var tag = saved.save(new CompoundTag(), null); var copy = HeraldrySavedData.load(tag, null);
        //?} else {
        /*var tag = saved.save(new CompoundTag()); var copy = HeraldrySavedData.load(tag);
        *///?}
        assertTrue(copy.get("polity:test:one").equals(saved.get("polity:test:one")), "Recipe/author/revision lost on reload");
        assertTrue(tag.getList("actors", 10).getCompound(0).getList("history", 10).size() == 1, "Previous design lost");
        for (String bad : new String[]{"2;0;;0;;0", "1;16;;0;;0", "1;-1;;0;;0", "1;0;not an id;0;;0", "1;0;;0;;0;extra"}) {
            boolean rejected = false;
            try { EmblemRecipe.decode(bad); } catch (RuntimeException expected) { rejected = true; }
            assertTrue(rejected, "Malformed recipe accepted: " + bad);
        }
        //? if >=1.21 {
        var registry = new net.minecraft.core.MappedRegistry<net.minecraft.world.level.block.entity.BannerPattern>(
                net.minecraft.core.registries.Registries.BANNER_PATTERN, com.mojang.serialization.Lifecycle.stable());
        for (String id : new String[]{"minecraft:stripe_center", "minecraft:rhombus", "custom:flower"}) {
            var key = net.minecraft.resources.ResourceLocation.tryParse(id);
            net.minecraft.core.Registry.register(registry, key, new net.minecraft.world.level.block.entity.BannerPattern(key, "test.pattern"));
        }
        registry.freeze();
        var access = new net.minecraft.core.RegistryAccess.ImmutableRegistryAccess(java.util.List.of(registry));
        assertTrue(EmblemItems.valid(access, first), "Registered recipe rejected");
        assertTrue(!EmblemItems.valid(access, second), "Missing registry pattern accepted");
        var custom = new EmblemRecipe(4, "", 0, "custom:flower", 15);
        assertTrue(EmblemItems.valid(access, custom), "Modded registered pattern rejected");
        //?}
        assertTrue(EmblemRecipe.decode(first.encode()).equals(first), "Recipe roundtrip failed");
        assertTrue(EmblemRecipe.safe("missing").equals(first), "Missing recipe has no stable fallback");
    }
}
