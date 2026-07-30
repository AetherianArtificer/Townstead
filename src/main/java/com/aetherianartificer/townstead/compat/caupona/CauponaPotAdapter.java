package com.aetherianartificer.townstead.compat.caupona;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.FluidCarriers;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.station.BlockInventories;
import com.aetherianartificer.townstead.work.station.StationAdapters;
import com.aetherianartificer.townstead.work.station.StationProtocols;
import com.aetherianartificer.townstead.work.station.WorkstationDef;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if neoforge {
import net.neoforged.neoforge.items.IItemHandler;
//?} else if forge {
/*import net.minecraftforge.items.IItemHandler;
*///?}
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Working a Caupona stew pot, which takes more tending than an insert-and-wait station.
 *
 * <p>The pot has one slot that everything passes through: a bucket of water goes in there and the
 * empty bucket comes back out, then a bowl goes in there and the filled bowl comes back out, and
 * nothing moves while that output slot is occupied. So a potful is not one exchange but a series
 * of them, and the villager has to keep clearing the way — which is exactly what a cook standing
 * over a pot would be doing.</p>
 *
 * <p>Filling the bowls runs the pot's own containing step rather than reproducing it. A serving of
 * Caupona soup carries what went into it, and only the pot knows that; conjuring the bowl here
 * would hand out a soup that had forgotten its own ingredients.</p>
 */
public final class CauponaPotAdapter implements StationAdapters.Adapter {

    public static final String NAME = "townstead:caupona_pot";

    /** The pot's fixed layout: nine ingredients, one shared container slot, one output, one spice. */
    private static final int INGREDIENT_SLOTS = 9;
    private static final int CONTAINER_SLOT = 9;
    private static final int OUTPUT_SLOT = 10;
    private static final int POT_SLOTS = 12;

    private CauponaPotAdapter() {}

    public static void bootstrap() {
        if (!ModCompat.isLoaded("caupona")) return;
        StationAdapters.register(NAME, new CauponaPotAdapter());
    }

    // ── Phase ──

    @Override
    public StationAdapters.StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                              @Nullable DiscoveredRecipe recipe) {
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler == null || handler.getSlots() < POT_SLOTS) return StationAdapters.StationPhase.FOREIGN;

        ItemStack output = handler.getStackInSlot(OUTPUT_SLOT);
        if (recipe != null && !output.isEmpty()
                && recipe.output().equals(BuiltInRegistries.ITEM.getKey(output.getItem()))) {
            return StationAdapters.StationPhase.READY;
        }

        boolean ingredientsLeft = false;
        for (int slot = 0; slot < INGREDIENT_SLOTS; slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                ingredientsLeft = true;
                break;
            }
        }
        // Soup is ready once the pot has drunk its ingredients and holds at least one serving.
        // Until then the empty bucket sitting in the output slot must not read as finished work.
        int soup = soupAmount(level, anchor);
        if (!ingredientsLeft && soup >= CauponaFluidRecipes.SERVING_MB) {
            return StationAdapters.StationPhase.READY;
        }
        if (ingredientsLeft || soup > 0 || !output.isEmpty()
                || !handler.getStackInSlot(CONTAINER_SLOT).isEmpty()) {
            return StationAdapters.StationPhase.WORKING;
        }
        return StationAdapters.StationPhase.IDLE;
    }

    // ── Insert ──

    @Override
    public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                          WorkstationDef def, DiscoveredRecipe recipe) {
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler == null || handler.getSlots() < POT_SLOTS) return false;

        boolean anyIngredient = false;
        for (RecipeIngredient ingredient : recipe.inputs()) {
            boolean carrier = ingredient.itemIds().stream().anyMatch(FluidCarriers::isCarrier);
            for (int n = 0; n < ingredient.count(); n++) {
                ItemStack one = StationProtocols.takeMatchingIngredient(villager, ingredient);
                if (one.isEmpty()) break;
                int slot = carrier ? CONTAINER_SLOT : firstAccepting(handler, one);
                if (slot < 0 || !handler.insertItem(slot, one, true).isEmpty()) {
                    // The bowls a recipe lists are for drawing the soup back out, not for putting
                    // in now — the pot only takes them once there is something to pour.
                    StationProtocols.giveBack(villager, one);
                    continue;
                }
                handler.insertItem(slot, one, false);
                if (!carrier) anyIngredient = true;
                ItemStack container = new ItemStack(one.getItem()).getCraftingRemainingItem();
                if (container != null && !container.isEmpty()) StationProtocols.giveBack(villager, container);
            }
        }
        return anyIngredient;
    }

    /** The first ingredient slot that will take this item; the pot refuses anything uncookable. */
    private static int firstAccepting(IItemHandler handler, ItemStack one) {
        for (int slot = 0; slot < INGREDIENT_SLOTS; slot++) {
            if (handler.insertItem(slot, one, true).isEmpty()) return slot;
        }
        return -1;
    }

    // ── Collect ──

    @Override
    public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                           WorkstationDef def, DiscoveredRecipe recipe) {
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler == null || handler.getSlots() < POT_SLOTS) return false;

        // Whatever is in the way first: usually the empty bucket the water arrived in.
        boolean collected = sweepOutput(handler, villager);

        for (int served = 0; served < Math.max(1, recipe.outputCount()); served++) {
            if (soupAmount(level, anchor) < CauponaFluidRecipes.SERVING_MB) break;
            if (!handler.getStackInSlot(OUTPUT_SLOT).isEmpty()) break;
            if (!offerVessel(handler, villager)) break;
            if (!runContainingStep(level, anchor)) break;
            if (!sweepOutput(handler, villager)) break;
            collected = true;
        }
        return collected;
    }

    /** Hands the pot one empty vessel from the villager's own bowls. */
    private static boolean offerVessel(IItemHandler handler, VillagerEntityMCA villager) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (FluidCarriers.isCarrier(id)) continue;
            ItemStack one = stack.copyWithCount(1);
            if (handler.insertItem(CONTAINER_SLOT, one, true).isEmpty()) {
                handler.insertItem(CONTAINER_SLOT, one, false);
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean sweepOutput(IItemHandler handler, VillagerEntityMCA villager) {
        ItemStack output = handler.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) return false;
        ItemStack taken = handler.extractItem(OUTPUT_SLOT, output.getCount(), false);
        if (taken.isEmpty()) return false;
        StationProtocols.giveBack(villager, taken);
        return true;
    }

    // ── The pot's own logic ──

    private static @Nullable Method containingStep;
    private static boolean containingStepResolved;

    /**
     * Runs the pot's containing step: the same call its own tick makes, so the bowl it hands back
     * is built the way the pot builds it. Without it the villager would still get served, one
     * bowl per tick cycle, so a version that has moved this method costs speed rather than work.
     */
    private static boolean runContainingStep(ServerLevel level, BlockPos anchor) {
        BlockEntity blockEntity = level.getBlockEntity(anchor);
        if (blockEntity == null) return false;
        Method step = resolveContainingStep(blockEntity.getClass());
        if (step == null) return true;
        try {
            step.invoke(blockEntity);
            blockEntity.setChanged();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Nullable
    private static synchronized Method resolveContainingStep(Class<?> type) {
        if (containingStepResolved) return containingStep;
        containingStepResolved = true;
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (m.getReturnType() != boolean.class) continue;
                String name = m.getName().toLowerCase(java.util.Locale.ROOT);
                // Spelled "tryContianFluid" upstream; match either spelling rather than the typo.
                if (!name.contains("contian") && !name.contains("contain")) continue;
                if (!name.contains("fluid")) continue;
                try {
                    m.setAccessible(true);
                    containingStep = m;
                    return containingStep;
                } catch (Throwable ignored) {
                    // Sealed by the module system; fall back to the pot's own tick.
                }
            }
        }
        return null;
    }

    private static int soupAmount(ServerLevel level, BlockPos anchor) {
        BlockEntity blockEntity = level.getBlockEntity(anchor);
        if (blockEntity == null) return 0;
        Object tank = CauponaRecipeAccess.read(blockEntity, "getTank", "tank");
        if (tank == null) return 0;
        try {
            Object amount = tank.getClass().getMethod("getFluidAmount").invoke(tank);
            return amount instanceof Integer i ? i : 0;
        } catch (Throwable t) {
            return 0;
        }
    }
}
