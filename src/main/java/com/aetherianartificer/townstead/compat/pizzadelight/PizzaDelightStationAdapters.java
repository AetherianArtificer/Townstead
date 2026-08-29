package com.aetherianartificer.townstead.compat.pizzadelight;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;

import com.aetherianartificer.townstead.work.station.StationAdapters;
import com.aetherianartificer.townstead.work.station.StationAdapters.StationPhase;
import com.aetherianartificer.townstead.work.station.BlockInventories;
import com.aetherianartificer.townstead.work.station.StationProtocols;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
//?} else if forge {
/*import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
*///?}
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Pizza Delight's method-bound station interactions, mirrored move for move. The basin
 * takes milk and a fermenting item by right-click (its item handler refuses insertion), so the
 * adapter invokes those same interaction methods with their documented nullable player —
 * consuming the villager's real milk bucket, handing the empty bucket back, shrinking the real
 * fermenting item. The finished pizza is lifted exactly as a peel-holding player lifts it:
 * {@code pickUpPizza} (no Player parameter) airs the block and throws the pizza item into the
 * world for pickup. Everything reflective degrades to a refusal, never a crash.
 */
final class PizzaDelightStationAdapters {

    static final String BASIN_ADAPTER = "townstead:pizzadelight_basin";
    static final String PIZZA_STATION_ADAPTER = "townstead:pizzadelight_station";
    static final String PIZZA_ADAPTER = "townstead:pizzadelight_pizza";

    private static final TagKey<net.minecraft.world.item.Item> FERMENTING_ITEMS =
            TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    ResourceLocation.tryParse("pizzadelight:fermenting_items"));

    private PizzaDelightStationAdapters() {}

    static void bootstrap() {
        StationAdapters.register(BASIN_ADAPTER, new BasinAdapter());
        StationAdapters.register(PIZZA_STATION_ADAPTER, new PizzaStationAdapter());
        StationAdapters.register(PIZZA_ADAPTER, new PizzaHarvestAdapter());
    }

    /**
     * The Pizza Station is not recipe-backed. Its menu asks {@code PizzaCalculator} to compose a
     * component-bearing raw pizza from three physical slot groups: base, sauce and toppings. A
     * generic item-handler adapter can fill those slots, but it cannot press the menu's result
     * calculation, which is why the station used to contribute nothing to an Order Sheet.
     */
    static final class PizzaStationAdapter implements StationAdapters.Adapter {

        private static final int OUTPUT_SLOT = 0;
        private static final int BASE_SLOT = 1;
        private static final int SAUCE_SLOT = 2;
        private static final int FIRST_TOPPING_SLOT = 3;
        /** Pizza Delight's menu exposes nine topping slots, numbered 3 through 11. */
        private static final int TOPPING_SLOTS_END = 12;
        /** The calculator reserves its tenth cell for sauce. */
        private static final int CALCULATOR_SLOTS = 10;
        private static final int MINIMUM_SLOTS = 12;

        @Override
        public StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                  @Nullable DiscoveredRecipe recipe) {
            ItemStackHandler handler = stationHandler(level, anchor);
            if (handler == null || handler.getSlots() < MINIMUM_SLOTS) return StationPhase.FOREIGN;
            if (!handler.getStackInSlot(OUTPUT_SLOT).isEmpty()) return StationPhase.READY;
            for (int slot = BASE_SLOT; slot < Math.min(TOPPING_SLOTS_END, handler.getSlots()); slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) return StationPhase.WORKING;
            }
            return StationPhase.IDLE;
        }

        @Override
        public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                              WorkstationDef def, DiscoveredRecipe recipe) {
            ItemStackHandler handler = stationHandler(level, anchor);
            if (handler == null || handler.getSlots() < MINIMUM_SLOTS) {
                return false;
            }
            if (phase(level, anchor, def, recipe) != StationPhase.IDLE) return false;

            List<ItemStack> staged = new ArrayList<>();
            for (var ingredient : recipe.inputs()) {
                for (int count = 0; count < ingredient.count(); count++) {
                    ItemStack one = StationProtocols.takeMatchingIngredient(villager, ingredient);
                    if (one.isEmpty()) {
                        rollback(handler, villager, staged);
                        return false;
                    }
                    int slot = acceptingSlot(handler, one);
                    if (slot < 0) {
                        StationProtocols.giveBack(villager, one);
                        rollback(handler, villager, staged);
                        return false;
                    }
                    handler.insertItem(slot, one, false);
                    staged.add(one.copy());
                }
            }

            ItemStack result = calculateResult(handler);
            if (result.isEmpty()) {
                rollback(handler, villager, staged);
                return false;
            }
            handler.setStackInSlot(OUTPUT_SLOT, result);
            BlockEntity be = level.getBlockEntity(anchor);
            if (be != null) be.setChanged();
            return true;
        }

        /** Let the station's own validation decide whether an item is base, sauce or topping. */
        private static int acceptingSlot(ItemStackHandler handler, ItemStack stack) {
            int[] preferred = {BASE_SLOT, SAUCE_SLOT};
            for (int slot : preferred) {
                if (handler.insertItem(slot, stack, true).isEmpty()) return slot;
            }
            for (int slot = FIRST_TOPPING_SLOT;
                 slot < Math.min(TOPPING_SLOTS_END, handler.getSlots()); slot++) {
                if (handler.insertItem(slot, stack, true).isEmpty()) return slot;
            }
            return -1;
        }

        /**
         * Invoke Pizza Delight's calculator so toppings and sauce survive on the returned stack.
         * This deliberately avoids a compile-time dependency on an optional mod class.
         */
        private static ItemStack calculateResult(ItemStackHandler station) {
            try {
                ItemStackHandler toppings = new ItemStackHandler(CALCULATOR_SLOTS);
                for (int slot = FIRST_TOPPING_SLOT;
                     slot < Math.min(TOPPING_SLOTS_END, station.getSlots()); slot++) {
                    toppings.setStackInSlot(slot - FIRST_TOPPING_SLOT,
                            station.getStackInSlot(slot).copy());
                }
                Class<?> calculatorType = Class.forName("com.tiviacz.pizzadelight.common.PizzaCalculator");
                Constructor<?> constructor = null;
                for (Constructor<?> candidate : calculatorType.getConstructors()) {
                    Class<?>[] parameters = candidate.getParameterTypes();
                    if (parameters.length == 3
                            && parameters[0] == ItemStack.class
                            && parameters[1] == ItemStack.class
                            && parameters[2].isInstance(toppings)) {
                        constructor = candidate;
                        break;
                    }
                }
                if (constructor == null) return ItemStack.EMPTY;
                Object calculator = constructor.newInstance(
                        station.getStackInSlot(BASE_SLOT).copy(),
                        station.getStackInSlot(SAUCE_SLOT).copy(), toppings);
                Method resultMethod = calculatorType.getMethod("getResultStackBlock", ItemStack.class);
                ResourceLocation rawPizzaId = ResourceLocation.tryParse("pizzadelight:raw_pizza");
                if (rawPizzaId == null) return ItemStack.EMPTY;
                ItemStack blank = BuiltInRegistries.ITEM
                        .get(rawPizzaId).getDefaultInstance();
                Object result = resultMethod.invoke(calculator, blank);
                return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
            } catch (Throwable ignored) {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                               WorkstationDef def, DiscoveredRecipe recipe) {
            ItemStackHandler handler = stationHandler(level, anchor);
            if (handler == null || handler.getSlots() < MINIMUM_SLOTS) {
                return false;
            }
            ItemStack output = handler.extractItem(OUTPUT_SLOT,
                    handler.getStackInSlot(OUTPUT_SLOT).getCount(), false);
            if (output.isEmpty()) return false;

            // PizzaStationResultSlot consumes one of every supplied ingredient on take.
            for (int slot = BASE_SLOT; slot < Math.min(TOPPING_SLOTS_END, handler.getSlots()); slot++) {
                ItemStack resident = handler.getStackInSlot(slot);
                if (resident.isEmpty()) continue;
                ItemStack consumed = handler.extractItem(slot, 1, false);
                ItemStack remainder = consumed.getCraftingRemainingItem();
                if (remainder != null && !remainder.isEmpty()) {
                    StationProtocols.giveBack(villager, remainder);
                }
            }
            StationProtocols.giveBack(villager, output);
            BlockEntity be = level.getBlockEntity(anchor);
            if (be != null) be.setChanged();
            return true;
        }

        private static void rollback(ItemStackHandler handler, VillagerEntityMCA villager,
                                     List<ItemStack> staged) {
            for (int slot = BASE_SLOT; slot < Math.min(TOPPING_SLOTS_END, handler.getSlots()); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    StationProtocols.giveBack(villager,
                            handler.extractItem(slot, stack.getCount(), false));
                }
            }
            staged.clear();
        }

        /**
         * Pizza Delight does not register its private item handler as a block capability on every
         * supported loader/version. Its block entity does, however, expose the same handler to its
         * own menu through {@code getInventory()}. Prefer the ordinary capability, then use that
         * public method as the compatibility bridge instead of declaring an empty station blocked.
         */
        private static @Nullable ItemStackHandler stationHandler(ServerLevel level, BlockPos anchor) {
            IItemHandler exposed = BlockInventories.itemHandler(level, anchor, null);
            if (exposed instanceof ItemStackHandler handler) return handler;
            BlockEntity be = level.getBlockEntity(anchor);
            if (be == null) return null;
            try {
                Object inventory = be.getClass().getMethod("getInventory").invoke(be);
                return inventory instanceof ItemStackHandler handler ? handler : null;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    }

    /** Basin content string from the block entity's own save data ("air"/"milk"/"fermenting_milk"/"cheese"). */
    private static @Nullable String basinContent(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        try {
            //? if >=1.21 {
            CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
            //?} else {
            /*CompoundTag tag = be.saveWithoutMetadata();
            *///?}
            return tag.contains("BasinContent") ? tag.getString("BasinContent") : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static final class BasinAdapter implements StationAdapters.Adapter {

        @Override
        public StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                  @Nullable DiscoveredRecipe recipe) {
            String content = basinContent(level, anchor);
            if (content == null) return StationPhase.FOREIGN;
            return switch (content) {
                case "air" -> StationPhase.IDLE;
                case "milk", "fermenting_milk" -> StationPhase.WORKING;
                case "cheese" -> StationPhase.READY;
                default -> StationPhase.FOREIGN;
            };
        }

        @Override
        public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                              WorkstationDef def, DiscoveredRecipe recipe) {
            BlockEntity be = level.getBlockEntity(anchor);
            if (be == null || !"air".equals(basinContent(level, anchor))) return false;
            // Pour the milk: consume the villager's real bucket, keep the empty one, exactly
            // as the player interaction does on its own side of the null-player split.
            ItemStack milk = com.aetherianartificer.townstead.work.station.StationProtocols
                    .takeOne(villager, Items.MILK_BUCKET);
            if (milk.isEmpty()) return false;
            try {
                be.getClass().getMethod("addMilk", Level.class, Player.class, InteractionHand.class)
                        .invoke(be, level, null, null);
            } catch (Throwable t) {
                com.aetherianartificer.townstead.work.station.StationProtocols
                        .giveBack(villager, milk);
                return false;
            }
            com.aetherianartificer.townstead.work.station.StationProtocols
                    .giveBack(villager, new ItemStack(Items.BUCKET));

            // Drop in the fermenting item; the method shrinks the held stack itself.
            for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
                ItemStack stack = villager.getInventory().getItem(i);
                if (stack.isEmpty() || !stack.is(FERMENTING_ITEMS)) continue;
                try {
                    be.getClass().getMethod("useFermentingItem", ItemStack.class, Level.class, Player.class)
                            .invoke(be, stack, level, null);
                } catch (Throwable t) {
                    return false;
                }
                return "fermenting_milk".equals(basinContent(level, anchor));
            }
            return false;
        }

        @Override
        public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                               WorkstationDef def, DiscoveredRecipe recipe) {
            // The finished cheese sits in the basin's capability slot; extracting it resets
            // the basin, the same state transition the player's take performs.
            StationAdapters.Adapter fallback = StationAdapters.byName(StationAdapters.DEFAULT_ITEM_HANDLER);
            return fallback != null && fallback.collect(level, villager, anchor, def, recipe);
        }
    }

    static final class PizzaHarvestAdapter implements StationAdapters.Adapter {

        @Override
        public StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                  @Nullable DiscoveredRecipe recipe) {
            // Place-surface phases are derived from the block ids by the protocol engine.
            return StationPhase.FOREIGN;
        }

        @Override
        public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                              WorkstationDef def, DiscoveredRecipe recipe) {
            // Composition runs through the placed block's own item handler in the engine.
            return false;
        }

        @Override
        public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                               WorkstationDef def, DiscoveredRecipe recipe) {
            BlockState state = level.getBlockState(anchor);
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (!blockId.equals(def.doneBlock())) return false;
            try {
                state.getBlock().getClass()
                        .getMethod("pickUpPizza", Level.class, BlockPos.class, BlockState.class,
                                net.minecraft.core.Direction.class)
                        .invoke(state.getBlock(), level, anchor, state, villager.getDirection().getOpposite());
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
