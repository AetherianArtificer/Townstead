package com.aetherianartificer.townstead.work.recipe;

import com.aetherianartificer.townstead.supply.SupplyLines;
import com.aetherianartificer.townstead.work.order.OrderProducts;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Discovers the live vanilla/loader potion-mix graph through Minecraft's public brewing API. */
public final class PotionBrewingRecipes {
    public static final int BREW_TIME_TICKS = 400;

    public record Mix(ResourceLocation sourceProduct, ResourceLocation resultProduct,
                      ResourceLocation ingredient, int bottles) {}

    private static final Map<Object, List<DiscoveredRecipe>> CACHE =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ResourceLocation, Mix> BY_RECIPE = new ConcurrentHashMap<>();

    private PotionBrewingRecipes() {}

    /** All registered mixes, including loader-event and mod-added mixes, in deterministic order. */
    public static List<DiscoveredRecipe> discover(ServerLevel level) {
        if (level == null) return List.of();
        //? if >=1.21 {
        Object brewing = level.potionBrewing();
        //?} else {
        /*Object brewing = PotionBrewingRecipes.class;
        *///?}
        List<DiscoveredRecipe> cached = CACHE.get(brewing);
        if (cached != null) return cached;
        List<DiscoveredRecipe> built = build(level);
        CACHE.put(brewing, built);
        return built;
    }

    @Nullable
    public static ResourceLocation productKey(@Nullable ResourceLocation recipeId) {
        Mix mix = recipeId == null ? null : BY_RECIPE.get(recipeId);
        return mix == null ? null : mix.resultProduct();
    }

    @Nullable
    public static Mix mix(@Nullable ResourceLocation recipeId) {
        return recipeId == null ? null : BY_RECIPE.get(recipeId);
    }

    private static List<DiscoveredRecipe> build(ServerLevel level) {
        List<ResourceLocation> ingredients = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry
                : BuiltInRegistries.ITEM.entrySet()) {
            ItemStack stack = new ItemStack(entry.getValue());
            if (isIngredient(level, stack)) ingredients.add(entry.getKey().location());
        }
        ingredients.sort(Comparator.comparing(ResourceLocation::toString));

        List<Map.Entry<net.minecraft.resources.ResourceKey<net.minecraft.world.item.alchemy.Potion>,
                net.minecraft.world.item.alchemy.Potion>> potions =
                new ArrayList<>(BuiltInRegistries.POTION.entrySet());
        potions.sort(Comparator.comparing(entry -> entry.getKey().location().toString()));

        List<ResourceLocation> forms = List.of(
                BuiltInRegistries.ITEM.getKey(Items.POTION),
                BuiltInRegistries.ITEM.getKey(Items.SPLASH_POTION),
                BuiltInRegistries.ITEM.getKey(Items.LINGERING_POTION));
        List<DiscoveredRecipe> out = new ArrayList<>();
        for (ResourceLocation form : forms) {
            for (var potionEntry : potions) {
                ResourceLocation sourceProduct = OrderProducts.potionKey(
                        form, potionEntry.getKey().location());
                registerProductSupply(sourceProduct);
                ItemStack source = OrderProducts.displayStack(sourceProduct, form);
                if (source.isEmpty()) continue;
                for (ResourceLocation ingredient : ingredients) {
                    ItemStack reagent = new ItemStack(BuiltInRegistries.ITEM.get(ingredient));
                    ItemStack result = brew(level, reagent, source);
                    if (result.isEmpty()) continue;
                    ResourceLocation resultProduct = OrderProducts.key(result);
                    if (resultProduct.equals(sourceProduct)) continue;
                    ResourceLocation output = BuiltInRegistries.ITEM.getKey(result.getItem());
                    registerProductSupply(resultProduct);
                    // Largest viable vanilla batch is preferred; smaller forms keep a stand useful
                    // when the shelves hold only one or two source bottles.
                    for (int bottles = 3; bottles >= 1; bottles--) {
                        ResourceLocation recipeId = recipeId(
                                sourceProduct, ingredient, resultProduct, bottles);
                        Mix mix = new Mix(sourceProduct, resultProduct, ingredient, bottles);
                        BY_RECIPE.put(recipeId, mix);
                        out.add(new DiscoveredRecipe(
                                recipeId,
                                StationType.PASSIVE_STATION,
                                1,
                                output,
                                bottles,
                                BREW_TIME_TICKS,
                                false,
                                null,
                                0,
                                List.of(
                                        new RecipeIngredient(List.of(sourceProduct), bottles),
                                        new RecipeIngredient(List.of(ingredient), 1)),
                                false,
                                false,
                                null));
                    }
                }
            }
        }
        out.sort(Comparator
                .comparing((DiscoveredRecipe recipe) -> BY_RECIPE.get(recipe.id()).resultProduct().toString())
                .thenComparing(recipe -> BY_RECIPE.get(recipe.id()).sourceProduct().toString())
                .thenComparing(recipe -> BY_RECIPE.get(recipe.id()).ingredient().toString())
                .thenComparing(Comparator.comparingInt(
                        (DiscoveredRecipe recipe) -> BY_RECIPE.get(recipe.id()).bottles()).reversed()));
        return List.copyOf(out);
    }

    private static boolean isIngredient(ServerLevel level, ItemStack stack) {
        //? if >=1.21 {
        return level.potionBrewing().isIngredient(stack);
        //?} else {
        /*return net.minecraft.world.item.alchemy.PotionBrewing.isIngredient(stack);
        *///?}
    }

    private static ItemStack brew(ServerLevel level, ItemStack ingredient, ItemStack source) {
        //? if >=1.21 {
        if (!level.potionBrewing().hasMix(source, ingredient)) return ItemStack.EMPTY;
        return level.potionBrewing().mix(ingredient, source);
        //?} else {
        /*if (!net.minecraft.world.item.alchemy.PotionBrewing.hasMix(source, ingredient)) {
            return ItemStack.EMPTY;
        }
        return net.minecraft.world.item.alchemy.PotionBrewing.mix(ingredient, source);
        *///?}
    }

    private static void registerProductSupply(ResourceLocation product) {
        if (SupplyLines.byId(product) != null) return;
        SupplyLines.register(new SupplyLines.Line() {
            @Override public ResourceLocation id() { return product; }
            @Override public boolean active() { return true; }
            @Override public boolean matches(ItemStack stack, ServerLevel level) {
                return OrderProducts.matches(product, stack);
            }
        });
    }

    private static ResourceLocation recipeId(ResourceLocation source, ResourceLocation ingredient,
                                             ResourceLocation result, int bottles) {
        String signature = source + "|" + ingredient + "|" + result + "|" + bottles;
        String hash = Integer.toUnsignedString(signature.hashCode(), 36);
        return id("townstead", "potion_brewing/" + hash + "/" + bottles);
    }

    private static ResourceLocation id(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }
}
