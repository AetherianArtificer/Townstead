package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.compat.starcatcher.StarcatcherCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Teaches fishing-rod "cast" display-predicates that our villagers count as
 * "actively fishing" when they have a linked hook in the air. Vanilla
 * registers a {@code minecraft:cast} property function whose body requires
 * {@code entity instanceof Player && player.fishing != null}; Starcatcher
 * registers a {@code starcatcher:cast} property that checks their own
 * {@code FISHING_BOB} data attachment. Both reject villager entities by
 * construction, so our fishermen always display the non-cast rod texture
 * even while their line is in the water.
 *
 * This wrapper replaces each registered cast predicate with one that:
 *   1. Returns 1.0 when the holder is a Townstead villager currently
 *      linked to a live hook via {@link FishermanHookLinkStore}.
 *   2. Otherwise delegates to the originally registered predicate — so
 *      vanilla players and Starcatcher players still get their normal
 *      cast/retrieve transitions.
 *
 * Called once on client connect (LoggingIn), by which time every mod has
 * already finished registering its item properties.
 */
public final class FishingRodCastPredicates {
    //? if >=1.21 {
    private static final ResourceLocation VANILLA_CAST = ResourceLocation.withDefaultNamespace("cast");
    private static final ResourceLocation STARCATCHER_CAST = ResourceLocation.fromNamespaceAndPath("starcatcher", "cast");
    private static final TagKey<Item> STARCATCHER_RODS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("starcatcher", "rods"));
    //?} else {
    /*private static final ResourceLocation VANILLA_CAST = new ResourceLocation("cast");
    private static final ResourceLocation STARCATCHER_CAST = new ResourceLocation("starcatcher", "cast");
    private static final TagKey<Item> STARCATCHER_RODS = TagKey.create(Registries.ITEM,
            new ResourceLocation("starcatcher", "rods"));
    *///?}

    private static boolean registered = false;

    private FishingRodCastPredicates() {}

    /**
     * Idempotent. Safe to call multiple times (e.g. on reconnect) — only
     * the first call actually wraps the predicates; subsequent calls would
     * otherwise stack wrapper-on-wrapper on every world join.
     */
    public static void registerOnce() {
        if (registered) return;
        registered = true;
        wrap(Items.FISHING_ROD, VANILLA_CAST);
        if (StarcatcherCompat.isLoaded()) {
            BuiltInRegistries.ITEM.getTag(STARCATCHER_RODS).ifPresent(holders ->
                    holders.forEach(holder -> wrap(holder.value(), STARCATCHER_CAST)));
        }
    }

    private static void wrap(Item item, ResourceLocation predicateKey) {
        //? if >=1.21 {
        ItemPropertyFunction existing = ItemProperties.getProperty(new ItemStack(item), predicateKey);
        //?} else {
        /*ItemPropertyFunction existing = ItemProperties.getProperty(item, predicateKey);
        *///?}
        ClampedItemPropertyFunction wrapped = (stack, level, entity, seed) -> {
            if (entity instanceof VillagerEntityMCA villager && isTownsteadFishing(villager)) {
                return 1.0F;
            }
            if (existing == null) return 0.0F;
            float v = existing.call(stack, level, entity, seed);
            if (v < 0.0F) return 0.0F;
            if (v > 1.0F) return 1.0F;
            return v;
        };
        ItemProperties.register(item, predicateKey, wrapped);
    }

    private static boolean isTownsteadFishing(VillagerEntityMCA villager) {
        int vid = villager.getId();
        for (Integer v : FishermanHookLinkStore.snapshot().values()) {
            if (v != null && v == vid) return true;
        }
        return false;
    }
}
