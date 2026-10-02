package com.aetherianartificer.townstead.compat.weaversparadise;

import com.aetherianartificer.townstead.villager.ProfessionProgressions;
import com.aetherianartificer.townstead.villager.ProgressionSpec;
import com.aetherianartificer.townstead.work.order.WorksiteOrders;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.station.CraftSurfaceAdapter;
import com.aetherianartificer.townstead.work.station.StationAdapters;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
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
 * Works Weavers' Paradise's spinning jenny and clothcrafting station the way a crafting surface
 * is worked: the villager carries the inputs, spends the recipe's time at the block, and the
 * exchange happens in their hands. The block's own inventory and the player's minigame are left
 * alone.
 *
 * <p>Quality comes from rank. The clothcrafting recipe scores a player's minigame and picks an
 * output tier; here the villager's career rank is mapped onto the same score range, so a master
 * tailor sews the top tier and an apprentice the bottom one. The spool return the recipe
 * declares comes back with the cloth.</p>
 */
public final class WeaversParadiseStationAdapter implements StationAdapters.Adapter {

    public static final String NAME = "townstead:weaversparadise_station";

    private WeaversParadiseStationAdapter() {}

    public static void bootstrap() {
        StationAdapters.register(NAME, new WeaversParadiseStationAdapter());
    }

    @Override
    public StationAdapters.StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                              @Nullable DiscoveredRecipe recipe) {
        return StationAdapters.StationPhase.IDLE;
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

        List<ItemStack> taken = new ArrayList<>();
        for (RecipeIngredient input : RecipeIngredient.merge(recipe.inputs())) {
            for (int n = 0; n < input.count(); n++) {
                ItemStack one = takePlainMatching(villager, input);
                if (one.isEmpty()) {
                    for (ItemStack held : taken) giveBack(villager, held);
                    return false;
                }
                taken.add(one);
            }
        }

        WeaversParadiseRecipes.ClothcraftingInfo info = WeaversParadiseRecipes.clothcrafting(recipe.id());
        if (info != null) {
            WeaversParadiseRecipes.Tier tier = info.forScore(scoreFor(villager, info));
            if (tier == null || tier.result().isEmpty()) {
                for (ItemStack held : taken) giveBack(villager, held);
                return false;
            }
            ItemStack cloth = tier.result().copy();
            cloth.setCount(Math.max(1, tier.count()));
            giveBack(villager, cloth);
            if (!info.spoolReturn().isEmpty() && info.spoolReturnCount() > 0) {
                ItemStack spools = info.spoolReturn().copy();
                spools.setCount(info.spoolReturnCount());
                giveBack(villager, spools);
            }
            return true;
        }

        giveBack(villager, new ItemStack(output, Math.max(1, recipe.outputCount())));
        return true;
    }

    /** Rank one scores zero, the top rank scores the recipe's maximum, ranks between fall in order. */
    static int scoreFor(VillagerEntityMCA villager, WeaversParadiseRecipes.ClothcraftingInfo info) {
        int rank = WorksiteOrders.rankOf(villager);
        int maxRank = maxRankOf(villager);
        return scoreFor(rank, maxRank, info.maxScore());
    }

    static int scoreFor(int rank, int maxRank, int maxScore) {
        if (maxRank <= 1 || maxScore <= 0) return maxScore;
        double fraction = (Math.max(1, Math.min(rank, maxRank)) - 1) / (double) (maxRank - 1);
        return (int) Math.round(fraction * maxScore);
    }

    private static int maxRankOf(VillagerEntityMCA villager) {
        ResourceLocation career = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        if (career == null) return 1;
        ProgressionSpec spec = ProfessionProgressions.spec(career);
        return spec == null ? 1 : Math.max(1, spec.maxTier());
    }

    private static int countMatching(VillagerEntityMCA villager, RecipeIngredient input) {
        int count = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (isPlainMatch(stack, input)) count += stack.getCount();
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

    private static boolean isPlainMatch(ItemStack stack, RecipeIngredient input) {
        if (stack.isEmpty() || !CraftSurfaceAdapter.isPlain(stack)) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && input.itemIds().contains(id);
    }

    private static void giveBack(VillagerEntityMCA villager, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack leftover = villager.getInventory().addItem(stack);
        if (!leftover.isEmpty()) villager.spawnAtLocation(leftover);
    }
}
