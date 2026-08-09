package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
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
import java.util.Set;
import java.util.UUID;

/** Generic V2 execution: public inventory contracts plus real player-like block interaction. */
public final class DataDrivenStationAdapter implements StationAdapters.Adapter {
    public static final String NAME = "townstead:data_driven";
    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("266f5434-42bb-47d9-b014-2b42e12ca454"), "[TownsteadWorker]");

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
        return type != null && WorkstationRecipeTypes.forBlock(block).contains(type);
    }

    @Override
    public int capacity(ServerLevel level, BlockPos anchor, WorkstationDef ignored) {
        WorkstationV2Def def = v2(level, anchor);
        if (def == null || !def.isOperational(level, anchor)) return 0;
        if (connectedStructure(def)) return connected(level, anchor, def).size();
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler == null) return 1;
        int free = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!def.containerSlots().contains(slot) && handler.getStackInSlot(slot).isEmpty()) free++;
        }
        return Math.max(0, free);
    }

    @Override
    public @Nullable BlockPos anchor(ServerLevel level, BlockPos pos, WorkstationDef ignored) {
        WorkstationV2Def def = v2(level, pos);
        if (def == null || !connectedStructure(def)) return null;
        return connected(level, pos, def).stream()
                .min(java.util.Comparator.comparingLong(BlockPos::asLong))
                .filter(anchor -> !anchor.equals(pos)).orElse(null);
    }

    @Override
    public StationAdapters.StationPhase phase(ServerLevel level, BlockPos anchor,
                                              WorkstationDef ignored,
                                              @Nullable DiscoveredRecipe recipe) {
        WorkstationV2Def def = v2(level, anchor);
        if (def == null || !def.isOperational(level, anchor)) return StationAdapters.StationPhase.FOREIGN;
        if (recipe != null && hasOutput(level, anchor, recipe.output())) {
            return StationAdapters.StationPhase.READY;
        }
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) return StationAdapters.StationPhase.WORKING;
            }
        }
        return StationAdapters.StationPhase.IDLE;
    }

    @Override
    public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                          WorkstationDef ignored, DiscoveredRecipe recipe) {
        WorkstationV2Def def = v2(level, anchor);
        if (def == null || !def.isOperational(level, anchor)) return false;

        // A declared ingredient interaction is authoritative (skillets, mincers and silos).
        if (def.behaviorUses("ingredient")) {
            if (!runPreparationActions(level, villager, anchor, def, recipe)) return false;
        } else {
            // A cutting board has no separate ingredient declaration: its normal player contract
            // accepts the ingredient, then the exceptional tool action processes it.
            boolean boardLike = def.behaviorUses("tool");
            if (boardLike) {
                if (!interactIngredient(level, villager, anchor, recipe, def, false)) return false;
            } else if (!insertIngredients(level, villager, anchor, def, recipe)) {
                return false;
            }
        }

        if (!insertContainer(level, villager, anchor, def, recipe)) return false;
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
        boolean collected = extractOutput(level, villager, anchor, recipe.output());
        List<ItemStack> drops = StationDropOutputs.collect(level, anchor, Set.of(recipe.output()));
        for (ItemStack drop : drops) StationProtocols.giveBack(villager, drop);
        return collected || !drops.isEmpty();
    }

    private static boolean runPreparationActions(ServerLevel level, VillagerEntityMCA villager,
                                                 BlockPos anchor, WorkstationV2Def def,
                                                 DiscoveredRecipe recipe) {
        for (JsonObject action : actions(def.behavior())) {
            String role = role(action);
            if ("ingredient".equals(role)) {
                if (!interactIngredient(level, villager, anchor, recipe, def, stackBatch(def))) return false;
                return true;
            }
            if ("empty".equals(role)
                    && !runAction(level, villager, anchor, recipe, action, role, def)) return false;
        }
        return false;
    }

    private static boolean insertIngredients(ServerLevel level, VillagerEntityMCA villager,
                                             BlockPos anchor, WorkstationV2Def def,
                                             DiscoveredRecipe recipe) {
        // Recipe entries are positions, not merely an amount ledger. Four identical entries in a
        // cooking-pot recipe mean four occupied ingredient slots; merging them into a stack of
        // four makes the real block reject the recipe. Preserve entry boundaries and only stack
        // an individual entry's own count within its assigned slot.
        for (RecipeIngredient ingredient : insertionEntries(recipe)) {
            ItemStack entry = takeMatching(villager, ingredient, Math.max(1, ingredient.count()));
            if (entry.isEmpty() || entry.getCount() < Math.max(1, ingredient.count())) {
                if (!entry.isEmpty()) StationProtocols.giveBack(villager, entry);
                return false;
            }
            ItemStack remainder = insertEntryIntoEmptyPublicSlot(level, anchor, def, entry);
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
                                                            WorkstationV2Def def, ItemStack stack) {
        Direction[] priority = {Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST, Direction.DOWN};
        Set<IItemHandler> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Direction side : priority) {
            IItemHandler handler = BlockInventories.itemHandler(level, anchor, side);
            if (handler == null || !visited.add(handler)) continue;
            ItemStack remainder = insertEntryIntoEmptySlot(handler, def, stack);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        // Unsided is a fallback for blocks that expose no automation face. Vanilla Container's
        // default canPlaceItem accepts output slots too, while WorldlyContainer's sided wrappers
        // correctly narrow insertion to the block's declared ingredient positions.
        IItemHandler all = BlockInventories.itemHandler(level, anchor, null);
        if (all != null && visited.add(all)) {
            ItemStack remainder = insertEntryIntoEmptySlot(all, def, stack);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        return stack;
    }

    static ItemStack insertEntryIntoEmptySlot(IItemHandler handler, WorkstationV2Def def,
                                              ItemStack entry) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (def.containerSlots().contains(slot) || !handler.getStackInSlot(slot).isEmpty()) continue;
            ItemStack simulated = handler.insertItem(slot, entry, true);
            if (!simulated.isEmpty()) continue;
            return handler.insertItem(slot, entry, false);
        }
        return entry;
    }

    private static boolean insertContainer(ServerLevel level, VillagerEntityMCA villager,
                                           BlockPos anchor, WorkstationV2Def def,
                                           DiscoveredRecipe recipe) {
        if (recipe.containerItemId() == null || recipe.containerCount() <= 0) return true;
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
                    remainder = handler.insertItem(slot, remainder, false);
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
            for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                IItemHandler handler = BlockInventories.itemHandler(level, anchor, side);
                if (handler == null) continue;
                int target = firstAcceptingSlotOutsideContainers(handler, def, one);
                if (target >= 0) {
                    handler.insertItem(target, one, false);
                    source.shrink(1);
                    return;
                }
            }
        }
    }

    /** Whether the block's public sided inventory exposes a slot that accepts ordinary fuel. */
    public static boolean acceptsFuel(ServerLevel level, BlockPos anchor) {
        WorkstationV2Def def = v2(level, anchor);
        for (ItemStack probe : List.of(new ItemStack(net.minecraft.world.item.Items.COAL),
                new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS))) {
            if (fuelSlots(level, anchor, probe).length > 0) return true;
            if (level.getBlockEntity(anchor) instanceof WorldlyContainer) continue;
            for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                IItemHandler handler = BlockInventories.itemHandler(level, anchor, side);
                if (handler == null) continue;
                if (firstAcceptingSlotOutsideContainers(handler, def, probe) >= 0) return true;
            }
        }
        return false;
    }

    /**
     * Finds a public slot that accepts the probe without confusing a permissive vessel slot for
     * a fuel slot. Some machines accept any item into their declared container position and
     * enforce bowl/bottle correctness only when serving the result.
     */
    private static int firstAcceptingSlotOutsideContainers(IItemHandler handler,
                                                           @Nullable WorkstationV2Def def,
                                                           ItemStack probe) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (def != null && def.containerSlots().contains(slot)) continue;
            if (handler.insertItem(slot, probe, true).isEmpty()) return slot;
        }
        return -1;
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
        for (int slot : sided.getSlotsForFace(Direction.NORTH)) {
            if (def != null && def.containerSlots().contains(slot)) continue;
            if (sided.canPlaceItemThroughFace(slot, probe, Direction.NORTH)) slots.add(slot);
        }
        return slots.build().toArray();
    }

    private static boolean interactIngredient(ServerLevel level, VillagerEntityMCA villager,
                                              BlockPos anchor, DiscoveredRecipe recipe,
                                              WorkstationV2Def def, boolean stack) {
        if (recipe.inputs().isEmpty()) return false;
        RecipeIngredient ingredient = recipe.inputs().get(0);
        int amount = stack ? Integer.MAX_VALUE : Math.max(1, ingredient.count());
        ItemStack held = takeMatching(villager, ingredient, amount);
        if (held.isEmpty()) return false;
        JsonObject action = new JsonObject();
        action.addProperty("type", "pheno:use_block");
        action.addProperty("item", "ingredient");
        return useBlock(level, villager, anchor, action, held);
    }

    private static boolean runAction(ServerLevel level, VillagerEntityMCA villager,
                                     BlockPos anchor, DiscoveredRecipe recipe, JsonObject action,
                                     String role, WorkstationV2Def def) {
        if (action.has("condition")) {
            BlockCondition condition = BlockConditions.parse(action.get("condition"));
            if (condition == null || !condition.test(level, anchor)) return true;
        }
        ItemStack held = ItemStack.EMPTY;
        if ("tool".equals(role)) {
            for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = villager.getInventory().getItem(slot);
                if (WorkRecipeRegistry.recipeToolMatches(recipe, candidate)) {
                    held = candidate.split(1);
                    break;
                }
            }
            if (held.isEmpty()) return false;
        }
        return useBlock(level, villager, anchor, action, held);
    }

    private static boolean useBlock(ServerLevel level, VillagerEntityMCA villager,
                                    BlockPos pos, JsonObject action, ItemStack supplied) {
        String role = action.has("item") ? action.get("item").getAsString() : "empty";
        var parsed = com.aetherianartificer.townstead.pheno.action.block.BlockActions.parse(action);
        if (parsed == null) {
            if (!supplied.isEmpty()) StationProtocols.giveBack(villager, supplied);
            return false;
        }
        var context = new com.aetherianartificer.townstead.pheno.action.block.BlockActionContext(
                level, pos, villager).withItemRole(role, supplied.copy());
        parsed.run(context);
        ItemStack remainder = context.itemRole(role);
        if (!remainder.isEmpty()) StationProtocols.giveBack(villager, remainder);
        for (ItemStack returned : context.returnedItems()) StationProtocols.giveBack(villager, returned);
        return context.succeeded();
    }

    private static boolean hasOutput(ServerLevel level, BlockPos anchor, ResourceLocation output) {
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (output.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return true;
            }
        }
        return StationDropOutputs.has(level, anchor, Set.of(output));
    }

    private static boolean extractOutput(ServerLevel level, VillagerEntityMCA villager,
                                         BlockPos anchor, ResourceLocation output) {
        LinkedHashSet<IItemHandler> handlers = new LinkedHashSet<>();
        for (Direction side : Direction.values()) {
            IItemHandler handler = BlockInventories.itemHandler(level, anchor, side);
            if (handler != null) handlers.add(handler);
        }
        IItemHandler all = BlockInventories.itemHandler(level, anchor, null);
        if (all != null) handlers.add(all);
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
        return action.has("item") ? action.get("item").getAsString() : "empty";
    }

    private static boolean stackBatch(WorkstationV2Def def) {
        if (def.capacity() == null) return false;
        return def.capacity().toString().contains("\"per_position\":\"stack\"");
    }

    private static boolean connectedStructure(WorkstationV2Def def) {
        return def.structure() != null && def.structure().isJsonObject()
                && "pheno:connected".equals(def.structure().getAsJsonObject().has("type")
                        ? def.structure().getAsJsonObject().get("type").getAsString() : "");
    }

    private static List<BlockPos> connected(ServerLevel level, BlockPos origin, WorkstationV2Def def) {
        return com.aetherianartificer.townstead.pheno.selector.types.ConnectedBlockSelectorType.connected(
                level, origin,
                pos -> def.blocks().contains(BuiltInRegistries.BLOCK.getKey(
                        level.getBlockState(pos).getBlock())), 256);
    }
}
