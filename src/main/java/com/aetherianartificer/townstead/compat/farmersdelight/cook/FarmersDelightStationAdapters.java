package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.station.StationAdapters;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.BlockInventories;
import com.aetherianartificer.townstead.work.station.StationDropOutputs;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCompat;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * The small code boundary for Farmer's Delight stations whose block entities do not expose a
 * normal item handler. Datapacks choose these adapters; all station identity, recipe, placement,
 * slot and ordering data remains in the workstation definition.
 */
public final class FarmersDelightStationAdapters {

    public static final String STOVE = "townstead:farmersdelight_stove";
    public static final String SKILLET = "townstead:farmersdelight_skillet";
    public static final String CUTTING_BOARD = "townstead:farmersdelight_cutting_board";

    private FarmersDelightStationAdapters() {}

    public static void bootstrap() {
        StationAdapters.register(STOVE, new SurfaceAdapter(true));
        StationAdapters.register(SKILLET, new SurfaceAdapter(false));
        StationAdapters.register(CUTTING_BOARD, new CuttingBoardAdapter());
    }

    private static final class SurfaceAdapter implements StationAdapters.Adapter {
        private final boolean multiBlock;

        private SurfaceAdapter(boolean multiBlock) {
            this.multiBlock = multiBlock;
        }

        @Override
        public boolean supportsPurification(ServerLevel level, BlockPos anchor, WorkstationDef def) {
            return !multiBlock && FarmersDelightStationInternals.supportsPurificationAt(level, anchor);
        }

        @Override
        public boolean insertPurification(ServerLevel level, VillagerEntityMCA villager,
                                          BlockPos anchor, WorkstationDef def,
                                          ThirstCompatBridge bridge) {
            return !multiBlock && FarmersDelightStationInternals.loadPurificationFireStation(level, villager, anchor, bridge);
        }

        @Override
        public boolean supports(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                DiscoveredRecipe recipe) {
            return recipe.stationType() == def.role()
                    && FarmersDelightStationInternals.surfaceCanCookRecipeInput(level, anchor, recipe);
        }

        @Override
        public int capacity(ServerLevel level, BlockPos anchor, WorkstationDef def) {
            return FarmersDelightStationInternals.surfaceFreeSlotCount(level, anchor);
        }

        @Override
        public @Nullable BlockPos anchor(ServerLevel level, BlockPos pos, WorkstationDef def) {
            if (!multiBlock) return null;
            BlockPos canonical = FarmersDelightStationInternals.canonicalStationAnchor(level, pos);
            return canonical.equals(pos) ? null : canonical;
        }

        @Override
        public StationAdapters.StationPhase phase(ServerLevel level, BlockPos anchor,
                                                  WorkstationDef def,
                                                  @Nullable DiscoveredRecipe recipe) {
            if (StationDropOutputs.has(level, anchor, WorkRecipeRegistry.allOutputIds(level))) {
                return StationAdapters.StationPhase.READY;
            }
            int free = FarmersDelightStationInternals.surfaceFreeSlotCount(level, anchor);
            int emptyCapacity = multiBlock ? 6 : 1;
            return free < emptyCapacity ? StationAdapters.StationPhase.WORKING
                    : StationAdapters.StationPhase.IDLE;
        }

        @Override
        public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                              WorkstationDef def, DiscoveredRecipe recipe) {
            return FarmersDelightStationInternals.loadSurfaceFireStation(level, villager, anchor, recipe);
        }

        @Override
        public boolean work(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                            WorkstationDef def, DiscoveredRecipe recipe) {
            ItemStack tool = ItemStack.EMPTY;
            for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = villager.getInventory().getItem(slot);
                if (WorkRecipeRegistry.recipeToolMatches(recipe, candidate)) {
                    tool = candidate;
                    break;
                }
            }
            return !tool.isEmpty() && FdCuttingBoard.processCuttingBoardStoredItem(level, anchor, tool);
        }

        @Override
        public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                               WorkstationDef def, DiscoveredRecipe recipe) {
            List<ItemStack> drops = StationDropOutputs.collect(
                    level, anchor, WorkRecipeRegistry.allOutputIds(level));
            for (ItemStack drop : drops) villager.getInventory().addItem(drop);
            return !drops.isEmpty();
        }
    }

    private static final class CuttingBoardAdapter implements StationAdapters.Adapter {

        @Override
        public boolean supports(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                DiscoveredRecipe recipe) {
            return recipe.stationType() == def.role();
        }

        @Override
        public int capacity(ServerLevel level, BlockPos anchor, WorkstationDef def) {
            return hasStoredItem(level, anchor) ? 0 : 1;
        }

        @Override
        public StationAdapters.StationPhase phase(ServerLevel level, BlockPos anchor,
                                                  WorkstationDef def,
                                                  @Nullable DiscoveredRecipe recipe) {
            return hasStoredItem(level, anchor)
                    ? StationAdapters.StationPhase.WORKING
                    : StationAdapters.StationPhase.IDLE;
        }

        private static boolean hasStoredItem(ServerLevel level, BlockPos anchor) {
            var handler = BlockInventories.itemHandler(level, anchor, null);
            if (handler == null) return false;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) return true;
            }
            return false;
        }

        @Override
        public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                              WorkstationDef def, DiscoveredRecipe recipe) {
            if (recipe.inputs().isEmpty()) return false;
            ItemStack input = ItemStack.EMPTY;
            for (var id : recipe.inputs().get(0).itemIds()) {
                var item = BuiltInRegistries.ITEM.get(id);
                for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
                    ItemStack candidate = villager.getInventory().getItem(slot);
                    if (!candidate.isEmpty() && candidate.is(item)) {
                        input = candidate;
                        break;
                    }
                }
                if (!input.isEmpty()) break;
            }
            if (input.isEmpty()) return false;
            ItemStack one = input.copy();
            one.setCount(1);
            if (!FdCuttingBoard.placeCuttingBoardInput(level, anchor, one)) return false;
            input.shrink(1);
            return true;
        }

        @Override
        public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                               WorkstationDef def, DiscoveredRecipe recipe) {
            ItemStack output = FdCuttingBoard.collectCuttingBoardOutput(level, anchor);
            if (output.isEmpty()) return false;
            villager.getInventory().addItem(output);
            return true;
        }
    }
}

final class FdCuttingBoard {

    private FdCuttingBoard() {}

    //? if >=1.21 {
    static final ResourceLocation FD_CUTTING_BOARD = ResourceLocation.parse("farmersdelight:cutting_board");
    //?} else {
    /*static final ResourceLocation FD_CUTTING_BOARD = new ResourceLocation("farmersdelight", "cutting_board");
    *///?}
    private static Class<?> FD_CUTTING_BOARD_BE_CLASS;
    private static Method FD_CUTTING_BOARD_ADD_ITEM;
    private static Method FD_CUTTING_BOARD_PROCESS;
    private static Method FD_CUTTING_BOARD_REMOVE_ITEM;

    public static boolean cuttingBoardProcess(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            ItemStack inputStack,
            ItemStack knifeStack
    ) {
        if (stationAnchor == null || inputStack.isEmpty()) return false;
        if (knifeStack.isEmpty()) return false;

        BlockState state = level.getBlockState(stationAnchor);
        ServerPlayer actor = FarmersDelightStationInternals.cuttingBoardActor(level);
        // FD 1.3 removed all off-hand interaction with cutting boards. The simulated-player path
        // below relies on placing via off-hand, so on 1.3+ we go straight to the BE reflection path.
        if (actor != null && !FarmersDelightCompat.isAtLeast13()) {
            ItemStack previousMain = actor.getMainHandItem().copy();
            ItemStack previousOff = actor.getOffhandItem().copy();
            try {
                actor.setItemInHand(InteractionHand.MAIN_HAND, knifeStack.copy());
                actor.setItemInHand(InteractionHand.OFF_HAND, inputStack.copy());
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stationAnchor), Direction.UP, stationAnchor, false);
                InteractionResult placed = FarmersDelightStationInternals.invokeCuttingBoardBlockUse(state, level, stationAnchor, actor, InteractionHand.OFF_HAND, hit);
                if (!placed.consumesAction()) {
                    return false;
                }
                InteractionResult processed = FarmersDelightStationInternals.invokeCuttingBoardBlockUse(state, level, stationAnchor, actor, InteractionHand.MAIN_HAND, hit);
                if (processed == InteractionResult.SUCCESS) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Fall through to the direct block-entity path below.
            } finally {
                actor.setItemInHand(InteractionHand.MAIN_HAND, previousMain);
                actor.setItemInHand(InteractionHand.OFF_HAND, previousOff);
            }
        }

        if (!ensureCuttingBoardReflection()) return false;
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (be == null || !FD_CUTTING_BOARD_BE_CLASS.isInstance(be)) return false;
        try {
            Object placed = FD_CUTTING_BOARD_ADD_ITEM.invoke(be, inputStack);
            if (!cuttingBoardAddItemFullyPlaced(placed)) {
                ItemStack leftover = placed instanceof ItemStack stack ? stack : inputStack;
                if (!leftover.isEmpty()) villager.getInventory().addItem(leftover);
                return false;
            }
            Object processed = FD_CUTTING_BOARD_PROCESS.invoke(be, knifeStack, FarmersDelightStationInternals.cuttingBoardActor(level));
            if (processed instanceof Boolean ok && ok) {
                return true;
            }
            try { FD_CUTTING_BOARD_REMOVE_ITEM.invoke(be); } catch (Throwable ignored) {}
            villager.getInventory().addItem(new ItemStack(inputStack.getItem(), 1));
        } catch (Throwable t) {
            villager.getInventory().addItem(new ItemStack(inputStack.getItem(), 1));
        }
        return false;
    }

    public static boolean placeCuttingBoardInput(
            ServerLevel level,
            BlockPos stationAnchor,
            ItemStack inputStack
    ) {
        if (stationAnchor == null || inputStack.isEmpty()) return false;
        BlockState state = level.getBlockState(stationAnchor);
        ServerPlayer actor = FarmersDelightStationInternals.cuttingBoardActor(level);
        // FD 1.3 removed off-hand cutting-board interaction; skip the simulated path on 1.3+.
        if (actor != null && !FarmersDelightCompat.isAtLeast13()) {
            ItemStack previousMain = actor.getMainHandItem().copy();
            ItemStack previousOff = actor.getOffhandItem().copy();
            try {
                actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                actor.setItemInHand(InteractionHand.OFF_HAND, inputStack.copy());
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stationAnchor), Direction.UP, stationAnchor, false);
                InteractionResult placed = FarmersDelightStationInternals.invokeCuttingBoardBlockUse(state, level, stationAnchor, actor, InteractionHand.OFF_HAND, hit);
                return placed.consumesAction();
            } catch (Throwable ignored) {
                // Fall through to BE path.
            } finally {
                actor.setItemInHand(InteractionHand.MAIN_HAND, previousMain);
                actor.setItemInHand(InteractionHand.OFF_HAND, previousOff);
            }
        }

        if (!ensureCuttingBoardReflection()) return false;
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (be == null || !FD_CUTTING_BOARD_BE_CLASS.isInstance(be)) return false;
        try {
            Object placed = FD_CUTTING_BOARD_ADD_ITEM.invoke(be, inputStack);
            return cuttingBoardAddItemFullyPlaced(placed);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean processCuttingBoardStoredItem(
            ServerLevel level,
            BlockPos stationAnchor,
            ItemStack knifeStack
    ) {
        if (stationAnchor == null || knifeStack.isEmpty()) return false;

        BlockState state = level.getBlockState(stationAnchor);
        ServerPlayer actor = FarmersDelightStationInternals.cuttingBoardActor(level);
        // FD 1.3 reworked the cutting board (full-stack handling); go BE-direct on 1.3+ to avoid
        // depending on block-use semantics that may have shifted.
        if (actor != null && !FarmersDelightCompat.isAtLeast13()) {
            ItemStack previousMain = actor.getMainHandItem().copy();
            ItemStack previousOff = actor.getOffhandItem().copy();
            try {
                actor.setItemInHand(InteractionHand.MAIN_HAND, knifeStack.copy());
                actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stationAnchor), Direction.UP, stationAnchor, false);
                InteractionResult processed = FarmersDelightStationInternals.invokeCuttingBoardBlockUse(state, level, stationAnchor, actor, InteractionHand.MAIN_HAND, hit);
                return processed == InteractionResult.SUCCESS;
            } catch (Throwable ignored) {
                // Fall through to BE path.
            } finally {
                actor.setItemInHand(InteractionHand.MAIN_HAND, previousMain);
                actor.setItemInHand(InteractionHand.OFF_HAND, previousOff);
            }
        }

        if (!ensureCuttingBoardReflection()) return false;
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (be == null || !FD_CUTTING_BOARD_BE_CLASS.isInstance(be)) return false;
        try {
            Object processed = FD_CUTTING_BOARD_PROCESS.invoke(be, knifeStack, FarmersDelightStationInternals.cuttingBoardActor(level));
            return processed instanceof Boolean ok && ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static ItemStack collectCuttingBoardOutput(ServerLevel level, BlockPos stationAnchor) {
        if (stationAnchor == null) return ItemStack.EMPTY;
        if (!ensureCuttingBoardReflection()) return ItemStack.EMPTY;
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (be == null || !FD_CUTTING_BOARD_BE_CLASS.isInstance(be)) return ItemStack.EMPTY;
        try {
            Object removed = FD_CUTTING_BOARD_REMOVE_ITEM.invoke(be);
            if (removed instanceof ItemStack stack && !stack.isEmpty()) {
                return stack;
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    static boolean ensureCuttingBoardReflection() {
        if (FD_CUTTING_BOARD_BE_CLASS != null && FD_CUTTING_BOARD_ADD_ITEM != null
                && FD_CUTTING_BOARD_PROCESS != null && FD_CUTTING_BOARD_REMOVE_ITEM != null) {
            return true;
        }
        try {
            Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
            FD_CUTTING_BOARD_BE_CLASS = Class.forName("vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity");
            FD_CUTTING_BOARD_ADD_ITEM = FD_CUTTING_BOARD_BE_CLASS.getMethod("addItem", ItemStack.class);
            FD_CUTTING_BOARD_PROCESS = FD_CUTTING_BOARD_BE_CLASS.getMethod("processStoredItemUsingTool", ItemStack.class, playerClass);
            FD_CUTTING_BOARD_REMOVE_ITEM = FD_CUTTING_BOARD_BE_CLASS.getMethod("removeItem");
            return true;
        } catch (Throwable ignored) {
            FD_CUTTING_BOARD_BE_CLASS = null; FD_CUTTING_BOARD_ADD_ITEM = null;
            FD_CUTTING_BOARD_PROCESS = null; FD_CUTTING_BOARD_REMOVE_ITEM = null;
            return false;
        }
    }


    static boolean cuttingBoardAddItemFullyPlaced(Object result) {
        if (result instanceof Boolean b) return b;
        if (result instanceof ItemStack stack) return stack.isEmpty();
        return false;
    }
}
