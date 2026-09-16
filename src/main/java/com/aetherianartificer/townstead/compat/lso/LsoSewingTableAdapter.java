package com.aetherianartificer.townstead.compat.lso;

import com.aetherianartificer.townstead.work.order.ModifiedProducts;
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
 * Works LSO's sewing table. String recipes are ordinary crafting-surface work. A coat commission
 * is the one thing a surface cannot do: the villager carries the commissioned armor piece and a
 * coat, and the coat is sewn onto that very piece through the mod's own API, so the piece that
 * comes back is the one the player handed over.
 */
public final class LsoSewingTableAdapter implements StationAdapters.Adapter {

    public static final String NAME = "townstead:lso_sewing_table";

    private LsoSewingTableAdapter() {}

    public static void bootstrap() {
        StationAdapters.register(NAME, new LsoSewingTableAdapter());
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
            if (countMatching(villager, input, isCommission(recipe)) < input.count()) return false;
        }
        return true;
    }

    @Override
    public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                           WorkstationDef def, DiscoveredRecipe recipe) {
        Item output = BuiltInRegistries.ITEM.get(recipe.output());
        if (output == Items.AIR) return false;
        boolean commission = isCommission(recipe);

        List<ItemStack> taken = new ArrayList<>();
        for (RecipeIngredient input : RecipeIngredient.merge(recipe.inputs())) {
            for (int n = 0; n < input.count(); n++) {
                ItemStack one = takeMatching(villager, input, commission);
                if (one.isEmpty()) {
                    for (ItemStack held : taken) giveBack(villager, held);
                    return false;
                }
                taken.add(one);
            }
        }

        if (!commission) {
            giveBack(villager, new ItemStack(output, Math.max(1, recipe.outputCount())));
            return true;
        }

        ItemStack piece = ItemStack.EMPTY;
        ItemStack coat = ItemStack.EMPTY;
        for (ItemStack held : taken) {
            if (piece.isEmpty() && held.getItem() == output) piece = held;
            else if (coat.isEmpty() && LsoCoats.coatIdOf(held.getItem()) != null) coat = held;
        }
        if (piece.isEmpty() || coat.isEmpty() || !LsoCoats.apply(piece, coat)) {
            for (ItemStack held : taken) giveBack(villager, held);
            return false;
        }
        for (ItemStack held : taken) {
            if (held != piece && held != coat) giveBack(villager, held);
        }
        giveBack(villager, piece);
        return true;
    }

    static boolean isCommission(DiscoveredRecipe recipe) {
        return recipe != null && ModifiedProducts.decodeRecipe(recipe.id()) != null;
    }

    /** A commission works the handed-over piece as it is, worn and enchanted included. */
    private static boolean matches(ItemStack stack, RecipeIngredient input, boolean commission) {
        if (stack.isEmpty()) return false;
        if (!commission && !CraftSurfaceAdapter.isPlain(stack)) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && input.itemIds().contains(id);
    }

    private static int countMatching(VillagerEntityMCA villager, RecipeIngredient input, boolean commission) {
        int count = 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (matches(stack, input, commission)) count += stack.getCount();
        }
        return count;
    }

    private static ItemStack takeMatching(VillagerEntityMCA villager, RecipeIngredient input, boolean commission) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (matches(stack, input, commission)) return stack.split(1);
        }
        return ItemStack.EMPTY;
    }

    private static void giveBack(VillagerEntityMCA villager, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemStack leftover = villager.getInventory().addItem(stack);
        if (!leftover.isEmpty()) villager.spawnAtLocation(leftover);
    }
}
