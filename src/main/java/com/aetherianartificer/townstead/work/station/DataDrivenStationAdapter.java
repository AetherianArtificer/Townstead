package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.recipe.WaterPurificationItems;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
//? if neoforge {
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
//?} else if forge {
/*import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;
*///?}
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Generic V2 execution: public inventory contracts plus real player-like block interaction. */
public final class DataDrivenStationAdapter implements StationAdapters.Adapter {
    public static final String NAME = "townstead:data_driven";
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("266f5434-42bb-47d9-b014-2b42e12ca454"), "[TownsteadWorker]");
    private static int outputCacheGeneration = Integer.MIN_VALUE;
    private static final Map<ResourceLocation, Set<ResourceLocation>> OUTPUTS_BY_BLOCK = new java.util.HashMap<>();
    private static final Map<ResourceLocation, Set<ResourceLocation>> CONTAINERS_BY_BLOCK = new java.util.HashMap<>();
    private static final Map<ResourceLocation, Set<ResourceLocation>> INPUTS_BY_BLOCK = new java.util.HashMap<>();

    private DataDrivenStationAdapter() {}

    public static void bootstrap() {
        StationAdapters.register(NAME, new DataDrivenStationAdapter());
    }

    private static @Nullable WorkstationV2Def v2(ServerLevel level, BlockPos pos) {
        return Workstations.v2ByState(level.getBlockState(pos));
    }

    @Override
    public boolean supports(ServerLevel level, BlockPos anchor, WorkstationDef ignored,
                            DiscoveredRecipe recipe) {
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(anchor).getBlock());
        ResourceLocation type = WorkRecipeRegistry.recipeTypeId(recipe);
        if (type != null && WorkstationRecipeTypes.forBlock(block).contains(type)) return true;
        return false;
    }

    @Override
    public boolean supportsPurification(ServerLevel level, BlockPos anchor, WorkstationDef ignored) {
        WorkstationV2Def def = v2(level, anchor);
        if (def == null || !def.behaviorUses("ingredient") || !def.isOperational(level, anchor)) {
            return false;
        }
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(anchor).getBlock());
        return def.schedulingRole(WorkstationRecipeTypes.forBlock(block))
                == com.aetherianartificer.townstead.work.recipe.StationType.FIRE_STATION
                && capacity(level, anchor, ignored) > 0;
    }

    @Override
    public boolean insertPurification(ServerLevel level, VillagerEntityMCA villager,
                                      BlockPos anchor, WorkstationDef ignored,
                                      ThirstCompatBridge bridge) {
        if (!supportsPurification(level, anchor, ignored)) return false;
        WorkstationV2Def def = v2(level, anchor);
        if (def == null) return false;
        JsonObject ingredientAction = actions(def.behavior()).stream()
                .filter(action -> "ingredient".equals(role(action)))
                .findFirst().orElse(null);
        if (ingredientAction == null) return false;
        int slot = WaterPurificationItems.bestSlot(villager.getInventory(), bridge,
                stack -> !stack.isEmpty());
        if (slot < 0) return false;
        ItemStack source = villager.getInventory().getItem(slot);
        ItemStack one = source.copyWithCount(1);
        if (!useBlock(level, villager, anchor, ingredientAction, one)) return false;
        source.shrink(1);
        return true;
    }

    @Override
    public int capacity(ServerLevel level, BlockPos anchor, WorkstationDef ignored) {
        WorkstationV2Def def = v2(level, anchor);
        if (def == null) return 0;
        if (!def.isOperational(level, anchor)) return def.hasPreparationAction() || def.hasReservation() ? 1 : 0;
        if (connectedStructure(def)) {
            List<BlockPos> structure = connected(level, anchor, def);
            if (def.capacityValue() == null) return structure.size();
            var context = stationSelectorContext(level, anchor, def)
                    .withBlockRole("structure", structure);
            return Math.max(0, (int) Math.floor(def.capacityValue().get(context)));
        }
        if (def.capacity() != null && def.capacityValue() == null) {
            return def.capacityPositions(level, anchor);
        }
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler == null) return 1;
        int free = 0;
        if (def.hasExplicitIngredientSlots()) {
            for (int slot : concat(def.ingredientSlots(), def.catalystSlots())) {
                if (slot < handler.getSlots() && handler.getStackInSlot(slot).isEmpty()) free++;
            }
        } else {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!def.reservedForInsertion(slot) && handler.getStackInSlot(slot).isEmpty()) free++;
            }
        }
        return Math.max(0, free);
    }

    @Override
    public int batchCapacity(ServerLevel level, BlockPos anchor, WorkstationDef ignored,
                             DiscoveredRecipe recipe) {
        WorkstationV2Def def = v2(level, anchor);
        if (def == null || def.capacity() == null || def.capacityValue() != null) return 1;
        int positions = def.capacityPositions(level, anchor);
        if (!def.stackPerPosition()) {
            return Math.max(1, positions * def.capacityPerPosition());
        }
        int operationsPerStack = Integer.MAX_VALUE;
        for (RecipeIngredient ingredient : recipe.inputs()) {
            int ingredientStack = 64;
            for (ResourceLocation id : ingredient.itemIds()) {
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(id);
                if (item != net.minecraft.world.item.Items.AIR) {
                    ingredientStack = Math.min(ingredientStack, new ItemStack(item).getMaxStackSize());
                }
            }
            operationsPerStack = Math.min(operationsPerStack,
                    ingredientStack / Math.max(1, ingredient.count()));
        }
        if (operationsPerStack == Integer.MAX_VALUE) operationsPerStack = 1;
        return Math.max(1, positions * operationsPerStack);
    }

    @Override
    public @Nullable BlockPos anchor(ServerLevel level, BlockPos pos, WorkstationDef ignored) {
        WorkstationV2Def def = v2(level, pos);
        if (def == null) return null;
        if (def.anchorSelector() != null) {
            return def.anchorSelector().select(stationSelectorContext(level, pos, def)).stream()
                    .min(java.util.Comparator
                            .comparingDouble((BlockPos candidate) -> {
                                double dx = candidate.getX() - pos.getX();
                                double dy = candidate.getY() - pos.getY();
                                double dz = candidate.getZ() - pos.getZ();
                                return dx * dx + dy * dy + dz * dz;
                            })
                            .thenComparingLong(BlockPos::asLong))
                    .map(BlockPos::immutable).orElse(null);
        }
        if (!connectedStructure(def)) return null;
        return connected(level, pos, def).stream()
                .min(java.util.Comparator.comparingLong(BlockPos::asLong))
                .filter(anchor -> !anchor.equals(pos)).orElse(null);
    }

    @Override
    public StationAdapters.StationPhase phase(ServerLevel level, BlockPos anchor,
                                              WorkstationDef ignored,
                                              @Nullable DiscoveredRecipe recipe) {
        WorkstationV2Def def = v2(level, anchor);
        if (def == null) return StationAdapters.StationPhase.FOREIGN;
        // Requirements gate starting/continuing work, never unloading a finished product. A pot
        // does not stop containing dinner merely because its heat source went out overnight.
        if (recipe != null && hasOutput(level, anchor, recipe.output())) {
            return StationAdapters.StationPhase.READY;
        }
        if (hasAvailableOutput(level, anchor)) return StationAdapters.StationPhase.READY;
        if (def.isReady(level, anchor)) return StationAdapters.StationPhase.READY;
        if (!def.isOperational(level, anchor)) {
            return def.hasPreparationAction() || def.hasReservation()
                    ? StationAdapters.StationPhase.IDLE : StationAdapters.StationPhase.FOREIGN;
        }
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler != null) {
            Set<ResourceLocation> knownContainers = containerIds(level, anchor);
            Set<ResourceLocation> knownInputs = inputIds(level, anchor);
            boolean working = false;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (def.catalystSlots().contains(slot)) continue;
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (def.containerSlots().contains(slot) && knownContainers.contains(item)) continue;
                if (isFuelStock(level, anchor, slot, stack)) continue;
                if (!knownInputs.contains(item)) {
                    return StationAdapters.StationPhase.INVALID_CONTENTS;
                }
                working = true;
            }
            if (working) return StationAdapters.StationPhase.WORKING;
        }
        return StationAdapters.StationPhase.IDLE;
    }

    @Override
    public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                          WorkstationDef ignored, DiscoveredRecipe recipe) {
        return insertBatch(level, villager, anchor, ignored, recipe, 1);
    }

    @Override
    public boolean insertBatch(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                               WorkstationDef ignored, DiscoveredRecipe recipe, int copies) {
        WorkstationV2Def def = v2(level, anchor);
        if (def == null) return false;
        if (!def.isOperational(level, anchor)) {
            if (!runSetupActions(level, villager, anchor, def, recipe)
                    || !def.isOperational(level, anchor)) return false;
        }
        InsertionTransaction transaction = new InsertionTransaction();

        // Stage reversible inventory state before any interaction which may be irreversible.
        if (!insertContainer(level, villager, anchor, def, recipe, transaction)) {
            rollback(villager, transaction);
            return false;
        }

        // A declared ingredient interaction is authoritative (skillets, mincers and silos).
        if (def.behaviorUses("ingredient")) {
            if (!runPreparationActions(level, villager, anchor, def, recipe,
                    Math.max(1, copies))) {
                rollback(villager, transaction);
                return false;
            }
        } else {
            // A cutting board has no separate ingredient declaration: its normal player contract
            // accepts the ingredient, then the exceptional tool action processes it.
            boolean boardLike = def.behaviorUses("tool");
            if (boardLike) {
                if (!interactIngredient(level, villager, anchor, recipe, def, false)) {
                    rollback(villager, transaction);
                    return false;
                }
            } else if (!insertIngredients(level, villager, anchor, def, recipe, transaction)) {
                rollback(villager, transaction);
                return false;
            }
        }

        opportunisticallyFuel(level, villager, anchor);
        return true;
    }

    @Override
    public boolean work(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                        WorkstationDef ignored, DiscoveredRecipe recipe) {
        WorkstationV2Def def = v2(level, anchor);
        if (def == null) return false;
        List<JsonObject> actions = actions(def.behavior());
        boolean hadWork = false;
        boolean afterIngredient = !def.behaviorUses("ingredient");
        for (JsonObject action : actions) {
            String role = role(action);
            if ("ingredient".equals(role)) { afterIngredient = true; continue; }
            // Empty-hand actions before an ingredient are preparation (e.g. close a silo), not a
            // crank/stir to repeat while it is processing.
            if (!afterIngredient && "empty".equals(role)) continue;
            if (!"tool".equals(role) && !"empty".equals(role)) continue;
            hadWork = true;
            if (!runAction(level, villager, anchor, recipe, action, role, def)) return false;
        }
        return true;
    }

    @Override
    public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                           WorkstationDef ignored, DiscoveredRecipe recipe) {
        WorkstationV2Def def = v2(level, anchor);
        if (def != null && def.collect() != null) {
            return collectThroughInteraction(level, villager, anchor, def, recipe,
                    Set.of(recipe.output()));
        }
        boolean collected = extractOutput(level, villager, anchor, recipe.output());
        collected |= extractDeclaredReturns(level, villager, anchor);
        collected |= extractRecipeRemainders(level, villager, anchor, recipe);
        List<ItemStack> drops = StationDropOutputs.collect(level, anchor, Set.of(recipe.output()));
        for (ItemStack drop : drops) StationProtocols.giveBack(villager, drop);
        return collected || !drops.isEmpty();
    }

    @Override
    public boolean collectAvailable(ServerLevel level, VillagerEntityMCA villager,
                                    BlockPos anchor, WorkstationDef ignored) {
        Set<ResourceLocation> outputs = outputIds(level, anchor);
        if (outputs.isEmpty()) return false;
        WorkstationV2Def def = v2(level, anchor);
        if (def != null && def.collect() != null) {
            return collectThroughInteraction(level, villager, anchor, def, null, outputs);
        }
        boolean collected = extractAvailablePreview(level, villager, anchor, outputs);
        collected |= extractAvailableOutputs(level, villager, anchor, outputs);
        collected |= extractDeclaredReturns(level, villager, anchor);
        List<ItemStack> drops = StationDropOutputs.collect(level, anchor, outputs);
        for (ItemStack drop : drops) StationProtocols.giveBack(villager, drop);
        return collected || !drops.isEmpty();
    }

    /**
     * Some stations expose an output slot for observation but require their normal player
     * interaction to take the result and reset the machine. The action's condition controls when
     * that interaction is legal; while it is false the output remains resident and collection
     * simply waits.
     */
    private static boolean collectThroughInteraction(
            ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
            WorkstationV2Def def, @Nullable DiscoveredRecipe recipe,
            Set<ResourceLocation> outputs) {
        List<ItemStack> alreadyDropped = StationDropOutputs.collect(level, anchor, outputs);
        for (ItemStack drop : alreadyDropped) StationProtocols.giveBack(villager, drop);
        if (!alreadyDropped.isEmpty()) return true;
        if (!hasResidentOutput(level, anchor, outputs) && !def.isReady(level, anchor)) return false;

        boolean collectedOutput = false;
        int attempts = recipe == null ? 64 : Math.max(1, recipe.outputCount());
        for (int attempt = 0; attempt < attempts; attempt++) {
            boolean returnedOutput = false;
            for (JsonObject action : actions(def.collect())) {
                String role = role(action);
                if (!"empty".equals(role) && !"tool".equals(role) && !"container".equals(role)) return false;
                UseOutcome outcome = runActionOutcome(level, villager, anchor, recipe, action, role, def, outputs);
                if (!outcome.succeeded()) return collectedOutput;
                returnedOutput |= outcome.returnedExpected();
            }
            List<ItemStack> outputDrops = StationDropOutputs.collect(level, anchor, outputs);
            for (ItemStack drop : outputDrops) StationProtocols.giveBack(villager, drop);
            boolean progressed = returnedOutput || !outputDrops.isEmpty();
            collectedOutput |= progressed;
            if (!progressed || !def.isReady(level, anchor)) break;
        }
        if (!collectedOutput) return false;

        Set<ResourceLocation> remainders = recipe == null
                ? remainderIds(inputIds(level, anchor)) : remainderIds(recipe);
        if (!remainders.isEmpty()) {
            List<ItemStack> returned = StationDropOutputs.collect(level, anchor, remainders);
            for (ItemStack stack : returned) StationProtocols.giveBack(villager, stack);
        }
        if (recipe != null) {
            extractDeclaredReturns(level, villager, anchor);
            extractRecipeRemainders(level, villager, anchor, recipe);
        }
        return true;
    }

    @Override
    public List<ItemStack> extractInvalidContents(ServerLevel level, BlockPos anchor,
                                                  WorkstationDef ignored) {
        WorkstationV2Def def = v2(level, anchor);
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (def == null || handler == null) return List.of();
        Set<ResourceLocation> inputs = inputIds(level, anchor);
        Set<ResourceLocation> outputs = outputIds(level, anchor);
        Set<ResourceLocation> containers = containerIds(level, anchor);
        List<ItemStack> recovered = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (def.catalystSlots().contains(slot)) continue;
            ItemStack present = handler.getStackInSlot(slot);
            if (present.isEmpty()) continue;
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(present.getItem());
            if (inputs.contains(item) || outputs.contains(item)) continue;
            if (def.containerSlots().contains(slot) && containers.contains(item)) continue;
            if (isFuelStock(level, anchor, slot, present)) continue;
            ItemStack extracted = handler.extractItem(slot, present.getCount(), false);
            if (!extracted.isEmpty()) recovered.add(extracted);
        }
        return recovered;
    }

    @Override
    public boolean hasPendingInputs(ServerLevel level, BlockPos anchor, WorkstationDef ignored,
                                    DiscoveredRecipe recipe) {
        for (IItemHandler handler : handlers(level, anchor)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack present = handler.getStackInSlot(slot);
                if (present.isEmpty()) continue;
                ResourceLocation item = BuiltInRegistries.ITEM.getKey(present.getItem());
                if (recipe.output().equals(item)) continue;
                for (RecipeIngredient ingredient : recipe.inputs()) {
                    if (ingredient.itemIds().contains(item)) return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean matchesPendingInputs(ServerLevel level, BlockPos anchor, WorkstationDef ignored,
                                        DiscoveredRecipe recipe) {
        WorkstationV2Def def = v2(level, anchor);
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (def == null || handler == null || recipe == null) return false;

        Map<ResourceLocation, Integer> contents = new java.util.LinkedHashMap<>();
        Set<ResourceLocation> outputs = outputIds(level, anchor);
        Set<ResourceLocation> containers = containerIds(level, anchor);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (def.hasExplicitIngredientSlots() && !def.ingredientSlots().contains(slot)) continue;
            if (def.reservedForInsertion(slot) || def.catalystSlots().contains(slot)) continue;
            ItemStack present = handler.getStackInSlot(slot);
            if (present.isEmpty() || isFuelStock(level, anchor, slot, present)) continue;
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(present.getItem());
            if (outputs.contains(item)) continue;
            if (def.containerSlots().contains(slot) && containers.contains(item)) continue;
            contents.merge(item, present.getCount(), Integer::sum);
        }
        return contentsMatchRecipe(contents, def.executableInputs(recipe.inputs()));
    }

    /** World-free recovery predicate: all observed inputs belong to, and satisfy, one recipe. */
    static boolean contentsMatchRecipe(Map<ResourceLocation, Integer> contents,
                                       List<RecipeIngredient> recipeInputs) {
        if (contents == null || contents.isEmpty() || recipeInputs == null || recipeInputs.isEmpty()) {
            return false;
        }
        List<RecipeIngredient> required = RecipeIngredient.merge(recipeInputs);
        for (ResourceLocation observed : contents.keySet()) {
            boolean accepted = false;
            for (RecipeIngredient ingredient : required) {
                if (ingredient.itemIds().contains(observed)) {
                    accepted = true;
                    break;
                }
            }
            if (!accepted) return false;
        }
        for (RecipeIngredient ingredient : required) {
            int available = 0;
            for (ResourceLocation accepted : ingredient.itemIds()) {
                available += Math.max(0, contents.getOrDefault(accepted, 0));
            }
            if (available < Math.max(1, ingredient.count())) return false;
        }
        return true;
    }

    private static boolean extractAvailablePreview(ServerLevel level, VillagerEntityMCA villager,
                                                   BlockPos anchor, Set<ResourceLocation> outputs) {
        WorkstationV2Def def = v2(level, anchor);
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (def == null || handler == null || def.previewSlots().isEmpty()) return false;
        for (int slot : def.previewSlots()) {
            if (slot >= handler.getSlots()) continue;
            ItemStack present = handler.getStackInSlot(slot);
            if (!isKnownOutput(present, outputs)) continue;
            ItemStack extracted = handler.extractItem(slot, present.getCount(), false);
            if (extracted.isEmpty()) continue;
            if (!consumePreviewInputs(handler, def)) {
                insertBack(handler, slot, extracted);
                return false;
            }
            StationProtocols.giveBack(villager, extracted);
            return true;
        }
        return false;
    }

    private static boolean runPreparationActions(ServerLevel level, VillagerEntityMCA villager,
                                                 BlockPos anchor, WorkstationV2Def def,
                                                 DiscoveredRecipe recipe, int copies) {
        boolean insertedIngredient = false;
        List<RecipeIngredient> ingredients = def.ordinaryInputs(recipe.inputs());
        int ingredientIndex = 0;
        for (JsonObject action : actions(def.behavior())) {
            String role = role(action);
            if ("supply".equals(role)) {
                if (!runAction(level, villager, anchor, recipe, action, role, def)) return false;
                continue;
            }
            if ("ingredient".equals(role)) {
                JsonObject interaction = WorkstationV2Def.interactionOf(action);
                boolean allRemaining = interaction != null && interaction.has("all")
                        && interaction.get("all").getAsBoolean();
                if (allRemaining) {
                    for (; ingredientIndex < ingredients.size(); ingredientIndex++) {
                        RecipeIngredient ingredient = ingredients.get(ingredientIndex);
                        if (!interactIngredient(level, villager, anchor, ingredient, def,
                                false, copies)) return false;
                    }
                } else {
                    if (ingredientIndex >= ingredients.size()
                            || !interactIngredient(level, villager, anchor,
                            ingredients.get(ingredientIndex++), def, stackBatch(def), copies)) return false;
                }
                insertedIngredient = true;
                continue;
            }
            if (!insertedIngredient && "empty".equals(role)
                    && !runAction(level, villager, anchor, recipe, action, role, def)) return false;
        }
        return insertedIngredient;
    }

    /** Runs only actions declared before ingredient staging, to satisfy transient requirements. */
    private static boolean runSetupActions(ServerLevel level, VillagerEntityMCA villager,
                                           BlockPos anchor, WorkstationV2Def def,
                                           DiscoveredRecipe recipe) {
        boolean ran = false;
        for (JsonObject action : actions(def.behavior())) {
            String role = role(action);
            if ("ingredient".equals(role)) break;
            if (!"supply".equals(role) && !"empty".equals(role)) continue;
            ran = true;
            if (!runAction(level, villager, anchor, recipe, action, role, def)) return false;
        }
        return ran;
    }

    private static boolean insertIngredients(ServerLevel level, VillagerEntityMCA villager,
                                             BlockPos anchor, WorkstationV2Def def,
                                             DiscoveredRecipe recipe,
                                             InsertionTransaction transaction) {
        // Recipe entries are positions, not merely an amount ledger. Four identical entries in a
        // cooking-pot recipe mean four occupied ingredient slots; merging them into a stack of
        // four makes the real block reject the recipe. Preserve entry boundaries and only stack
        // an individual entry's own count within its assigned slot.
        List<RecipeIngredient> entries = insertionEntries(recipe);
        int ingredientSlotIndex = 0;
        int catalystSlotIndex = 0;
        for (int recipeIndex = 0; recipeIndex < entries.size(); recipeIndex++) {
            RecipeIngredient ingredient = entries.get(recipeIndex);
            WorkstationV2Def.RecipeSlotRole role = def.recipeRole(recipeIndex);
            List<Integer> declared = role == WorkstationV2Def.RecipeSlotRole.CATALYST
                    ? def.catalystSlots() : def.ingredientSlots();
            int declaredIndex = role == WorkstationV2Def.RecipeSlotRole.CATALYST
                    ? catalystSlotIndex++ : ingredientSlotIndex++;
            Integer targetSlot = def.hasExplicitIngredientSlots() && declaredIndex < declared.size()
                    ? declared.get(declaredIndex) : null;
            if (def.hasExplicitIngredientSlots() && targetSlot == null) return false;
            if (role == WorkstationV2Def.RecipeSlotRole.CATALYST
                    && targetSlot != null && declaredSlotMatches(level, anchor, targetSlot, ingredient)) {
                continue;
            }
            ItemStack entry = takeMatching(villager, ingredient, Math.max(1, ingredient.count()));
            if (entry.isEmpty() || entry.getCount() < Math.max(1, ingredient.count())) {
                if (!entry.isEmpty()) StationProtocols.giveBack(villager, entry);
                return false;
            }
            ItemStack remainder = targetSlot == null
                    ? insertEntryIntoEmptyPublicSlot(level, anchor, def, entry, transaction)
                    : insertEntryIntoDeclaredSlot(level, anchor, targetSlot, entry, transaction);
            if (!remainder.isEmpty()) {
                StationProtocols.giveBack(villager, remainder);
                return false;
            }
        }
        return true;
    }

    /** The physical insertion plan deliberately preserves every recipe position. */
    static List<RecipeIngredient> insertionEntries(DiscoveredRecipe recipe) {
        return recipe.inputs();
    }

    private static ItemStack insertEntryIntoEmptyPublicSlot(ServerLevel level, BlockPos anchor,
                                                            WorkstationV2Def def, ItemStack stack,
                                                            InsertionTransaction transaction) {
        Direction[] priority = {Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST, Direction.DOWN};
        Set<IItemHandler> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Direction side : priority) {
            IItemHandler handler = BlockInventories.itemHandler(level, anchor, side);
            if (handler == null || !visited.add(handler)) continue;
            ItemStack remainder = insertEntryIntoEmptySlot(handler, def, stack, transaction);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        // Unsided is a fallback for blocks that expose no automation face. Vanilla Container's
        // default canPlaceItem accepts output slots too, while WorldlyContainer's sided wrappers
        // correctly narrow insertion to the block's declared ingredient positions.
        IItemHandler all = BlockInventories.itemHandler(level, anchor, null);
        if (all != null && visited.add(all)) {
            ItemStack remainder = insertEntryIntoEmptySlot(all, def, stack, transaction);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        return stack;
    }

    static ItemStack insertEntryIntoEmptySlot(IItemHandler handler, WorkstationV2Def def,
                                              ItemStack entry) {
        return insertEntryIntoEmptySlot(handler, def, entry, new InsertionTransaction());
    }

    private static ItemStack insertEntryIntoEmptySlot(IItemHandler handler, WorkstationV2Def def,
                                                      ItemStack entry,
                                                      InsertionTransaction transaction) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (def.reservedForInsertion(slot) || !handler.getStackInSlot(slot).isEmpty()) continue;
            ItemStack simulated = handler.insertItem(slot, entry, true);
            if (!simulated.isEmpty()) continue;
            return handler.insertItem(slot, entry, false);
        }
        return entry;
    }

    private static ItemStack insertEntryIntoDeclaredSlot(ServerLevel level, BlockPos anchor,
                                                         int slot, ItemStack entry,
                                                         InsertionTransaction transaction) {
        LinkedHashSet<IItemHandler> handlers = handlers(level, anchor);
        for (IItemHandler handler : handlers) {
            if (slot >= handler.getSlots()) continue;
            ItemStack current = handler.getStackInSlot(slot);
            if (!current.isEmpty()) continue;
            if (!handler.insertItem(slot, entry, true).isEmpty()) continue;
            return transaction.insert(handler, slot, entry);
        }
        return entry;
    }

    private static boolean declaredSlotMatches(ServerLevel level, BlockPos anchor, int slot,
                                               RecipeIngredient ingredient) {
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler == null || slot >= handler.getSlots()) return false;
        ItemStack stack = handler.getStackInSlot(slot);
        return !stack.isEmpty()
                && ingredient.itemIds().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean hasStagedCatalyst(ServerLevel level, BlockPos anchor,
                                            WorkstationV2Def def, DiscoveredRecipe recipe,
                                            int recipeInputIndex) {
        if (def.recipeRole(recipeInputIndex) != WorkstationV2Def.RecipeSlotRole.CATALYST) return false;
        int catalystIndex = 0;
        for (int i = 0; i < recipeInputIndex; i++) {
            if (def.recipeRole(i) == WorkstationV2Def.RecipeSlotRole.CATALYST) catalystIndex++;
        }
        if (catalystIndex >= def.catalystSlots().size()
                || recipeInputIndex >= recipe.inputs().size()) return false;
        return declaredSlotMatches(level, anchor, def.catalystSlots().get(catalystIndex),
                recipe.inputs().get(recipeInputIndex));
    }

    private static boolean insertContainer(ServerLevel level, VillagerEntityMCA villager,
                                           BlockPos anchor, WorkstationV2Def def,
                                           DiscoveredRecipe recipe,
                                           InsertionTransaction transaction) {
        if (recipe.containerItemId() == null || recipe.containerCount() <= 0) return true;
        // Some machines accept the serving vessel only when the finished product is taken out.
        // Gathering already put it in the worker's inventory; keep it there until collect.
        if (def.collectUses("container")) return true;
        if (def.containerSlots().isEmpty()) return false;
        List<IItemHandler> handlers = new ArrayList<>();
        Set<IItemHandler> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        // A block may expose its vessel slot only from a particular face. The definition names
        // the exceptional physical slot; the block's public handlers remain the authority on
        // which face can insert there.
        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST,
                Direction.EAST, Direction.DOWN, Direction.UP}) {
            IItemHandler handler = BlockInventories.itemHandler(level, anchor, side);
            if (handler != null && visited.add(handler)) handlers.add(handler);
        }
        IItemHandler unsided = BlockInventories.itemHandler(level, anchor, null);
        if (unsided != null && visited.add(unsided)) handlers.add(unsided);
        if (handlers.isEmpty()) return false;
        for (int n = 0; n < recipe.containerCount(); n++) {
            ItemStack one = takeItem(villager, recipe.containerItemId(), 1);
            if (one.isEmpty()) return false;
            ItemStack remainder = one;
            for (IItemHandler handler : handlers) {
                for (int slot : def.containerSlots()) {
                    if (slot >= handler.getSlots()
                            || !handler.insertItem(slot, remainder, true).isEmpty()) continue;
                    remainder = transaction.insert(handler, slot, remainder);
                    if (remainder.isEmpty()) break;
                }
                if (remainder.isEmpty()) break;
            }
            if (!remainder.isEmpty()) {
                StationProtocols.giveBack(villager, remainder);
                return false;
            }
        }
        return true;
    }

    /** Tries public insertion with fuel the villager already reserved; blocks reject it elsewhere. */
    private static void opportunisticallyFuel(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        var fuel = com.aetherianartificer.townstead.supply.SupplyLines.matcher(level,
                com.aetherianartificer.townstead.supply.TownsteadSupplyLines.FURNACE_FUEL);
        WorkstationV2Def def = v2(level, anchor);
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            ItemStack source = villager.getInventory().getItem(slot);
            if (!fuel.test(source)) continue;
            ItemStack one = StationInventoryOps.copyOne(source);
            int[] slots = fuelSlots(level, anchor, one);
            IItemHandler all = BlockInventories.itemHandler(level, anchor, null);
            if (all != null) {
                for (int target : slots) {
                    if (target >= all.getSlots() || !all.insertItem(target, one, true).isEmpty()) continue;
                    all.insertItem(target, one, false);
                    source.shrink(1);
                    return;
                }
            }
            // A non-sided item handler does not publish which of its permissive slots is fuel.
            // If the block also exposes a sided-container fuel lane, use only those exact slot
            // numbers through the sided capability. Treating the first slot that accepts coal as
            // fuel feeds coal to cutting boards, trays and other ordinary item surfaces.
            for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                IItemHandler handler = BlockInventories.itemHandler(level, anchor, side);
                if (handler == null) continue;
                for (int target : slots) {
                    if (target >= handler.getSlots() || !handler.insertItem(target, one, true).isEmpty()) continue;
                    handler.insertItem(target, one, false);
                    source.shrink(1);
                    return;
                }
            }
        }
    }

    /** Whether the block's public sided inventory exposes a slot that accepts ordinary fuel. */
    public static boolean acceptsFuel(ServerLevel level, BlockPos anchor) {
        for (ItemStack probe : List.of(new ItemStack(net.minecraft.world.item.Items.COAL),
                new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS))) {
            if (fuelSlots(level, anchor, probe).length > 0) return true;
        }
        return false;
    }

    public static boolean hasFuel(ServerLevel level, BlockPos anchor) {
        var matcher = com.aetherianartificer.townstead.supply.SupplyLines.matcher(level,
                com.aetherianartificer.townstead.supply.TownsteadSupplyLines.FURNACE_FUEL);
        WorkstationV2Def def = v2(level, anchor);
        if (level.getBlockEntity(anchor) instanceof WorldlyContainer sided) {
            for (int slot : sided.getSlotsForFace(Direction.NORTH)) {
                if (def != null && def.containerSlots().contains(slot)) continue;
                if (matcher.test(sided.getItem(slot))) return true;
            }
            return false;
        }
        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            IItemHandler handler = BlockInventories.itemHandler(level, anchor, side);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (matcher.test(handler.getStackInSlot(slot))) return true;
            }
        }
        return false;
    }

    private static int[] fuelSlots(ServerLevel level, BlockPos anchor, ItemStack probe) {
        if (!(level.getBlockEntity(anchor) instanceof WorldlyContainer sided)) return new int[0];
        WorkstationV2Def def = v2(level, anchor);
        java.util.stream.IntStream.Builder slots = java.util.stream.IntStream.builder();
        for (int slot : sideOnlySlots(sided.getSlotsForFace(Direction.NORTH),
                sided.getSlotsForFace(Direction.UP), sided.getSlotsForFace(Direction.DOWN))) {
            // Ingredient/output surfaces commonly accept arbitrary items when empty. A real
            // furnace-style fuel channel is distinguished by being exposed from a horizontal
            // face but not from the top or bottom (vanilla furnaces and Farm & Charm's stove).
            if (def != null && def.containerSlots().contains(slot)) continue;
            if (def != null && (def.ingredientSlots().contains(slot)
                    || def.catalystSlots().contains(slot)
                    || def.outputSlots().contains(slot)
                    || def.returnSlots().contains(slot)
                    || def.previewSlots().contains(slot))) continue;
            if (sided.canPlaceItemThroughFace(slot, probe, Direction.NORTH)) slots.add(slot);
        }
        return slots.build().toArray();
    }

    static int[] sideOnlySlots(int[] horizontal, int[] up, int[] down) {
        Set<Integer> vertical = new java.util.HashSet<>();
        for (int slot : up) vertical.add(slot);
        for (int slot : down) vertical.add(slot);
        return java.util.Arrays.stream(horizontal).filter(slot -> !vertical.contains(slot)).toArray();
    }

    private static boolean interactIngredient(ServerLevel level, VillagerEntityMCA villager,
                                              BlockPos anchor, DiscoveredRecipe recipe,
                                              WorkstationV2Def def, boolean stack) {
        return interactIngredient(level, villager, anchor, recipe, def, stack, 1);
    }

    private static boolean interactIngredient(ServerLevel level, VillagerEntityMCA villager,
                                              BlockPos anchor, DiscoveredRecipe recipe,
                                              WorkstationV2Def def, boolean stack, int copies) {
        if (recipe.inputs().isEmpty()) return false;
        return interactIngredient(level, villager, anchor, recipe.inputs().get(0), def, stack, copies);
    }

    private static boolean interactIngredient(ServerLevel level, VillagerEntityMCA villager,
                                              BlockPos anchor, RecipeIngredient ingredient,
                                              WorkstationV2Def def, boolean stack, int copies) {
        if (!stack && copies > 1) {
            for (int copy = 0; copy < copies; copy++) {
                if (!interactIngredient(level, villager, anchor, ingredient, def, false, 1)) return false;
            }
            return true;
        }
        ItemStack held = stack
                ? takeMatchingBatch(villager, ingredient,
                        Math.max(1, ingredient.count()) * Math.max(1, copies))
                : takeMatching(villager, ingredient, Math.max(1, ingredient.count()));
        if (held.isEmpty()) return false;
        JsonObject action = new JsonObject();
        action.addProperty("type", "pheno:use_block");
        action.addProperty("item", "ingredient");
        return useBlock(level, villager, anchor, action, held);
    }

    private static boolean runAction(ServerLevel level, VillagerEntityMCA villager,
                                     BlockPos anchor, @Nullable DiscoveredRecipe recipe, JsonObject action,
                                     String role, WorkstationV2Def def) {
        return runActionOutcome(level, villager, anchor, recipe, action, role, def, Set.of()).succeeded();
    }

    private static UseOutcome runActionOutcome(ServerLevel level, VillagerEntityMCA villager,
                                     BlockPos anchor, @Nullable DiscoveredRecipe recipe, JsonObject action,
                                     String role, WorkstationV2Def def, Set<ResourceLocation> expected) {
        JsonObject interaction = WorkstationV2Def.interactionOf(action);
        if (interaction == null) return new UseOutcome(false, false);
        ItemStack held = ItemStack.EMPTY;
        if ("tool".equals(role)) {
            // Cleanup on station claim has no selected recipe. An explicitly declared tool is
            // nevertheless enough information to perform the real collection interaction.
            if (recipe == null && !interaction.has("tool")) return new UseOutcome(false, false);
            for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = villager.getInventory().getItem(slot);
                boolean matches = interaction.has("tool")
                        ? WorkstationV2Def.actionToolMatches(interaction, candidate)
                        : WorkRecipeRegistry.recipeToolMatches(recipe, candidate);
                if (matches) {
                    held = candidate.split(1);
                    break;
                }
            }
            if (held.isEmpty()) return new UseOutcome(false, false);
        } else if ("supply".equals(role)) {
            if (!interaction.has("supply")) return new UseOutcome(false, false);
            String selector = interaction.get("supply").getAsString();
            for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = villager.getInventory().getItem(slot);
                if (WorkstationV2Def.actionSelectorMatches(selector, candidate)) {
                    held = candidate.split(1);
                    break;
                }
            }
            if (held.isEmpty()) return new UseOutcome(false, false);
        } else if ("container".equals(role)) {
            if (recipe == null) return new UseOutcome(false, false);
            if (recipe.containerItemId() == null) {
                // Mixed-shape recipe families may package some results in a carrier while other
                // results are removed with a tool. A container action is inapplicable—not an
                // empty-hand interaction—when this particular recipe declares no carrier.
                return new UseOutcome(true, false);
            }
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(recipe.containerItemId());
            for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = villager.getInventory().getItem(slot);
                if (candidate.is(item)) {
                    held = candidate.split(1);
                    break;
                }
            }
            if (held.isEmpty()) return new UseOutcome(false, false);
        }
        return useBlockOutcome(level, villager, anchor, action, role, held, expected);
    }

    private static boolean useBlock(ServerLevel level, VillagerEntityMCA villager,
                                    BlockPos pos, JsonObject action, ItemStack supplied) {
        return useBlockOutcome(level, villager, pos, action, role(action), supplied, Set.of()).succeeded();
    }

    private record UseOutcome(boolean succeeded, boolean returnedExpected) {}

    private static UseOutcome useBlockOutcome(ServerLevel level, VillagerEntityMCA villager,
                                    BlockPos pos, JsonObject action, String role, ItemStack supplied,
                                    Set<ResourceLocation> expected) {
        var parsed = com.aetherianartificer.townstead.pheno.action.block.BlockActions.parse(action);
        if (parsed == null) {
            if (!supplied.isEmpty()) StationProtocols.giveBack(villager, supplied);
            return new UseOutcome(false, false);
        }
        var context = new com.aetherianartificer.townstead.pheno.action.block.BlockActionContext(
                level, pos, villager).withItemRole(role, supplied.copy());
        parsed.run(context);
        ItemStack remainder = context.itemRole(role);
        boolean returnedExpected = !remainder.isEmpty()
                && expected.contains(BuiltInRegistries.ITEM.getKey(remainder.getItem()));
        if (!remainder.isEmpty()) StationProtocols.giveBack(villager, remainder);
        for (ItemStack returned : context.returnedItems()) {
            returnedExpected |= expected.contains(BuiltInRegistries.ITEM.getKey(returned.getItem()));
            StationProtocols.giveBack(villager, returned);
        }
        return new UseOutcome(context.succeeded(), returnedExpected);
    }

    private static boolean hasOutput(ServerLevel level, BlockPos anchor, ResourceLocation output) {
        return hasResidentOutput(level, anchor, Set.of(output))
                || StationDropOutputs.has(level, anchor, Set.of(output));
    }

    private static boolean hasResidentOutput(ServerLevel level, BlockPos anchor,
                                             Set<ResourceLocation> outputs) {
        WorkstationV2Def def = v2(level, anchor);
        LinkedHashSet<IItemHandler> available = handlers(level, anchor);
        if (!available.isEmpty()) {
            List<Integer> declared = def == null ? List.of() : declaredOutputSlots(def);
            if (!declared.isEmpty()) {
                for (IItemHandler handler : available) {
                    for (int slot : declared) {
                        if (slot >= handler.getSlots()) continue;
                        ItemStack stack = handler.getStackInSlot(slot);
                        if (outputs.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return true;
                    }
                }
                return false;
            }
            for (IItemHandler handler : available) {
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (outputs.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return true;
                }
            }
        }
        return false;
    }

    /**
     * Finished output is public machine state, not producer-session state. The block-owned recipe
     * types tell us which items can be results, while the block's sided extraction contract tells
     * us whether one is actually sitting in its output channel. No mod slot number is involved.
     */
    private static boolean hasAvailableOutput(ServerLevel level, BlockPos anchor) {
        Set<ResourceLocation> outputs = outputIds(level, anchor);
        if (outputs.isEmpty()) return false;
        WorkstationV2Def def = v2(level, anchor);
        IItemHandler all = BlockInventories.itemHandler(level, anchor, null);
        if (def != null && all != null && hasAnyStackInSlots(all, def.returnSlots())) return true;
        if (def != null && all != null && !declaredOutputSlots(def).isEmpty()) {
            return hasKnownOutputInSlots(all, outputs, declaredOutputSlots(def))
                    || StationDropOutputs.has(level, anchor, outputs);
        }
        IItemHandler down = BlockInventories.itemHandler(level, anchor, Direction.DOWN);
        if (hasExtractableKnownOutput(down, outputs, false)) return true;
        for (Direction side : Direction.values()) {
            if (side == Direction.DOWN) continue;
            if (hasExtractableKnownOutput(
                    BlockInventories.itemHandler(level, anchor, side), outputs, true)) return true;
        }
        return StationDropOutputs.has(level, anchor, outputs);
    }

    private static boolean extractAvailableOutputs(ServerLevel level, VillagerEntityMCA villager,
                                                   BlockPos anchor, Set<ResourceLocation> outputs) {
        WorkstationV2Def def = v2(level, anchor);
        IItemHandler all = BlockInventories.itemHandler(level, anchor, null);
        if (def != null && all != null && !def.outputSlots().isEmpty()) {
            return extractKnownOutputsFromSlots(all, villager, outputs, def.outputSlots());
        }
        IItemHandler down = BlockInventories.itemHandler(level, anchor, Direction.DOWN);
        boolean collected = extractKnownOutputs(down, villager, outputs, false);
        if (collected) return true;
        Set<IItemHandler> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (down != null) visited.add(down);
        for (Direction side : Direction.values()) {
            IItemHandler handler = BlockInventories.itemHandler(level, anchor, side);
            if (handler == null || !visited.add(handler)) continue;
            collected |= extractKnownOutputs(handler, villager, outputs, true);
        }
        return collected;
    }

    private static boolean hasExtractableKnownOutput(@Nullable IItemHandler handler,
                                                     Set<ResourceLocation> outputs,
                                                     boolean requireOutputOnly) {
        if (handler == null) return false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!isKnownOutput(stack, outputs)) continue;
            if (handler.extractItem(slot, 1, true).isEmpty()) continue;
            if (!requireOutputOnly || rejectsInsertion(handler, slot, stack)) return true;
        }
        return false;
    }

    private static boolean extractKnownOutputs(@Nullable IItemHandler handler,
                                               VillagerEntityMCA villager,
                                               Set<ResourceLocation> outputs,
                                               boolean requireOutputOnly) {
        if (handler == null) return false;
        boolean collected = false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!isKnownOutput(stack, outputs)) continue;
            if (handler.extractItem(slot, 1, true).isEmpty()) continue;
            if (requireOutputOnly && !rejectsInsertion(handler, slot, stack)) continue;
            ItemStack extracted = handler.extractItem(slot, stack.getCount(), false);
            if (extracted.isEmpty()) continue;
            StationProtocols.giveBack(villager, extracted);
            collected = true;
        }
        return collected;
    }

    private static boolean rejectsInsertion(IItemHandler handler, int slot, ItemStack stack) {
        ItemStack probe = stack.copy();
        probe.setCount(1);
        return !handler.insertItem(slot, probe, true).isEmpty();
    }

    private static boolean isKnownOutput(ItemStack stack, Set<ResourceLocation> outputs) {
        return !stack.isEmpty() && outputs.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static synchronized Set<ResourceLocation> outputIds(ServerLevel level, BlockPos anchor) {
        int generation = WorkRecipeRegistry.generation();
        if (outputCacheGeneration != generation) {
            OUTPUTS_BY_BLOCK.clear();
            CONTAINERS_BY_BLOCK.clear();
            INPUTS_BY_BLOCK.clear();
            outputCacheGeneration = generation;
        }
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(anchor).getBlock());
        return OUTPUTS_BY_BLOCK.computeIfAbsent(block, ignored -> {
            Set<ResourceLocation> types = WorkstationRecipeTypes.forBlock(block);
            LinkedHashSet<ResourceLocation> outputs = new LinkedHashSet<>();
            for (DiscoveredRecipe candidate : WorkRecipeRegistry.getRecipes(level)) {
                ResourceLocation type = WorkRecipeRegistry.recipeTypeId(candidate);
                if (type != null && types.contains(type)) outputs.add(candidate.output());
            }
            return Set.copyOf(outputs);
        });
    }

    private static synchronized Set<ResourceLocation> containerIds(ServerLevel level,
                                                                   BlockPos anchor) {
        // outputIds owns generation invalidation for both recipe-derived caches.
        outputIds(level, anchor);
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(anchor).getBlock());
        return CONTAINERS_BY_BLOCK.computeIfAbsent(block, ignored -> {
            Set<ResourceLocation> types = WorkstationRecipeTypes.forBlock(block);
            if (types.isEmpty()) return Set.of();
            LinkedHashSet<ResourceLocation> containers = new LinkedHashSet<>();
            for (DiscoveredRecipe candidate : WorkRecipeRegistry.getRecipes(level)) {
                ResourceLocation type = WorkRecipeRegistry.recipeTypeId(candidate);
                if (type != null && types.contains(type) && candidate.containerItemId() != null) {
                    containers.add(candidate.containerItemId());
                }
            }
            return Set.copyOf(containers);
        });
    }

    private static synchronized Set<ResourceLocation> inputIds(ServerLevel level,
                                                               BlockPos anchor) {
        // outputIds owns generation invalidation for every recipe-derived block cache.
        outputIds(level, anchor);
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(anchor).getBlock());
        return INPUTS_BY_BLOCK.computeIfAbsent(block, ignored -> {
            Set<ResourceLocation> types = WorkstationRecipeTypes.forBlock(block);
            LinkedHashSet<ResourceLocation> inputs = new LinkedHashSet<>();
            for (DiscoveredRecipe candidate : WorkRecipeRegistry.getRecipes(level)) {
                ResourceLocation type = WorkRecipeRegistry.recipeTypeId(candidate);
                if (type == null || !types.contains(type)) continue;
                for (RecipeIngredient ingredient : candidate.inputs()) {
                    inputs.addAll(ingredient.itemIds());
                }
            }
            return Set.copyOf(inputs);
        });
    }

    /** Fuel is persistent machine stock, including a non-fuel remainder occupying its fuel slot. */
    private static boolean isFuelStock(ServerLevel level, BlockPos anchor, int slot,
                                       ItemStack ignored) {
        for (ItemStack probe : List.of(new ItemStack(net.minecraft.world.item.Items.COAL),
                new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS))) {
            for (int fuelSlot : fuelSlots(level, anchor, probe)) {
                if (fuelSlot == slot) return true;
            }
        }
        return false;
    }

    private static boolean extractOutput(ServerLevel level, VillagerEntityMCA villager,
                                         BlockPos anchor, ResourceLocation output) {
        WorkstationV2Def def = v2(level, anchor);
        IItemHandler all = BlockInventories.itemHandler(level, anchor, null);
        if (def != null && all != null && !def.previewSlots().isEmpty()) {
            for (int slot : def.previewSlots()) {
                if (slot >= all.getSlots()) continue;
                ItemStack stack = all.getStackInSlot(slot);
                if (!output.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
                ItemStack extracted = all.extractItem(slot, stack.getCount(), false);
                if (extracted.isEmpty()) continue;
                if (!consumePreviewInputs(all, def)) {
                    insertBack(all, slot, extracted);
                    return false;
                }
                StationProtocols.giveBack(villager, extracted);
                return true;
            }
        }
        if (def != null && all != null && !def.outputSlots().isEmpty()) {
            return extractKnownOutputsFromSlots(all, villager, Set.of(output), def.outputSlots());
        }
        if (def != null && !def.previewSlots().isEmpty()) {
            return false;
        }
        LinkedHashSet<IItemHandler> handlers = handlers(level, anchor);
        boolean collected = false;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!output.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
                ItemStack extracted = handler.extractItem(slot, stack.getCount(), false);
                if (!extracted.isEmpty()) {
                    StationProtocols.giveBack(villager, extracted);
                    collected = true;
                }
            }
            if (collected) break;
        }
        return collected;
    }

    private static boolean consumePreviewInputs(IItemHandler handler, WorkstationV2Def def) {
        for (int slot : def.ingredientSlots()) {
            if (slot >= handler.getSlots() || handler.extractItem(slot, 1, true).isEmpty()) return false;
        }
        for (int slot : def.ingredientSlots()) {
            handler.extractItem(slot, 1, false);
        }
        return true;
    }

    private static void insertBack(IItemHandler handler, int slot, ItemStack stack) {
        if (stack.isEmpty() || slot >= handler.getSlots()) return;
        handler.insertItem(slot, stack, false);
    }

    private static boolean extractDeclaredReturns(ServerLevel level, VillagerEntityMCA villager,
                                                   BlockPos anchor) {
        WorkstationV2Def def = v2(level, anchor);
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (def == null || handler == null || def.returnSlots().isEmpty()) return false;
        boolean collected = false;
        for (int slot : def.returnSlots()) {
            if (slot >= handler.getSlots()) continue;
            ItemStack stack = handler.extractItem(slot, handler.getStackInSlot(slot).getCount(), false);
            if (stack.isEmpty()) continue;
            StationProtocols.giveBack(villager, stack);
            collected = true;
        }
        return collected;
    }

    /**
     * Recipes can leave vanilla crafting remainders in their former ingredient positions. Their
     * item identity is public recipe/item behavior, so ordinary bowls and similar inventories do
     * not need mod-specific return-slot JSON merely to get a bucket or bottle back.
     */
    private static boolean extractRecipeRemainders(ServerLevel level, VillagerEntityMCA villager,
                                                   BlockPos anchor, DiscoveredRecipe recipe) {
        LinkedHashSet<net.minecraft.world.item.Item> remainderItems = new LinkedHashSet<>();
        for (RecipeIngredient ingredient : recipe.inputs()) {
            for (ResourceLocation id : ingredient.itemIds()) {
                net.minecraft.world.item.Item input = BuiltInRegistries.ITEM.get(id);
                if (input == net.minecraft.world.item.Items.AIR || !input.hasCraftingRemainingItem()) continue;
                net.minecraft.world.item.Item remainder = input.getCraftingRemainingItem();
                if (remainder != null && remainder != net.minecraft.world.item.Items.AIR) {
                    remainderItems.add(remainder);
                }
            }
        }
        if (remainderItems.isEmpty()) return false;

        boolean collected = false;
        for (IItemHandler handler : handlers(level, anchor)) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack present = handler.getStackInSlot(slot);
                if (present.isEmpty() || !remainderItems.contains(present.getItem())) continue;
                ItemStack extracted = handler.extractItem(slot, present.getCount(), false);
                if (extracted.isEmpty()) continue;
                StationProtocols.giveBack(villager, extracted);
                collected = true;
            }
        }
        return collected;
    }

    private static Set<ResourceLocation> remainderIds(DiscoveredRecipe recipe) {
        LinkedHashSet<ResourceLocation> inputs = new LinkedHashSet<>();
        for (RecipeIngredient ingredient : recipe.inputs()) inputs.addAll(ingredient.itemIds());
        return remainderIds(inputs);
    }

    private static Set<ResourceLocation> remainderIds(Set<ResourceLocation> inputs) {
        LinkedHashSet<ResourceLocation> remainders = new LinkedHashSet<>();
        for (ResourceLocation id : inputs) {
            net.minecraft.world.item.Item input = BuiltInRegistries.ITEM.get(id);
            if (input == net.minecraft.world.item.Items.AIR || !input.hasCraftingRemainingItem()) continue;
            net.minecraft.world.item.Item remainder = input.getCraftingRemainingItem();
            if (remainder != null && remainder != net.minecraft.world.item.Items.AIR) {
                remainders.add(BuiltInRegistries.ITEM.getKey(remainder));
            }
        }
        return Set.copyOf(remainders);
    }

    private static boolean hasKnownOutputInSlots(IItemHandler handler, Set<ResourceLocation> outputs,
                                                 List<Integer> slots) {
        for (int slot : slots) {
            if (slot >= handler.getSlots()) continue;
            if (isKnownOutput(handler.getStackInSlot(slot), outputs)) return true;
        }
        return false;
    }

    private static boolean hasAnyStackInSlots(IItemHandler handler, List<Integer> slots) {
        for (int slot : slots) {
            if (slot < handler.getSlots() && !handler.getStackInSlot(slot).isEmpty()) return true;
        }
        return false;
    }

    private static boolean extractKnownOutputsFromSlots(IItemHandler handler,
                                                        VillagerEntityMCA villager,
                                                        Set<ResourceLocation> outputs,
                                                        List<Integer> slots) {
        boolean collected = false;
        for (int slot : slots) {
            if (slot >= handler.getSlots()) continue;
            ItemStack present = handler.getStackInSlot(slot);
            if (!isKnownOutput(present, outputs)) continue;
            ItemStack extracted = handler.extractItem(slot, present.getCount(), false);
            if (extracted.isEmpty()) continue;
            StationProtocols.giveBack(villager, extracted);
            collected = true;
        }
        return collected;
    }

    private static List<Integer> declaredOutputSlots(WorkstationV2Def def) {
        return concat(def.outputSlots(), def.previewSlots());
    }

    private static List<Integer> concat(List<Integer> first, List<Integer> second) {
        ArrayList<Integer> out = new ArrayList<>(first.size() + second.size());
        out.addAll(first);
        out.addAll(second);
        return out;
    }

    private static LinkedHashSet<IItemHandler> handlers(ServerLevel level, BlockPos anchor) {
        LinkedHashSet<IItemHandler> out = new LinkedHashSet<>();
        IItemHandler all = BlockInventories.itemHandler(level, anchor, null);
        if (all != null) out.add(all);
        for (Direction side : Direction.values()) {
            IItemHandler handler = BlockInventories.itemHandler(level, anchor, side);
            if (handler != null) out.add(handler);
        }
        return out;
    }

    private static ItemStack takeMatching(VillagerEntityMCA villager, RecipeIngredient ingredient, int maximum) {
        int needed = Math.max(1, maximum);
        var inventory = villager.getInventory();
        for (int seedSlot = 0; seedSlot < inventory.getContainerSize(); seedSlot++) {
            ItemStack seed = inventory.getItem(seedSlot);
            ResourceLocation seedId = BuiltInRegistries.ITEM.getKey(seed.getItem());
            if (seed.isEmpty() || !ingredient.itemIds().contains(seedId)) continue;
            int available = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (StationInventoryOps.sameItemAndComponents(seed, candidate)) {
                    available += candidate.getCount();
                }
            }
            if (available < needed) continue;
            ItemStack taken = StationInventoryOps.copyWithCount(seed, needed);
            int remaining = needed;
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (!StationInventoryOps.sameItemAndComponents(seed, candidate)) continue;
                int amount = Math.min(remaining, candidate.getCount());
                candidate.shrink(amount);
                remaining -= amount;
            }
            return taken;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Takes the largest compatible batch currently on hand, up to the item's real stack limit.
     * A stack-capacity station means "as many as available", not "require an infinite stack".
     */
    private static ItemStack takeMatchingBatch(VillagerEntityMCA villager,
                                               RecipeIngredient ingredient, int maximum) {
        var inventory = villager.getInventory();
        int minimum = Math.max(1, ingredient.count());
        for (int seedSlot = 0; seedSlot < inventory.getContainerSize(); seedSlot++) {
            ItemStack seed = inventory.getItem(seedSlot);
            ResourceLocation seedId = BuiltInRegistries.ITEM.getKey(seed.getItem());
            if (seed.isEmpty() || !ingredient.itemIds().contains(seedId)) continue;
            int available = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (StationInventoryOps.sameItemAndComponents(seed, candidate)) {
                    available += candidate.getCount();
                }
            }
            int amount = Math.min(Math.max(1, maximum),
                    Math.min(seed.getMaxStackSize(), available));
            if (amount < minimum) continue;
            ItemStack taken = StationInventoryOps.copyWithCount(seed, amount);
            int remaining = amount;
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack candidate = inventory.getItem(slot);
                if (!StationInventoryOps.sameItemAndComponents(seed, candidate)) continue;
                int moved = Math.min(remaining, candidate.getCount());
                candidate.shrink(moved);
                remaining -= moved;
            }
            return taken;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack takeItem(VillagerEntityMCA villager, ResourceLocation id, int count) {
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            ItemStack stack = villager.getInventory().getItem(slot);
            if (!stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                return stack.split(Math.min(count, stack.getCount()));
            }
        }
        return ItemStack.EMPTY;
    }

    private static List<JsonObject> actions(@Nullable JsonElement behavior) {
        if (behavior == null) return List.of();
        List<JsonObject> out = new ArrayList<>();
        if (behavior.isJsonArray()) {
            for (JsonElement element : behavior.getAsJsonArray()) out.add(element.getAsJsonObject());
        } else out.add(behavior.getAsJsonObject());
        return out;
    }

    private static String role(JsonObject action) {
        JsonObject interaction = WorkstationV2Def.interactionOf(action);
        return interaction != null && interaction.has("item")
                ? interaction.get("item").getAsString() : "empty";
    }

    private static void rollback(VillagerEntityMCA villager, InsertionTransaction transaction) {
        for (ItemStack returned : transaction.rollback()) {
            StationProtocols.giveBack(villager, returned);
        }
    }

    /** Journal of inventory writes made while staging one recipe operation. */
    static final class InsertionTransaction {
        private record Commit(IItemHandler handler, int slot, ItemStack inserted) {}

        private final List<Commit> commits = new ArrayList<>();

        ItemStack insert(IItemHandler handler, int slot, ItemStack offered) {
            ItemStack remainder = handler.insertItem(slot, offered, false);
            int insertedCount = offered.getCount() - remainder.getCount();
            if (insertedCount > 0) {
                commits.add(new Commit(handler, slot,
                        StationInventoryOps.copyWithCount(offered, insertedCount)));
            }
            return remainder;
        }

        List<ItemStack> rollback() {
            List<ItemStack> returned = new ArrayList<>();
            for (int i = commits.size() - 1; i >= 0; i--) {
                Commit commit = commits.get(i);
                ItemStack present = commit.handler().getStackInSlot(commit.slot());
                if (present.isEmpty()
                        || !StationInventoryOps.sameItemAndComponents(present, commit.inserted())) {
                    continue;
                }
                int count = Math.min(present.getCount(), commit.inserted().getCount());
                ItemStack extracted = commit.handler().extractItem(commit.slot(), count, false);
                if (!extracted.isEmpty()) returned.add(extracted);
            }
            commits.clear();
            return returned;
        }
    }

    private static boolean stackBatch(WorkstationV2Def def) {
        return def.stackPerPosition();
    }

    private static boolean connectedStructure(WorkstationV2Def def) {
        return def.structureSelector() != null;
    }

    private static List<BlockPos> connected(ServerLevel level, BlockPos origin, WorkstationV2Def def) {
        if (def.structureSelector() == null) return List.of();
        return def.structureSelector().select(stationSelectorContext(level, origin, def));
    }

    private static com.aetherianartificer.townstead.pheno.selector.SelectorContext stationSelectorContext(
            ServerLevel level, BlockPos origin, WorkstationV2Def def) {
        return com.aetherianartificer.townstead.pheno.selector.SelectorContext
                .ofBlock(level, origin, null)
                .withDefaultBlockMembership(pos -> def.blocks().contains(
                        BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())));
    }
}
