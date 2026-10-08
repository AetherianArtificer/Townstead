package com.aetherianartificer.townstead.switchboard;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps recipes for the items of a switched-off system out of players' recipe books, and gives them
 * back when the system returns. Crafting itself is blocked at the result slot; this only keeps the
 * book honest.
 */
public final class RecipeGates {
    private RecipeGates() {}

    //? if >=1.21 {
    private static RecipeManager cachedFor;
    private static List<net.minecraft.world.item.crafting.RecipeHolder<?>> townstead = List.of();
    //?} else {
    /*private static RecipeManager cachedFor;
    private static List<net.minecraft.world.item.crafting.Recipe<?>> townstead = List.of();
    *///?}

    /** Takes gated recipes out of this player's book. */
    public static void sync(ServerPlayer player) {
        try {
            //? if >=1.21 {
            List<net.minecraft.world.item.crafting.RecipeHolder<?>> off = new ArrayList<>();
            for (var holder : townsteadRecipes(player.server)) {
                if (!ContentGates.enabled(result(player.server, holder))) off.add(holder);
            }
            //?} else {
            /*List<net.minecraft.world.item.crafting.Recipe<?>> off = new ArrayList<>();
            for (var recipe : townsteadRecipes(player.server)) {
                if (!ContentGates.enabled(result(player.server, recipe))) off.add(recipe);
            }
            *///?}
            if (!off.isEmpty()) player.resetRecipes(off);
        } catch (RuntimeException e) {
            Townstead.LOGGER.debug("[Switchboard] Recipe book sync failed for {}", player.getName().getString(), e);
        }
    }

    /** Gives back the recipes of systems that are on again, then takes out any still gated. */
    public static void restore(ServerPlayer player) {
        try {
            //? if >=1.21 {
            List<net.minecraft.world.item.crafting.RecipeHolder<?>> on = new ArrayList<>();
            for (var holder : townsteadRecipes(player.server)) {
                if (ContentGates.enabled(result(player.server, holder))) on.add(holder);
            }
            //?} else {
            /*List<net.minecraft.world.item.crafting.Recipe<?>> on = new ArrayList<>();
            for (var recipe : townsteadRecipes(player.server)) {
                if (ContentGates.enabled(result(player.server, recipe))) on.add(recipe);
            }
            *///?}
            if (!on.isEmpty()) player.awardRecipes(on);
        } catch (RuntimeException e) {
            Townstead.LOGGER.debug("[Switchboard] Recipe book restore failed for {}", player.getName().getString(), e);
        }
        sync(player);
    }

    //? if >=1.21 {
    private static synchronized List<net.minecraft.world.item.crafting.RecipeHolder<?>> townsteadRecipes(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        if (manager != cachedFor) {
            List<net.minecraft.world.item.crafting.RecipeHolder<?>> out = new ArrayList<>();
            for (var holder : manager.getRecipes()) {
                if (isTownstead(result(server, holder))) out.add(holder);
            }
            townstead = List.copyOf(out);
            cachedFor = manager;
        }
        return townstead;
    }

    private static ItemStack result(MinecraftServer server, net.minecraft.world.item.crafting.RecipeHolder<?> holder) {
        return holder.value().getResultItem(server.registryAccess());
    }
    //?} else {
    /*private static synchronized List<net.minecraft.world.item.crafting.Recipe<?>> townsteadRecipes(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        if (manager != cachedFor) {
            List<net.minecraft.world.item.crafting.Recipe<?>> out = new ArrayList<>();
            for (var recipe : manager.getRecipes()) {
                if (isTownstead(result(server, recipe))) out.add(recipe);
            }
            townstead = List.copyOf(out);
            cachedFor = manager;
        }
        return townstead;
    }

    private static ItemStack result(MinecraftServer server, net.minecraft.world.item.crafting.Recipe<?> recipe) {
        return recipe.getResultItem(server.registryAccess());
    }
    *///?}

    private static boolean isTownstead(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && Townstead.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }
}
