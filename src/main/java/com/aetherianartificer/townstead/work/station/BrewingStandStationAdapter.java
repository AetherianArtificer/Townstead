package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.order.OrderProducts;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.PotionBrewingRecipes;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Operates the vanilla Brewing Stand through its public Container contract. */
public final class BrewingStandStationAdapter implements StationAdapters.Adapter {
    public static final String NAME = "townstead:brewing_stand";
    private static final int INGREDIENT = 3;
    private static final int FUEL = 4;

    private BrewingStandStationAdapter() {}

    public static void bootstrap() {
        StationAdapters.register(NAME, new BrewingStandStationAdapter());
    }

    @Override
    public boolean supports(ServerLevel level, BlockPos anchor, WorkstationDef def,
                            DiscoveredRecipe recipe) {
        return stand(level, anchor) != null && PotionBrewingRecipes.mix(recipe.id()) != null;
    }

    @Override
    public int capacity(ServerLevel level, BlockPos anchor, WorkstationDef def) {
        return stand(level, anchor) == null ? 0 : 1;
    }

    @Override
    public List<RecipeIngredient> additionalInputs(ServerLevel level, BlockPos anchor,
                                                   WorkstationDef def,
                                                   DiscoveredRecipe recipe) {
        BrewingStandBlockEntity stand = stand(level, anchor);
        if (stand == null || fuelUses(level, stand) > 0 || stand.getItem(FUEL).is(Items.BLAZE_POWDER)) {
            return List.of();
        }
        return List.of(new RecipeIngredient(
                List.of(BuiltInRegistries.ITEM.getKey(Items.BLAZE_POWDER)), 1));
    }

    @Override
    public StationAdapters.StationPhase phase(ServerLevel level, BlockPos anchor,
                                              WorkstationDef def,
                                              @Nullable DiscoveredRecipe recipe) {
        BrewingStandBlockEntity stand = stand(level, anchor);
        if (stand == null) return StationAdapters.StationPhase.FOREIGN;
        if (recipe == null) {
            return bottlesEmpty(stand) && stand.getItem(INGREDIENT).isEmpty()
                    ? StationAdapters.StationPhase.IDLE
                    : StationAdapters.StationPhase.FOREIGN;
        }
        PotionBrewingRecipes.Mix mix = PotionBrewingRecipes.mix(recipe.id());
        if (mix == null) return StationAdapters.StationPhase.FOREIGN;
        if (countProduct(stand, mix.resultProduct()) >= mix.bottles()) {
            return StationAdapters.StationPhase.READY;
        }
        if (brewTime(level, stand) > 0 || countProduct(stand, mix.sourceProduct()) > 0) {
            return StationAdapters.StationPhase.WORKING;
        }
        if (!bottlesEmpty(stand)) return StationAdapters.StationPhase.FOREIGN;
        ItemStack reagent = stand.getItem(INGREDIENT);
        if (!reagent.isEmpty() && !mix.ingredient().equals(
                BuiltInRegistries.ITEM.getKey(reagent.getItem()))) {
            return StationAdapters.StationPhase.FOREIGN;
        }
        return StationAdapters.StationPhase.IDLE;
    }

    @Override
    public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                          WorkstationDef def, DiscoveredRecipe recipe) {
        BrewingStandBlockEntity stand = stand(level, anchor);
        PotionBrewingRecipes.Mix mix = PotionBrewingRecipes.mix(recipe.id());
        if (stand == null || mix == null) return false;

        int present = countProduct(stand, mix.sourceProduct());
        int needed = Math.max(0, mix.bottles() - present);
        if (present + emptyBottleSlots(stand) < mix.bottles()
                || inventoryCount(villager, mix.sourceProduct()) < needed) return false;

        ItemStack reagent = stand.getItem(INGREDIENT);
        if (!reagent.isEmpty() && !mix.ingredient().equals(
                BuiltInRegistries.ITEM.getKey(reagent.getItem()))) return false;
        if (reagent.isEmpty() && inventoryCount(villager, mix.ingredient()) < 1) return false;

        boolean needsFuel = fuelUses(level, stand) <= 0 && !stand.getItem(FUEL).is(Items.BLAZE_POWDER);
        if (needsFuel && inventoryCount(villager,
                BuiltInRegistries.ITEM.getKey(Items.BLAZE_POWDER)) < 1) return false;

        for (int slot = 0; slot < 3 && needed > 0; slot++) {
            if (!stand.getItem(slot).isEmpty()) continue;
            ItemStack one = takeOne(villager, stack -> OrderProducts.matches(mix.sourceProduct(), stack));
            if (one.isEmpty()) return false;
            stand.setItem(slot, one);
            needed--;
        }
        if (stand.getItem(INGREDIENT).isEmpty()) {
            ItemStack one = takeOne(villager, stack -> !stack.isEmpty()
                    && mix.ingredient().equals(BuiltInRegistries.ITEM.getKey(stack.getItem())));
            if (one.isEmpty()) return false;
            stand.setItem(INGREDIENT, one);
        }
        if (needsFuel) {
            ItemStack one = takeOne(villager, stack -> stack.is(Items.BLAZE_POWDER));
            if (one.isEmpty()) return false;
            stand.setItem(FUEL, one);
        }
        stand.setChanged();
        return true;
    }

    @Override
    public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                           WorkstationDef def, DiscoveredRecipe recipe) {
        BrewingStandBlockEntity stand = stand(level, anchor);
        PotionBrewingRecipes.Mix mix = PotionBrewingRecipes.mix(recipe.id());
        if (stand == null || mix == null) return false;
        int collected = 0;
        for (int slot = 0; slot < 3; slot++) {
            ItemStack stack = stand.getItem(slot);
            if (!OrderProducts.matches(mix.resultProduct(), stack)) continue;
            ItemStack taken = stand.removeItem(slot, stack.getCount());
            if (!taken.isEmpty()) {
                collected += taken.getCount();
                StationProtocols.giveBack(villager, taken);
            }
        }
        if (collected > 0) stand.setChanged();
        return collected > 0;
    }

    @Override
    public boolean hasPendingInputs(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                    DiscoveredRecipe recipe) {
        BrewingStandBlockEntity stand = stand(level, anchor);
        PotionBrewingRecipes.Mix mix = PotionBrewingRecipes.mix(recipe.id());
        return stand != null && mix != null
                && (brewTime(level, stand) > 0
                || countProduct(stand, mix.sourceProduct()) > 0);
    }

    @Override
    public boolean matchesPendingInputs(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                        DiscoveredRecipe recipe) {
        return hasPendingInputs(level, anchor, def, recipe);
    }

    @Nullable
    private static BrewingStandBlockEntity stand(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand ? stand : null;
    }

    private static boolean bottlesEmpty(BrewingStandBlockEntity stand) {
        return stand.getItem(0).isEmpty() && stand.getItem(1).isEmpty()
                && stand.getItem(2).isEmpty();
    }

    private static int emptyBottleSlots(BrewingStandBlockEntity stand) {
        int count = 0;
        for (int slot = 0; slot < 3; slot++) if (stand.getItem(slot).isEmpty()) count++;
        return count;
    }

    private static int countProduct(BrewingStandBlockEntity stand, ResourceLocation product) {
        int count = 0;
        for (int slot = 0; slot < 3; slot++) {
            ItemStack stack = stand.getItem(slot);
            if (OrderProducts.matches(product, stack)) count += stack.getCount();
        }
        return count;
    }

    private static int inventoryCount(VillagerEntityMCA villager, ResourceLocation idOrProduct) {
        int count = 0;
        var inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (OrderProducts.decodePotion(idOrProduct) != null
                    ? OrderProducts.matches(idOrProduct, stack)
                    : idOrProduct.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ItemStack takeOne(VillagerEntityMCA villager,
                                     java.util.function.Predicate<ItemStack> predicate) {
        var inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!predicate.test(stack)) continue;
            return stack.split(1);
        }
        return ItemStack.EMPTY;
    }

    private static int brewTime(ServerLevel level, BrewingStandBlockEntity stand) {
        return saved(level, stand).getShort("BrewTime");
    }

    private static int fuelUses(ServerLevel level, BrewingStandBlockEntity stand) {
        return Byte.toUnsignedInt(saved(level, stand).getByte("Fuel"));
    }

    /** Public live-fuel probe used by the Order Sheet's availability explanation. */
    public static boolean hasFuel(ServerLevel level, BlockPos pos) {
        BrewingStandBlockEntity stand = stand(level, pos);
        return stand != null && (fuelUses(level, stand) > 0
                || stand.getItem(FUEL).is(Items.BLAZE_POWDER));
    }

    private static net.minecraft.nbt.CompoundTag saved(ServerLevel level,
                                                        BrewingStandBlockEntity stand) {
        //? if >=1.21 {
        return stand.saveWithoutMetadata(level.registryAccess());
        //?} else {
        /*return stand.saveWithoutMetadata();
        *///?}
    }
}
