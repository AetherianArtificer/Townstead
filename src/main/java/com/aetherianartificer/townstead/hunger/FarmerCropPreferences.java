package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.compat.farming.FarmerCropCompatRegistry;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.neoforged.neoforge.common.Tags;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public final class FarmerCropPreferences {
    public enum CropCategory {
        STAPLE,
        CASH,
        FAST,
        LUXURY,
        INDUSTRIAL,
        FODDER,
        SEED_OIL,
        FIBER,
        MEDICINAL,
        DYE,
        BREW,
        SPICE,
        FRUIT,
        VEGETABLE,
        PROTEIN_CROP
    }

    public record CropTraits(
            EnumSet<CropCategory> categories,
            int yieldScore,
            int growthSpeed,
            int hydrationNeed,
            int laborCost,
            int valueScore,
            int riskScore
    ) {}

    private static final CropTraits NEUTRAL_TRAITS = traits(
            3, 3, 3, 3, 3, 3,
            CropCategory.STAPLE, CropCategory.CASH
    );

    private static final Map<Item, CropTraits> VANILLA = Map.ofEntries(
            Map.entry(Items.WHEAT_SEEDS, traits(3, 3, 4, 3, 2, 2, CropCategory.STAPLE, CropCategory.FODDER, CropCategory.SEED_OIL)),
            Map.entry(Items.CARROT, traits(3, 4, 4, 2, 2, 2, CropCategory.STAPLE, CropCategory.VEGETABLE, CropCategory.FAST)),
            Map.entry(Items.POTATO, traits(4, 4, 4, 2, 2, 2, CropCategory.STAPLE, CropCategory.VEGETABLE, CropCategory.FAST)),
            Map.entry(Items.BEETROOT_SEEDS, traits(2, 3, 4, 3, 3, 2, CropCategory.STAPLE, CropCategory.VEGETABLE, CropCategory.CASH)),
            Map.entry(Items.PUMPKIN_SEEDS, traits(4, 2, 1, 2, 3, 2, CropCategory.STAPLE, CropCategory.INDUSTRIAL, CropCategory.CASH)),
            Map.entry(Items.MELON_SEEDS, traits(5, 3, 1, 2, 3, 2, CropCategory.FRUIT, CropCategory.CASH, CropCategory.FAST)),
            Map.entry(Items.COCOA_BEANS, traits(2, 4, 1, 3, 4, 3, CropCategory.SPICE, CropCategory.CASH, CropCategory.DYE)),
            Map.entry(Items.SWEET_BERRIES, traits(3, 4, 1, 2, 3, 2, CropCategory.FRUIT, CropCategory.FAST, CropCategory.MEDICINAL)),
            Map.entry(Items.GLOW_BERRIES, traits(3, 3, 1, 3, 4, 3, CropCategory.FRUIT, CropCategory.LUXURY, CropCategory.MEDICINAL)),
            Map.entry(Items.SUGAR_CANE, traits(4, 3, 1, 1, 4, 2, CropCategory.CASH, CropCategory.INDUSTRIAL, CropCategory.FIBER)),
            Map.entry(Items.BAMBOO, traits(5, 5, 1, 1, 2, 1, CropCategory.INDUSTRIAL, CropCategory.FIBER, CropCategory.FAST)),
            Map.entry(Items.CACTUS, traits(3, 2, 1, 1, 3, 2, CropCategory.INDUSTRIAL, CropCategory.DYE, CropCategory.CASH)),
            Map.entry(Items.KELP, traits(4, 5, 1, 1, 2, 2, CropCategory.INDUSTRIAL, CropCategory.FAST)),
            Map.entry(Items.NETHER_WART, traits(3, 3, 1, 2, 5, 3, CropCategory.BREW, CropCategory.CASH, CropCategory.SPICE)),
            Map.entry(Items.CHORUS_FLOWER, traits(2, 1, 1, 4, 5, 5, CropCategory.LUXURY, CropCategory.FRUIT, CropCategory.CASH)),
            Map.entry(Items.TORCHFLOWER_SEEDS, traits(1, 2, 4, 4, 4, 4, CropCategory.LUXURY, CropCategory.DYE)),
            Map.entry(Items.PITCHER_POD, traits(1, 2, 4, 4, 4, 4, CropCategory.LUXURY, CropCategory.DYE)),
            Map.entry(Items.BROWN_MUSHROOM, traits(2, 2, 1, 3, 3, 3, CropCategory.MEDICINAL, CropCategory.SPICE, CropCategory.LUXURY)),
            Map.entry(Items.RED_MUSHROOM, traits(2, 2, 1, 3, 3, 3, CropCategory.MEDICINAL, CropCategory.SPICE, CropCategory.LUXURY))
    );

    // Farmer's Delight crop traits — resolved lazily since FD items aren't available at compile time.
    private static volatile Map<Item, CropTraits> fdTraits;

    private static Map<Item, CropTraits> getFdTraits() {
        if (fdTraits != null) return fdTraits;
        Map<Item, CropTraits> map = new HashMap<>();
        resolve(map, "farmersdelight", "onion",
                traits(3, 3, 3, 3, 3, 2, CropCategory.VEGETABLE, CropCategory.SPICE, CropCategory.STAPLE));
        resolve(map, "farmersdelight", "cabbage_seeds",
                traits(4, 3, 3, 2, 2, 2, CropCategory.VEGETABLE, CropCategory.STAPLE));
        resolve(map, "farmersdelight", "rice",
                traits(4, 3, 5, 2, 3, 2, CropCategory.STAPLE, CropCategory.INDUSTRIAL));
        resolve(map, "farmersdelight", "tomato_seeds",
                traits(3, 3, 3, 2, 3, 2, CropCategory.FRUIT, CropCategory.VEGETABLE, CropCategory.CASH));
        fdTraits = Map.copyOf(map);
        return fdTraits;
    }

    private static void resolve(Map<Item, CropTraits> map, String namespace, String path, CropTraits traits) {
        BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(namespace, path))
                .ifPresent(item -> map.put(item, traits));
    }

    private FarmerCropPreferences() {}

    public static CropTraits traitsFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CropTraits vanilla = VANILLA.get(stack.getItem());
        if (vanilla != null) return vanilla;
        CropTraits fd = getFdTraits().get(stack.getItem());
        if (fd != null) return fd;
        if (!isSeedLike(stack)) return null;
        return NEUTRAL_TRAITS;
    }

    public static double scoreSeed(VillagerEntityMCA villager, ItemStack stack, boolean hasNearbyWater) {
        CropTraits traits = traitsFor(stack);
        if (traits == null) return Double.NEGATIVE_INFINITY;
        Personality p = villager.getVillagerBrain().getPersonality();
        double score = 0.0;
        for (CropCategory c : traits.categories()) {
            score += categoryWeight(p, c);
        }
        score += traits.yieldScore() * yieldWeight(p);
        score += traits.growthSpeed() * growthWeight(p);
        score += traits.valueScore() * valueWeight(p);
        score -= traits.riskScore() * riskWeight(p);
        score -= traits.laborCost() * laborWeight(p);
        if (!hasNearbyWater) {
            score -= traits.hydrationNeed() * dryPenaltyWeight(p);
        }
        return score;
    }

    public static boolean isSeedLike(ItemStack stack) {
        if (stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) return true;
        if (stack.is(Tags.Items.SEEDS)) return true;
        if (FarmerCropCompatRegistry.isSeed(stack)) return true;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        return blockItem.getBlock() instanceof CropBlock || blockItem.getBlock() instanceof StemBlock;
    }

    private static CropTraits traits(
            int yield, int growth, int hydration, int labor, int value, int risk, CropCategory... categories
    ) {
        EnumSet<CropCategory> set = EnumSet.noneOf(CropCategory.class);
        for (CropCategory category : categories) set.add(category);
        return new CropTraits(set, yield, growth, hydration, labor, value, risk);
    }

    private static double categoryWeight(Personality p, CropCategory c) {
        return switch (p) {
            case FRIENDLY -> pref(c, 2.0, CropCategory.STAPLE, CropCategory.FODDER, CropCategory.VEGETABLE);
            case FLIRTY -> pref(c, 2.2, CropCategory.LUXURY, CropCategory.FRUIT, CropCategory.SPICE);
            case PLAYFUL -> pref(c, 2.1, CropCategory.FAST, CropCategory.FRUIT, CropCategory.DYE);
            case GLOOMY -> pref(c, 2.0, CropCategory.STAPLE, CropCategory.MEDICINAL);
            case SENSITIVE -> pref(c, 2.0, CropCategory.MEDICINAL, CropCategory.STAPLE, CropCategory.FRUIT);
            case GREEDY -> pref(c, 2.5, CropCategory.CASH, CropCategory.BREW, CropCategory.LUXURY);
            case ODD -> pref(c, 2.3, CropCategory.SPICE, CropCategory.BREW, CropCategory.LUXURY);
            case CRABBY -> pref(c, 1.8, CropCategory.STAPLE, CropCategory.VEGETABLE);
            case EXTROVERTED -> pref(c, 2.2, CropCategory.CASH, CropCategory.FAST, CropCategory.FRUIT);
            case INTROVERTED -> pref(c, 2.0, CropCategory.STAPLE, CropCategory.MEDICINAL);
            case RELAXED -> pref(c, 1.8, CropCategory.STAPLE, CropCategory.INDUSTRIAL);
            case ANXIOUS -> pref(c, 2.1, CropCategory.FAST, CropCategory.STAPLE);
            case PEACEFUL -> pref(c, 2.0, CropCategory.STAPLE, CropCategory.FRUIT, CropCategory.MEDICINAL);
            case UPBEAT -> pref(c, 2.2, CropCategory.FAST, CropCategory.CASH, CropCategory.FRUIT);
            default -> pref(c, 1.6, CropCategory.STAPLE, CropCategory.VEGETABLE);
        };
    }

    private static double pref(CropCategory c, double hit, CropCategory... prefers) {
        for (CropCategory prefer : prefers) {
            if (c == prefer) return hit;
        }
        return 0.0;
    }

    private static double yieldWeight(Personality p) {
        return switch (p) {
            case GREEDY -> 0.55;
            case CRABBY, RELAXED -> 0.35;
            default -> 0.45;
        };
    }

    private static double growthWeight(Personality p) {
        return switch (p) {
            case PLAYFUL, EXTROVERTED, ANXIOUS, UPBEAT -> 0.55;
            case GLOOMY, CRABBY -> 0.30;
            default -> 0.40;
        };
    }

    private static double valueWeight(Personality p) {
        return switch (p) {
            case GREEDY, FLIRTY, EXTROVERTED -> 0.65;
            case GLOOMY, INTROVERTED -> 0.30;
            default -> 0.45;
        };
    }

    private static double riskWeight(Personality p) {
        return switch (p) {
            case ANXIOUS, SENSITIVE, PEACEFUL, INTROVERTED -> 0.45;
            case ODD, PLAYFUL -> 0.20;
            default -> 0.30;
        };
    }

    private static double laborWeight(Personality p) {
        return switch (p) {
            case CRABBY, RELAXED -> 0.35;
            case GREEDY, UPBEAT -> 0.20;
            default -> 0.25;
        };
    }

    private static double dryPenaltyWeight(Personality p) {
        return switch (p) {
            case ANXIOUS, SENSITIVE, PEACEFUL, INTROVERTED -> 0.65;
            case GREEDY, PLAYFUL, ODD -> 0.30;
            default -> 0.45;
        };
    }
}
