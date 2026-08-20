package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.recipe.WaterPurificationItems;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
//? if >=1.21 {
import net.minecraft.world.item.crafting.RecipeHolder;
//?}
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Vanilla campfire mechanics behind the same adapter contract used by modded fire surfaces. */
public final class CampfireStationAdapter implements StationAdapters.Adapter {
    public static final String NAME = "townstead:campfire";

    public static void bootstrap() {
        StationAdapters.register(NAME, new CampfireStationAdapter());
    }

    @Override
    public boolean supportsPurification(ServerLevel level, BlockPos anchor, WorkstationDef def) {
        return capacity(level, anchor, def) > 0;
    }

    @Override
    public boolean insertPurification(ServerLevel level, VillagerEntityMCA villager,
                                      BlockPos anchor, WorkstationDef def,
                                      ThirstCompatBridge bridge) {
        BlockEntity be = level.getBlockEntity(anchor);
        if (!(be instanceof CampfireBlockEntity campfire)) return false;
        int loaded = 0;
        for (int attempt = 0; attempt < capacity(level, anchor, def); attempt++) {
            int slot = WaterPurificationItems.bestSlot(villager.getInventory(), bridge, stack -> !stack.isEmpty());
            if (slot < 0) break;
            ItemStack source = villager.getInventory().getItem(slot);
            ItemStack one = StationInventoryOps.copyOne(source);
            // Purification is performed by the thirst integration's cooking event and may not
            // have a normal campfire recipe, so use its recipe time when present and a short
            // deterministic cycle otherwise.
            var match = recipeFor(level, one);
            //? if >=1.21 {
            int cookTime = match.map(holder -> holder.value().getCookingTime()).orElse(100);
            //?} else {
            /*int cookTime = match.map(CampfireCookingRecipe::getCookingTime).orElse(100);
            *///?}
            if (!campfire.placeFood(villager, one, cookTime)) break;
            source.shrink(1);
            loaded++;
        }
        return loaded > 0;
    }

    @Override
    public boolean supports(ServerLevel level, BlockPos anchor, WorkstationDef def,
                            DiscoveredRecipe recipe) {
        if (!level.getBlockState(anchor).is(BlockTags.CAMPFIRES) || recipe.inputs().isEmpty()) return false;
        Item item = BuiltInRegistries.ITEM.get(recipe.inputs().get(0).primaryId());
        if (item == Items.AIR) return false;
        return recipeFor(level, new ItemStack(item))
                .filter(match -> StationRecipeMatch.produces(level, match, recipe.output()))
                .isPresent();
    }

    @Override
    public int capacity(ServerLevel level, BlockPos anchor, WorkstationDef def) {
        BlockEntity be = level.getBlockEntity(anchor);
        if (!(be instanceof CampfireBlockEntity campfire)) return 0;
        int free = 0;
        for (ItemStack stack : campfire.getItems()) if (stack.isEmpty()) free++;
        return free;
    }

    @Override
    public StationAdapters.StationPhase phase(
            ServerLevel level, BlockPos anchor, WorkstationDef def,
            @Nullable DiscoveredRecipe recipe
    ) {
        if (StationDropOutputs.has(level, anchor, WorkRecipeRegistry.allOutputIds(level))) {
            return StationAdapters.StationPhase.READY;
        }
        BlockEntity be = level.getBlockEntity(anchor);
        if (!(be instanceof CampfireBlockEntity campfire)) return StationAdapters.StationPhase.FOREIGN;
        for (ItemStack stack : campfire.getItems()) {
            if (!stack.isEmpty()) return StationAdapters.StationPhase.WORKING;
        }
        return StationAdapters.StationPhase.IDLE;
    }

    @Override
    public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                          WorkstationDef def, DiscoveredRecipe recipe) {
        BlockEntity be = level.getBlockEntity(anchor);
        if (!(be instanceof CampfireBlockEntity campfire) || recipe.inputs().isEmpty()) return false;
        RecipeIngredient ingredient = recipe.inputs().get(0);
        int loads = capacity(level, anchor, def);
        boolean inserted = false;
        for (int load = 0; load < loads; load++) {
            ItemStack input = take(villager, ingredient);
            if (input.isEmpty()) break;
            var match = recipeFor(level, input);
            if (match.isEmpty() || !StationRecipeMatch.produces(level, match.get(), recipe.output())) {
                StationProtocols.giveBack(villager, input);
                break;
            }
            //? if >=1.21 {
            int cookTime = match.get().value().getCookingTime();
            //?} else {
            /*int cookTime = match.get().getCookingTime();
            *///?}
            if (!campfire.placeFood(villager, input, cookTime)) {
                StationProtocols.giveBack(villager, input);
                break;
            }
            inserted = true;
        }
        return inserted;
    }

    @Override
    public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                           WorkstationDef def, DiscoveredRecipe recipe) {
        return collectDrops(level, villager, anchor);
    }

    @Override
    public boolean collectAvailable(ServerLevel level, VillagerEntityMCA villager,
                                    BlockPos anchor, WorkstationDef def) {
        return collectDrops(level, villager, anchor);
    }

    private boolean collectDrops(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        List<ItemStack> outputs = StationDropOutputs.collect(level, anchor, WorkRecipeRegistry.allOutputIds(level));
        for (ItemStack output : outputs) StationProtocols.giveBack(villager, output);
        return !outputs.isEmpty();
    }

    private static ItemStack take(VillagerEntityMCA villager, RecipeIngredient ingredient) {
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            ItemStack stack = villager.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && ingredient.itemIds().contains(id)) return stack.split(1);
        }
        return ItemStack.EMPTY;
    }

    //? if >=1.21 {
    private static Optional<RecipeHolder<CampfireCookingRecipe>> recipeFor(ServerLevel level, ItemStack input) {
        for (RecipeHolder<CampfireCookingRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING)) {
            if (!holder.value().getIngredients().isEmpty()
                    && holder.value().getIngredients().get(0).test(input)) return Optional.of(holder);
        }
        return Optional.empty();
    }
    //?} else {
    /*private static Optional<CampfireCookingRecipe> recipeFor(ServerLevel level, ItemStack input) {
        for (CampfireCookingRecipe recipe
                : level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING)) {
            if (!recipe.getIngredients().isEmpty() && recipe.getIngredients().get(0).test(input)) return Optional.of(recipe);
        }
        return Optional.empty();
    }
    *///?}
}
