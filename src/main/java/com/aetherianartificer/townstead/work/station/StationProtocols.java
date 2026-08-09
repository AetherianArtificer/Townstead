package com.aetherianartificer.townstead.work.station;


import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;

import com.aetherianartificer.townstead.work.station.FurnaceStationAdapter;
import com.aetherianartificer.townstead.work.station.StationAdapters;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.Workstations;

import com.aetherianartificer.townstead.work.producer.ProducerStationSessions;
import com.aetherianartificer.townstead.work.producer.ProducerStationState;
import com.aetherianartificer.townstead.work.station.StationAdapters.Adapter;
import com.aetherianartificer.townstead.work.station.StationAdapters.StationPhase;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
//? if neoforge {
import net.neoforged.neoforge.items.IItemHandler;
//?} else if forge {
/*import net.minecraftforge.items.IItemHandler;
*///?}
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * The generic lifecycle for protocol workstations. A {@code passive_station} is
 * insert-wait-collect (the block processes by itself: a fermenting basin, a curing barrel). A
 * {@code place_surface} station is place-compose-wait-harvest: the villager places the work
 * block from their own inventory onto a declared surface, composes it by inserting real items
 * into its block entity, waits for the in-place transformation, and harvests with the declared
 * tool. Both run through {@link StationAdapters} so mod-specific interactions stay tiny named
 * primitives while the walking, staging, waiting, and storing stay engine.
 */
public final class StationProtocols {

    private StationProtocols() {}

    public static boolean isProtocolType(@Nullable StationType type) {
        return type == StationType.PASSIVE_STATION
                || type == StationType.PLACE_SURFACE
                || type == StationType.FURNACE_STATION
                || type == StationType.CRAFT_SURFACE;
    }

    /** Whether the declared station and its adapter accept this recipe. */
    public static boolean supports(ServerLevel level, BlockPos anchor, DiscoveredRecipe recipe) {
        if (level == null || anchor == null || recipe == null) return false;
        WorkstationDef def = defAt(level, anchor);
        Adapter adapter = resolveAdapter(level, def);
        return def != null && adapter != null && adapter.supports(level, anchor, def, recipe);
    }

    /** True when this anchor resolves to a declared station with a registered lifecycle adapter. */
    public static boolean handles(ServerLevel level, BlockPos anchor) {
        if (level == null || anchor == null) return false;
        WorkstationDef def = defAt(level, anchor);
        return def != null && resolveAdapter(level, def) != null;
    }

    public static boolean supportsPurification(ServerLevel level, BlockPos anchor) {
        WorkstationDef def = defAt(level, anchor);
        Adapter adapter = resolveAdapter(level, def);
        return def != null && adapter != null && adapter.supportsPurification(level, anchor, def);
    }

    public static boolean insertPurification(ServerLevel level, VillagerEntityMCA villager,
                                             BlockPos anchor, ThirstCompatBridge bridge) {
        WorkstationDef def = defAt(level, anchor);
        Adapter adapter = resolveAdapter(level, def);
        return def != null && adapter != null
                && adapter.insertPurification(level, villager, anchor, def, bridge);
    }

    /**
     * The def governing this anchor: the block itself (placed work blocks, passive stations),
     * or — for an empty placement anchor — the place-surface def whose surface is below.
     */
    @Nullable
    public static WorkstationDef defAt(ServerLevel level, BlockPos anchor) {
        BlockState state = level.getBlockState(anchor);
        WorkstationDef def = Workstations.byState(state);
        // An explicit adapter is the declaration that this station participates in the generic
        // lifecycle. Do not second-guess it with a closed list of roles: surface fire stations
        // such as the Farmer's Delight skillet are FIRE_STATIONs, but still implement the same
        // insert/wait/collect protocol through their adapter.
        if (def != null && (def.adapter() != null || isProtocolType(def.role()))) return def;
        if (state.isAir()) return surfaceDefBelow(level, anchor);
        return null;
    }

    /** The place-surface def whose declared surface sits directly below {@code anchor}. */
    @Nullable
    public static WorkstationDef surfaceDefBelow(ServerLevel level, BlockPos anchor) {
        BlockState below = level.getBlockState(anchor.below());
        for (WorkstationDef def : Workstations.all()) {
            if (def.role() != StationType.PLACE_SURFACE) continue;
            if (matchesSurface(def, below)) return def;
        }
        return null;
    }

    public static boolean matchesSurface(WorkstationDef def, BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (def.surfaceBlocks().contains(blockId)) return true;
        for (ResourceLocation tagId : def.surfaceTags()) {
            if (state.is(TagKey.create(net.minecraft.core.registries.Registries.BLOCK, tagId))) return true;
        }
        return false;
    }

    // ── Classification ──

    public static ProducerStationState classify(
            ServerLevel level, VillagerEntityMCA villager, BlockPos anchor, StationType type,
            @Nullable DiscoveredRecipe recipe, @Nullable ProducerStationSessions.SessionSnapshot session) {
        WorkstationDef def = defAt(level, anchor);
        Adapter adapter = resolveAdapter(level, def);
        if (def == null || adapter == null) return ProducerStationState.BLOCKED;
        boolean ownsSession = session != null && session.isOwner(villager.getUUID());
        StationPhase phase = phase(level, anchor, def, adapter, recipe);
        return switch (phase) {
            case IDLE -> ProducerStationState.EMPTY_READY;
            case READY -> ProducerStationState.FINISHED_OUTPUT;
            // The block is mid-cycle: ours to keep waiting on, someone else's to leave alone.
            // Never FOREIGN_CONTENTS — a fermenting basin cannot be "cleaned up".
            case WORKING -> ownsSession ? ProducerStationState.OWNED_STAGED : ProducerStationState.BLOCKED;
            case FOREIGN -> ProducerStationState.BLOCKED;
        };
    }

    private static StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                      Adapter adapter, @Nullable DiscoveredRecipe recipe) {
        if (def.role() == StationType.PLACE_SURFACE) {
            BlockState state = level.getBlockState(anchor);
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (state.isAir()) return StationPhase.IDLE;
            if (blockId.equals(def.doneBlock())) return StationPhase.READY;
            if (blockId.equals(def.places())) return StationPhase.WORKING;
            return StationPhase.FOREIGN;
        }
        return adapter.phase(level, anchor, def, recipe);
    }

    public static boolean isReady(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                                  @Nullable DiscoveredRecipe recipe) {
        WorkstationDef def = defAt(level, anchor);
        Adapter adapter = resolveAdapter(level, def);
        if (def == null || adapter == null) return true;
        // A craft surface has no state to poll: the recipe's declared time IS the work, and the
        // caller gates on that clock before asking. Once asked, the craft is done.
        if (def.role() == StationType.CRAFT_SURFACE) return true;
        return phase(level, anchor, def, adapter, recipe) == StationPhase.READY;
    }

    /** True once an adapter-backed station has no staged work left in it. */
    public static boolean isIdle(ServerLevel level, BlockPos anchor, @Nullable DiscoveredRecipe recipe) {
        WorkstationDef def = defAt(level, anchor);
        Adapter adapter = resolveAdapter(level, def);
        return def != null && adapter != null && phase(level, anchor, def, adapter, recipe) == StationPhase.IDLE;
    }

    public static boolean hasAnyContents(ServerLevel level, BlockPos anchor) {
        WorkstationDef def = defAt(level, anchor);
        Adapter adapter = resolveAdapter(level, def);
        if (def == null || adapter == null) return false;
        return phase(level, anchor, def, adapter, null) != StationPhase.IDLE;
    }

    // ── Insert (beginProduce) ──

    /**
     * Commit staged inputs into the station. For place-surface stations this first places the
     * work block from the villager's inventory onto the surface (a real placement: the item is
     * consumed, {@code setPlacedBy} runs), then composes it with the remaining inputs and any
     * opportunistic extras.
     */
    public static boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                                 DiscoveredRecipe recipe, Set<Long> storageBounds) {
        return insert(level, villager, anchor, recipe, storageBounds, 1);
    }

    public static boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                                 DiscoveredRecipe recipe, Set<Long> storageBounds, int copies) {
        WorkstationDef def = defAt(level, anchor);
        Adapter adapter = resolveAdapter(level, def);
        if (def == null || adapter == null) return false;

        if (def.role() == StationType.PLACE_SURFACE) {
            if (level.getBlockState(anchor).isAir() && !placeWorkBlock(level, villager, anchor, def)) {
                return false;
            }
            // The placed item is already committed; compose the block with the remaining
            // inputs through its own item handler (which enforces its acceptance rules).
            if (!composePlaced(level, villager, anchor, def, recipe)) return false;
            insertExtras(level, villager, anchor, def, recipe, storageBounds);
            return true;
        }
        return adapter.insertBatch(level, villager, anchor, def, recipe, Math.max(1, copies));
    }

    /** Perform a station's explicit work action after its inputs have been inserted. */
    public static boolean work(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                               DiscoveredRecipe recipe) {
        WorkstationDef def = defAt(level, anchor);
        Adapter adapter = resolveAdapter(level, def);
        return def != null && adapter != null && adapter.work(level, villager, anchor, def, recipe);
    }

    private static boolean composePlaced(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                                         WorkstationDef def, DiscoveredRecipe recipe) {
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler == null) return false;
        for (RecipeIngredient ingredient : recipe.inputs()) {
            if (def.places() != null && ingredient.itemIds().contains(def.places())) continue;
            for (int n = 0; n < ingredient.count(); n++) {
                ItemStack one = takeMatchingIngredient(villager, ingredient);
                if (one.isEmpty()) return false;
                if (!insertIntoAnySlot(handler, one)) {
                    giveBack(villager, one);
                    return false;
                }
                ItemStack container = new ItemStack(one.getItem()).getCraftingRemainingItem();
                if (container != null && !container.isEmpty()) giveBack(villager, container);
            }
        }
        return true;
    }

    public static ItemStack takeMatchingIngredient(VillagerEntityMCA villager,
                                            RecipeIngredient ingredient) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && ingredient.itemIds().contains(id)) {
                return stack.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean placeWorkBlock(ServerLevel level, VillagerEntityMCA villager,
                                          BlockPos anchor, WorkstationDef def) {
        if (def.places() == null) return false;
        Item placedItem = BuiltInRegistries.ITEM.get(def.places());
        if (!(placedItem instanceof BlockItem blockItem)) return false;
        ItemStack held = takeOne(villager, placedItem);
        if (held.isEmpty()) return false;

        BlockPos surface = anchor.below();
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(surface).add(0, 0.5, 0), Direction.UP, surface, false);
        BlockPlaceContext context = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, held, hit);
        boolean placed = blockItem.place(context).consumesAction();
        if (!placed || !held.isEmpty()) {
            // Refused (or not consumed): give the item back rather than losing it.
            if (!held.isEmpty()) giveBack(villager, held);
            return placed && held.isEmpty();
        }
        return true;
    }

    /**
     * Garnish a composed place-surface block with up to {@code extrasMax} DISTINCT extra items
     * from the produce's extras tag — pulled from real storage, inserted through the block's
     * own item handler (which enforces its acceptance rules). Extras are best-effort: a
     * plainer product still ships, a fuller pantry yields a better one.
     */
    private static void insertExtras(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                                     WorkstationDef def, DiscoveredRecipe recipe, Set<Long> storageBounds) {
        WorkstationDef.Produce produce = produceFor(def, recipe);
        if (produce == null || produce.extrasTag() == null || produce.extrasMax() <= 0) return;
        TagKey<Item> tag = TagKey.create(net.minecraft.core.registries.Registries.ITEM, produce.extrasTag());
        StationSupplies.pullDistinct(level, villager, tag, produce.extrasMax(), anchor, storageBounds);

        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler == null) return;
        Set<Item> inserted = new HashSet<>();
        int placed = 0;
        for (int invSlot = 0; invSlot < villager.getInventory().getContainerSize()
                && placed < produce.extrasMax(); invSlot++) {
            ItemStack stack = villager.getInventory().getItem(invSlot);
            if (stack.isEmpty() || !stack.is(tag) || inserted.contains(stack.getItem())) continue;
            ItemStack one = stack.copyWithCount(1);
            if (insertIntoAnySlot(handler, one)) {
                stack.shrink(1);
                inserted.add(one.getItem());
                placed++;
            }
        }
    }

    // ── Collect ──

    public static boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                                  @Nullable DiscoveredRecipe recipe, Set<Long> storageBounds) {
        WorkstationDef def = defAt(level, anchor);
        Adapter adapter = resolveAdapter(level, def);
        if (def == null || adapter == null) return false;
        if (recipe == null) return adapter.collectAvailable(level, villager, anchor, def);
        if (def.role() == StationType.PLACE_SURFACE
                && !ensureHarvestTool(level, villager, anchor, def, storageBounds)) {
            return false;
        }
        return adapter.collect(level, villager, anchor, def, recipe);
    }

    /** The declared harvest tool must actually be in the villager's hands (pulled from storage). */
    private static boolean ensureHarvestTool(ServerLevel level, VillagerEntityMCA villager,
                                             BlockPos anchor, WorkstationDef def, Set<Long> storageBounds) {
        if (def.harvestTools().isEmpty()) return true;
        if (hasHarvestTool(villager, def)) return true;
        StationSupplies.pullTool(level, villager,
                stack -> matchesHarvestTool(def, stack), anchor, storageBounds);
        return hasHarvestTool(villager, def);
    }

    public static boolean hasHarvestTool(VillagerEntityMCA villager, WorkstationDef def) {
        if (def.harvestTools().isEmpty()) return true;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            if (matchesHarvestTool(def, villager.getInventory().getItem(i))) return true;
        }
        return false;
    }

    private static boolean matchesHarvestTool(WorkstationDef def, ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && def.harvestTools().contains(id);
    }

    // ── Shared helpers ──

    @Nullable
    public static WorkstationDef.Produce produceFor(@Nullable WorkstationDef def, @Nullable DiscoveredRecipe recipe) {
        if (def == null || recipe == null) return null;
        for (WorkstationDef.Produce produce : def.produces()) {
            if (produce.output().equals(recipe.output())) return produce;
        }
        return null;
    }

    /**
     * Whether this def owns {@code recipe} (exclusive pairing). Protocol stations can declare
     * either inline {@code produces} lines or a normal recipe type. The latter used to be missed,
     * causing a Farm & Charm stove to reject every recipe discovered from
     * {@code farm_and_charm:stove} even though that was the type named by its own workstation def.
     */
    public static boolean defOwnsRecipe(WorkstationDef def, DiscoveredRecipe recipe) {
        if (def == null || recipe == null || recipe.stationType() != def.role()) return false;
        if (produceFor(def, recipe) != null) return true;
        // Built-in recipe families predate workstation declarations. A def with no declared
        // recipe source intentionally adopts that built-in family for its role.
        if (def.recipeType() == null && def.produces().isEmpty()) return true;
        return StationRecipeOwnership.ownsDeclaredType(def, recipe.stationType(),
                WorkRecipeRegistry.foreignRecipeTypeId(recipe));
    }

    @Nullable
    private static Adapter resolveAdapter(ServerLevel level, @Nullable WorkstationDef def) {
        return def == null ? null : StationAdapters.forDef(def);
    }

    public static ItemStack takeOne(VillagerEntityMCA villager, Item item) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return stack.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    public static void giveBack(VillagerEntityMCA villager, ItemStack stack) {
        ItemStack remainder = villager.getInventory().addItem(stack);
        if (!remainder.isEmpty()) {
            villager.spawnAtLocation(remainder);
        }
    }

    static boolean insertIntoAnySlot(IItemHandler handler, ItemStack one) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.insertItem(slot, one, true).isEmpty()) {
                handler.insertItem(slot, one, false);
                return true;
            }
        }
        return false;
    }

    /** Registers the default capability-driven adapter for slot-based passive blocks. */
    public static void bootstrap() {
        StationAdapters.register(StationAdapters.DEFAULT_ITEM_HANDLER, new ItemHandlerAdapter());
        CampfireStationAdapter.bootstrap();
        FurnaceStationAdapter.bootstrap();
        CraftSurfaceAdapter.bootstrap();
        DataDrivenStationAdapter.bootstrap();
    }

    /** Physical copies of one recipe the declared station can accept together. */
    public static int batchCapacity(ServerLevel level, BlockPos anchor, DiscoveredRecipe recipe) {
        if (level == null || anchor == null || recipe == null) return 1;
        WorkstationDef def = defAt(level, anchor);
        Adapter adapter = resolveAdapter(level, def);
        return def == null || adapter == null ? 1
                : Math.max(1, adapter.batchCapacity(level, anchor, def, recipe));
    }

    /** Insert-wait-collect through a plain item capability. */
    static final class ItemHandlerAdapter implements Adapter {

        /**
         * Which face to work a station through. A sided block states where its slots are — a
         * cooking pot takes ingredients through the top, hands the dish out of the bottom, and
         * keeps its bowls on the sides — and asking for the unsided handler instead gets one
         * view of every slot at once. That view lets an ingredient land in the output slot,
         * where it is not an ingredient and blocks the recipe from ever completing. Falls back
         * to unsided for plain containers, which is what every non-sided block wants.
         */
        private static @Nullable IItemHandler handler(ServerLevel level, BlockPos anchor,
                                                      @Nullable Direction side) {
            IItemHandler sided = side == null ? null : BlockInventories.itemHandler(level, anchor, side);
            if (sided != null && sided.getSlots() > 0) return sided;
            return BlockInventories.itemHandler(level, anchor, null);
        }

        @Override
        public StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                  @Nullable DiscoveredRecipe recipe) {
            IItemHandler handler = handler(level, anchor, null);
            if (handler == null) return StationPhase.FOREIGN;
            boolean any = false;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (def.role() == StationType.HOT_STATION && slot == def.containerSlot()) continue;
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                any = true;
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (recipe == null && WorkRecipeRegistry.allOutputIds(level).contains(itemId)) {
                    return StationPhase.READY;
                }
                if (isFinished(def, recipe, itemId)) {
                    return StationPhase.READY;
                }
            }
            return any ? StationPhase.WORKING : StationPhase.IDLE;
        }

        @Override
        public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                              WorkstationDef def, DiscoveredRecipe recipe) {
            IItemHandler handler = handler(level, anchor, Direction.UP);
            if (handler == null) return false;
            // Fuel first, for the same reason a furnace does it first: ingredients loaded into a
            // machine that will never light are ingredients the villager cannot cheaply get back.
            if (def.needsFuel() && !ensureFuel(level, villager, anchor)) return false;
            for (RecipeIngredient ingredient : recipe.inputs()) {
                // Fuel is a planning requirement represented by a supply-line id, not an item
                // that belongs in an ingredient slot. ensureFuel above has already moved the
                // concrete log/coal into the machine's fuel face.
                if (StationRecipeOwnership.isFuelRequirement(ingredient)) continue;
                for (int n = 0; n < ingredient.count(); n++) {
                    ItemStack one = takeMatching(villager, ingredient);
                    if (one.isEmpty()) return false;
                    if (!insertIntoAnySlot(handler, one)) {
                        giveBack(villager, one);
                        return false;
                    }
                    returnContainer(villager, one.getItem());
                }
            }
            return true;
        }

        @Override
        public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                               WorkstationDef def, DiscoveredRecipe recipe) {
            IItemHandler handler = handler(level, anchor, Direction.DOWN);
            if (handler == null) return false;
            boolean collected = false;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                if (!isFinished(def, recipe, BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
                ItemStack extracted = handler.extractItem(slot, stack.getCount(), false);
                if (!extracted.isEmpty()) {
                    giveBack(villager, extracted);
                    collected = true;
                }
            }
            return collected;
        }

        /**
         * Loads one fuel item through a side face, which is where a sided machine keeps its fuel
         * (its top is ingredients and its bottom is the output). Already-fuelled stations are
         * left alone — a stove that is burning does not want a second log.
         */
        private static boolean ensureFuel(ServerLevel level, VillagerEntityMCA villager,
                                          BlockPos anchor) {
            IItemHandler fuel = handler(level, anchor, Direction.NORTH);
            if (fuel == null) return false;
            for (int slot = 0; slot < fuel.getSlots(); slot++) {
                if (!fuel.getStackInSlot(slot).isEmpty()) return true;
            }
            var matches = com.aetherianartificer.townstead.supply.SupplyLines.matcher(level,
                    com.aetherianartificer.townstead.supply.TownsteadSupplyLines.FURNACE_FUEL);
            var inventory = villager.getInventory();
            int bestSlot = -1;
            int bestPreference = Integer.MIN_VALUE;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || !matches.test(stack)) continue;
                int preference = com.aetherianartificer.townstead.supply.SupplyLines.preference(
                        level, com.aetherianartificer.townstead.supply.TownsteadSupplyLines.FURNACE_FUEL, stack);
                if (bestSlot < 0 || preference > bestPreference) {
                    bestSlot = i;
                    bestPreference = preference;
                }
            }
            if (bestSlot < 0) return false;
            ItemStack stack = inventory.getItem(bestSlot);
            if (!insertIntoAnySlot(fuel, stack.copyWithCount(1))) return false;
            stack.shrink(1);
            return true;
        }

        /**
         * Whether this item is the thing the station was set to make. A def with inline produce
         * lines names its own outputs; a def whose recipes are discovered has none to name, so
         * the recipe being worked is what says when the work is done.
         */
        private static boolean isFinished(WorkstationDef def, @Nullable DiscoveredRecipe recipe,
                                          @Nullable ResourceLocation id) {
            if (id == null) return false;
            for (WorkstationDef.Produce produce : def.produces()) {
                if (produce.output().equals(id)) return true;
            }
            return def.produces().isEmpty() && recipe != null && recipe.output().equals(id);
        }

        private static ItemStack takeMatching(VillagerEntityMCA villager,
                                              RecipeIngredient ingredient) {
            for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
                ItemStack stack = villager.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null && ingredient.itemIds().contains(id)) {
                    return stack.split(1);
                }
            }
            return ItemStack.EMPTY;
        }

        /** Consuming a milk bucket hands the empty bucket back, exactly as a player keeps theirs. */
        private static void returnContainer(VillagerEntityMCA villager, Item consumed) {
            ItemStack container = new ItemStack(consumed).getCraftingRemainingItem();
            if (container != null && !container.isEmpty()) {
                giveBack(villager, container);
            }
        }
    }
}
