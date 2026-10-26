package com.aetherianartificer.townstead.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Items in {@code #townstead:learned_recipe} can be crafted, and show in recipe viewers, only once the
 * player has one of their recipes in their recipe book. Someone teaches it, such as an order's
 * founding, or the player finds the block and uses it.
 */
public final class LearnedRecipes {
    private LearnedRecipes() {}

    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM,
            ResourceLocation.tryParse("townstead:learned_recipe"));
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    public static boolean requires(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(TAG);
    }

    //? if >=1.21 {
    public static boolean allowed(Level level, ServerPlayer player, net.minecraft.world.item.crafting.RecipeHolder<?> recipe) {
        return !requires(recipe.value().getResultItem(level.registryAccess())) || player.getRecipeBook().contains(recipe);
    }
    //?} else {
    /*public static boolean allowed(Level level, ServerPlayer player, net.minecraft.world.item.crafting.Recipe<?> recipe) {
        return !requires(recipe.getResultItem(level.registryAccess())) || player.getRecipeBook().contains(recipe);
    }
    *///?}

    /** Adds every recipe that makes {@code item} to the player's recipe book. */
    public static void teach(ServerPlayer player, Item item) {
        if (!requires(new ItemStack(item))) return;
        //? if >=1.21 {
        List<net.minecraft.world.item.crafting.RecipeHolder<?>> recipes = new ArrayList<>();
        for (var holder : player.server.getRecipeManager().getRecipes()) {
            if (holder.value().getResultItem(player.level().registryAccess()).is(item)
                    && !player.getRecipeBook().contains(holder)) recipes.add(holder);
        }
        //?} else {
        /*List<net.minecraft.world.item.crafting.Recipe<?>> recipes = new ArrayList<>();
        for (var recipe : player.server.getRecipeManager().getRecipes()) {
            if (recipe.getResultItem(player.level().registryAccess()).is(item)
                    && !player.getRecipeBook().contains(recipe)) recipes.add(recipe);
        }
        *///?}
        if (!recipes.isEmpty()) player.awardRecipes(recipes);
    }

    /** Tagged items with no recipe in {@code book}. */
    public static List<Item> unlearned(net.minecraft.stats.RecipeBook book, RecipeManager manager,
                                       net.minecraft.core.RegistryAccess access) {
        List<Item> out = new ArrayList<>();
        if (book == null || manager == null) return out;
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(TAG)) {
            Item item = holder.value();
            boolean known = false;
            for (var recipe : manager.getRecipes()) {
                //? if >=1.21 {
                if (recipe.value().getResultItem(access).is(item) && book.contains(recipe)) { known = true; break; }
                //?} else {
                /*if (recipe.getResultItem(access).is(item) && book.contains(recipe)) { known = true; break; }
                *///?}
            }
            if (!known) out.add(item);
        }
        return out;
    }

    public static void onChange(Runnable listener) {
        LISTENERS.add(listener);
    }

    /** The local recipe book changed. */
    public static void changed() {
        for (Runnable listener : LISTENERS) listener.run();
    }
}
