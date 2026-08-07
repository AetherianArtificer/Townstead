package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;

import com.aetherianartificer.townstead.work.station.StationAdapters.StationPhase;
import com.aetherianartificer.townstead.work.station.StationProtocols;
import com.aetherianartificer.townstead.supply.SupplyLines;
import com.aetherianartificer.townstead.supply.TownsteadSupplyLines;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Loading and unloading a furnace the way a player does: input in one slot, something that burns
 * in another, take the result out of the third. The block does the cooking on its own clock, so
 * this never simulates smelting — it stages, waits, and collects.
 *
 * <p>Slot numbers come from the def rather than from code, because "input 0, fuel 1, output 2" is
 * only vanilla's convention. A modded furnace that lays its slots out differently is a workstation
 * JSON with three extra lines, not a new adapter.</p>
 *
 * <p>Fuel is pulled by supply line rather than by item id, so anything the game considers burnable
 * qualifies, including whatever a mod adds after this was written.</p>
 */
public final class FurnaceStationAdapter implements StationAdapters.Adapter {

    public static final String NAME = "townstead:furnace";

    private FurnaceStationAdapter() {}

    public static void bootstrap() {
        StationAdapters.register(NAME, new FurnaceStationAdapter());
    }

    private static boolean slotInRange(FurnaceSlotAccess slots, int slot) {
        return slot >= 0 && slot < slots.size();
    }

    private static boolean slotsUsable(FurnaceSlotAccess access, WorkstationDef.FurnaceSlots slots) {
        return slotInRange(access, slots.input())
                && slotInRange(access, slots.fuel())
                && slotInRange(access, slots.output());
    }

    @Override
    public StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def,
                              @Nullable DiscoveredRecipe recipe) {
        FurnaceSlotAccess access = FurnaceSlotAccess.of(level, anchor);
        if (access == null) return StationPhase.FOREIGN;
        WorkstationDef.FurnaceSlots slots = def.furnaceSlots();
        if (!slotsUsable(access, slots)) return StationPhase.FOREIGN;

        // Anything finished outranks everything else: collect first, then judge the rest.
        if (!access.get(slots.output()).isEmpty()) return StationPhase.READY;

        // A furnace block can be more than a furnace. Iron Furnaces' augments retask the same
        // block entity into a factory (inputs move to other slots) or a generator (no smelting at
        // all), and both keep slot 0 present but refuse it. Asking the container whether it would
        // accept the job is the only honest test, and it costs one call.
        if (recipe != null && !acceptsInput(access, slots, recipe)) return StationPhase.FOREIGN;

        ItemStack input = access.get(slots.input());
        if (!input.isEmpty()) {
            // Someone else's smelt, or a block someone is using as storage. Either way it is not
            // ours to interrupt, and unlike a cooking pot a furnace has no "wrong contents" state
            // worth cleaning up — the item will finish and can be collected then.
            return StationPhase.WORKING;
        }
        return StationPhase.IDLE;
    }

    @Override
    public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                          WorkstationDef def, DiscoveredRecipe recipe) {
        FurnaceSlotAccess access = FurnaceSlotAccess.of(level, anchor);
        if (access == null) return false;
        WorkstationDef.FurnaceSlots slots = def.furnaceSlots();
        if (!slotsUsable(access, slots)) return false;
        if (!access.get(slots.input()).isEmpty()) return false;

        // Fuel first. Loading the input into a cold furnace with no fuel to follow would leave the
        // villager's food sitting in a block it cannot get back out cheaply.
        if (!ensureFuel(level, villager, access, slots.fuel())) return false;

        ItemStack staged = ItemStack.EMPTY;
        for (RecipeIngredient ingredient : recipe.inputs()) {
            if (isFuelIngredient(ingredient)) continue;
            staged = StationProtocols.takeMatchingIngredient(villager, ingredient);
            if (staged.isEmpty()) return false;
            break;
        }
        if (staged.isEmpty()) return false;

        ItemStack one = staged.split(1);
        StationProtocols.giveBack(villager, staged);
        if (!access.place(slots.input(), one)) {
            StationProtocols.giveBack(villager, one);
            return false;
        }
        return true;
    }

    /** Whether the block would accept this recipe's input in its current mode. */
    static boolean acceptsInput(FurnaceSlotAccess access, WorkstationDef.FurnaceSlots slots,
                                DiscoveredRecipe recipe) {
        for (RecipeIngredient ingredient : recipe.inputs()) {
            if (isFuelIngredient(ingredient)) continue;
            for (net.minecraft.resources.ResourceLocation itemId : ingredient.itemIds()) {
                net.minecraft.world.item.Item item =
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
                if (item == net.minecraft.world.item.Items.AIR) continue;
                if (access.canPlace(slots.input(), new ItemStack(item))) return true;
            }
            // First real ingredient decided it; a furnace takes one input.
            return false;
        }
        return false;
    }

    @Override
    public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                           WorkstationDef def, DiscoveredRecipe recipe) {
        return collectOutput(level, villager, anchor, def);
    }

    @Override
    public boolean collectAvailable(ServerLevel level, VillagerEntityMCA villager,
                                    BlockPos anchor, WorkstationDef def) {
        return collectOutput(level, villager, anchor, def);
    }

    private boolean collectOutput(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                                  WorkstationDef def) {
        FurnaceSlotAccess access = FurnaceSlotAccess.of(level, anchor);
        if (access == null) return false;
        WorkstationDef.FurnaceSlots slots = def.furnaceSlots();
        if (!slotInRange(access, slots.output())) return false;

        ItemStack output = access.takeAll(slots.output());
        if (output.isEmpty()) return false;
        StationProtocols.giveBack(villager, output);
        return true;
    }

    /** True when this ingredient is the synthetic fuel line rather than a real item. */
    private static boolean isFuelIngredient(RecipeIngredient ingredient) {
        return ingredient.itemIds().size() == 1
                && TownsteadSupplyLines.FURNACE_FUEL.equals(ingredient.itemIds().get(0));
    }

    /**
     * Tops the fuel slot up from the villager's own inventory if it is empty. A furnace that is
     * already burning keeps its own fuel, so this adds nothing when it does not need to.
     */
    private static boolean ensureFuel(ServerLevel level, VillagerEntityMCA villager,
                                      FurnaceSlotAccess access, int fuelSlot) {
        if (!access.get(fuelSlot).isEmpty()) return true;
        var matches = SupplyLines.matcher(level, TownsteadSupplyLines.FURNACE_FUEL);
        var inventory = villager.getInventory();
        int bestSlot = -1;
        int bestPreference = Integer.MIN_VALUE;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !matches.test(stack)) continue;
            int preference = SupplyLines.preference(level, TownsteadSupplyLines.FURNACE_FUEL, stack);
            if (bestSlot < 0 || preference > bestPreference) {
                bestSlot = i;
                bestPreference = preference;
            }
        }
        if (bestSlot < 0) return false;
        ItemStack stack = inventory.getItem(bestSlot);
        if (!access.place(fuelSlot, stack.copyWithCount(1))) return false;
        stack.shrink(1);
        return true;
    }
}
