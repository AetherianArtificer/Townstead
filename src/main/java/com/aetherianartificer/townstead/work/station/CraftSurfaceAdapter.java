package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;

import com.aetherianartificer.townstead.work.station.StationAdapters.StationPhase;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Working at a surface that holds nothing: the crafter carries the inputs, spends the recipe's
 * time at the block, and the exchange happens in their hands. Insert only verifies the pockets
 * are full enough to start; the consume-and-produce is a single transaction at collect, so an
 * interrupted craft loses nothing — the materials are still in the villager's inventory,
 * exactly as a player's are when they walk away from a crafting table.
 */
public final class CraftSurfaceAdapter implements StationAdapters.Adapter {

    public static final String NAME = "townstead:craft_surface";

    private CraftSurfaceAdapter() {}

    public static void bootstrap() {
        StationAdapters.register(NAME, new CraftSurfaceAdapter());
    }

    @Override
    public StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def,
                              @Nullable DiscoveredRecipe recipe) {
        // The block has no contents to judge; whatever state a craft is in travels with the
        // crafter. Always ready for whoever stands here next.
        return StationPhase.IDLE;
    }

    @Override
    public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                          WorkstationDef def, DiscoveredRecipe recipe) {
        for (RecipeIngredient input : RecipeIngredient.merge(recipe.inputs())) {
            if (countMatching(villager, input) < input.count()) return false;
        }
        return true;
    }

    @Override
    public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                           WorkstationDef def, DiscoveredRecipe recipe) {
        Item output = BuiltInRegistries.ITEM.get(recipe.output());
        if (output == Items.AIR) return false;

        // Take everything first so a shortfall can hand it all back — the craft either happens
        // whole or not at all.
        List<ItemStack> taken = new ArrayList<>();
        for (RecipeIngredient input : RecipeIngredient.merge(recipe.inputs())) {
            for (int n = 0; n < input.count(); n++) {
                ItemStack one = takePlainMatching(villager, input);
                if (one.isEmpty()) {
                    for (ItemStack held : taken) StationProtocols.giveBack(villager, held);
                    return false;
                }
                taken.add(one);
            }
        }
        for (ItemStack consumed : taken) {
            ItemStack container = new ItemStack(consumed.getItem()).getCraftingRemainingItem();
            if (container != null && !container.isEmpty()) {
                StationProtocols.giveBack(villager, container);
            }
        }

        // A duplicating line's output is a copy of what was handed over — the map's identity
        // lives in the consumed stack, not in the output id.
        WorkstationDef.Produce produce = StationProtocols.produceFor(def, recipe);
        if (produce != null && produce.copies() != null) {
            ItemStack source = ItemStack.EMPTY;
            for (ItemStack consumed : taken) {
                if (produce.copies().equals(BuiltInRegistries.ITEM.getKey(consumed.getItem()))) {
                    source = consumed;
                    break;
                }
            }
            if (source.isEmpty()) {
                for (ItemStack held : taken) StationProtocols.giveBack(villager, held);
                return false;
            }
            // Count means COPIES: the original always comes back on top of them, so "copy this
            // x3" is three new maps and the master, never the master counted as produce.
            int stacksBack = Math.max(1, recipe.outputCount()) + 1;
            //? if >=1.21 {
            StationProtocols.giveBack(villager, source.copyWithCount(stacksBack));
            //?} else {
            /*ItemStack copy = source.copy();
            copy.setCount(stacksBack);
            StationProtocols.giveBack(villager, copy);
            *///?}
            return true;
        }

        StationProtocols.giveBack(villager,
                new ItemStack(output, Math.max(1, recipe.outputCount())));
        return true;
    }

    private static int countMatching(VillagerEntityMCA villager, RecipeIngredient input) {
        int count = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (!isPlainMatch(stack, input)) continue;
            count += stack.getCount();
        }
        return count;
    }

    private static ItemStack takePlainMatching(VillagerEntityMCA villager, RecipeIngredient input) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (isPlainMatch(stack, input)) return stack.split(1);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Only unworn, unenchanted stacks feed a craft. The netherite upgrade consumes a stored
     * diamond piece and produces a fresh one, so consuming the hero's enchanted chestplate
     * would quietly destroy the enchantments — a smith works from stock, not from heirlooms.
     */
    public static boolean isPlain(ItemStack stack) {
        return !stack.isDamaged() && !stack.isEnchanted();
    }

    private static boolean isPlainMatch(ItemStack stack, RecipeIngredient input) {
        if (stack.isEmpty() || !isPlain(stack)) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && input.itemIds().contains(id);
    }
}
