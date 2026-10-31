package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.profession.career.CareerProgression;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.supply.SupplyLines;
import com.aetherianartificer.townstead.supply.TownsteadSupplyLines;
import com.aetherianartificer.townstead.work.WorkBuildingNav;
import com.aetherianartificer.townstead.work.WorkSiteView;
import com.aetherianartificer.townstead.work.WorkTaskDeclarations;
import com.aetherianartificer.townstead.work.order.StationCatalogs;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.WorkIngredients;
import com.aetherianartificer.townstead.work.site.ProfessionWorksites;
import com.aetherianartificer.townstead.work.station.ProtocolRecipes;
import com.aetherianartificer.townstead.work.station.StationProtocols;
import com.aetherianartificer.townstead.work.station.Stations;
import com.aetherianartificer.townstead.work.station.StationSupplies;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.Workstations;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.tags.TagKey;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The generic producer: works any workstation def that names a station-driven work task, with
 * no code of its own anywhere in the chain. The profession def says who and where (building
 * type prefixes, job block), the workstation def says which block and which recipe family, and
 * this task walks there, stages real inputs through {@link StationProtocols}, waits on the
 * block's own clock, and shelves what comes out. Adding a trade of this shape is data:
 * a profession def declaring the task type and a workstation def naming it.
 *
 * <p>Other producer engines keep their types; {@link WorkTaskTypes} routes each type
 * to exactly one driver, so a station is never worked by two engines at once.</p>
 */
public class StationWorkTask extends ProducerWorkTask {

    private static final long ASSIGNMENT_CACHE_TICKS = 100L;
    private static final long SNAPSHOT_CACHE_TICKS = 40L;
    private static final int MIN_PRODUCE_WAIT_TICKS = 20;

    private @Nullable ProfessionWorksites.Assignment cachedAssignment;
    private long cachedAssignmentUntil = Long.MIN_VALUE;
    private long assignmentDecisionTick = Long.MIN_VALUE;
    private WorkBuildingNav.Snapshot cachedSnapshot = WorkBuildingNav.Snapshot.EMPTY;
    private @Nullable BlockPos cachedSnapshotAnchor;
    private long cachedSnapshotUntil = Long.MIN_VALUE;

    // ── Identity / guards ──

    @Override
    protected boolean isTaskEnabled() {
        return true;
    }

    @Override
    protected boolean isEligibleVillager(ServerLevel level, VillagerEntityMCA villager) {
        ResourceLocation[] types = WorkTaskTypes.stationDrivenTypes();
        if (types.length == 0) return false;
        List<WorkTaskDef> declared = WorkTaskDeclarations.declared(villager, types);
        if (declared == null || declared.isEmpty()) return false;
        // A profession may ship optional-mod task declarations unconditionally. They become
        // executable only when at least one loaded workstation definition satisfies them.
        for (WorkTaskDef task : declared) {
            if (task.anyWorkstation() && !Workstations.all().isEmpty()) return true;
            for (ResourceLocation requested : task.workstations().ids()) {
                if (Workstations.byBlockId(requested) != null) return true;
            }
            for (WorkstationDef station : Workstations.all()) {
                for (ResourceLocation block : station.blocks()) {
                    if (task.allowsBlock(block)) return true;
                }
                for (ResourceLocation tag : station.blockTags()) {
                    if (task.workstations().tags().contains(tag)) return true;
                }
            }
        }
        return false;
    }

    // ── Worksite ──

    @Override
    protected @Nullable WorkSiteView resolveWorksite(ServerLevel level, VillagerEntityMCA villager) {
        ProfessionWorksites.Assignment assignment = assignment(level, villager);
        if (assignment == null) return null;
        Set<Long> bounds = ProfessionWorksites.extentOf(level, assignment);
        if (bounds.isEmpty()) return null;
        return WorkSiteView.building(assignment.reference(), bounds, assignment.site());
    }

    @Override
    protected boolean isVillagerAtWorksite(ServerLevel level, VillagerEntityMCA villager) {
        ProfessionWorksites.Assignment assignment = assignment(level, villager);
        if (assignment == null) return false;
        return WorkBuildingNav.isInsideOrOnStationStand(
                snapshot(level, villager), villager.blockPosition());
    }

    @Override
    protected @Nullable BlockPos resolveWorksiteTarget(
            ServerLevel level, VillagerEntityMCA villager, long gameTime, WorkSiteView site) {
        if (currentWorksiteTarget != null
                && !worksiteTargetFailures.isBlacklisted(currentWorksiteTarget, gameTime)) {
            return currentWorksiteTarget;
        }
        WorkBuildingNav.Snapshot snapshot = snapshot(level, villager);
        BlockPos stand = nearestOf(villager, gameTime,
                snapshot.stationStandPositions().values().stream().flatMap(List::stream).toList());
        if (stand != null) {
            currentWorksiteTargetKind = "stand";
            return stand;
        }
        BlockPos entry = nearestOf(villager, gameTime, snapshot.entryTargets());
        if (entry != null) {
            currentWorksiteTargetKind = "entry";
            return entry;
        }
        currentWorksiteTargetKind = "approach";
        return worksiteReference(villager);
    }

    private @Nullable BlockPos nearestOf(VillagerEntityMCA villager, long gameTime, List<BlockPos> candidates) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : candidates) {
            if (worksiteTargetFailures.isBlacklisted(pos, gameTime)) continue;
            double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }

    @Override
    protected BlockPos worksiteReference(VillagerEntityMCA villager) {
        if (cachedAssignment != null) return cachedAssignment.reference();
        if (stationAnchor != null) return stationAnchor;
        return villager.blockPosition();
    }

    @Override
    protected @Nullable BlockPos refreshStandPosition(
            ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos stationAnchor) {
        if (stationAnchor == null) return null;
        return WorkBuildingNav.nearestReachableStationStand(
                level, snapshot(level, villager), villager, stationAnchor);
    }

    // ── Station acquisition ──

    @Override
    protected @Nullable ProducerStationSelection selectStation(
            ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        WorkBuildingNav.Snapshot snapshot = snapshot(level, villager);
        BlockPos best = null;
        BlockPos bestStand = null;
        int bestRank = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (Stations.StationSlot slot : snapshot.stations()) {
            WorkstationDef def = defFor(level, slot);
            if (def == null || !servesDef(villager, def, slot.blockId())) continue;
            long packed = slot.pos().asLong();
            Long abandonedUntil = abandonedUntilByStation.get(packed);
            if (abandonedUntil != null && gameTime < abandonedUntil) continue;
            if (ProducerStationClaims.isClaimedByOther(level, villager.getUUID(), slot.pos())) continue;
            BlockPos stand = WorkBuildingNav.nearestReachableStationStand(
                    level, snapshot, villager, slot.pos());
            if (stand == null) continue;

            ProducerStationState state = StationProtocols.classify(level, villager, slot.pos(),
                    slot.type(), null, ProducerStationSessions.snapshot(level, slot.pos()));
            int rank = switch (state) {
                case FINISHED_OUTPUT -> 0;
                case OWNED_STAGED -> 1;
                case EMPTY_READY -> 2;
                default -> Integer.MAX_VALUE;
            };
            if (rank == Integer.MAX_VALUE) continue;
            double dist = villager.distanceToSqr(
                    slot.pos().getX() + 0.5, slot.pos().getY() + 0.5, slot.pos().getZ() + 0.5);
            if (rank < bestRank || (rank == bestRank && dist < bestDist)) {
                bestRank = rank;
                bestDist = dist;
                best = slot.pos();
                bestStand = stand;
            }
        }
        if (best == null) {
            setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_WORKSITE, null);
            return null;
        }
        return new ProducerStationSelection(best, bestStand, null);
    }

    @Override
    protected void claimStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) return;
        ProducerStationClaims.tryClaim(level, villager.getUUID(), stationAnchor, gameTime + MAX_DURATION + 20L);
    }

    @Override
    protected void releaseStationClaim(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos pos) {
        if (pos == null) return;
        ProducerStationClaims.release(level, villager.getUUID(), pos);
    }

    // ── Reconcile ──

    @Override
    protected ProducerStationState classifyStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) return ProducerStationState.BLOCKED;
        WorkstationDef def = StationProtocols.defAt(level, stationAnchor);
        if (def == null) return ProducerStationState.BLOCKED;
        ProducerStationSessions.SessionSnapshot session = ProducerStationSessions.snapshot(level, stationAnchor);
        // Coming back to a block mid-cycle (chunk reload, interrupted run): the session names
        // the recipe, so pick the work back up instead of judging our own cook foreign.
        if (activeRecipe == null && session != null && session.isOwner(villager.getUUID())) {
            activeRecipe = recipeById(level, def, session.recipeId());
        }
        return StationProtocols.classify(level, villager, stationAnchor, def.role(),
                recipe(), session);
    }

    @Override
    protected boolean cleanupForeignStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Protocol stations never classify as cleanable-foreign: a mid-cycle block is left to
        // finish and a refusing block is rotated away from.
        return false;
    }

    // ── Recipe / gather / produce / collect ──

    @Override
    protected List<? extends ProducerRecipe> orderCandidates(
            ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) return List.of();
        WorkstationDef def = StationProtocols.defAt(level, stationAnchor);
        if (def == null || !servesDef(villager, def, blockIdAt(level, stationAnchor))) return List.of();
        WorkTaskDef declared = declarationFor(villager, def, blockIdAt(level, stationAnchor));
        Set<Long> bounds = worksiteBounds(level, villager);
        Map<ResourceLocation, Integer> stock = StationCatalogs.stockIn(level, bounds);
        Map<ResourceLocation, Integer> lineStock = new java.util.HashMap<>();
        List<DiscoveredRecipe> out = new ArrayList<>();
        for (DiscoveredRecipe recipe : recipesFor(level, def)) {
            if (declared != null && !declared.allowsRecipe(
                    recipe.id(), recipe.output(), recipe.inputs())) continue;
            if (!inputsAvailable(level, villager, recipe, stock, bounds, lineStock)) continue;
            out.add(recipe);
        }
        return out;
    }

    @Override
    protected @Nullable ProducerRecipe pickRecipe(ServerLevel level, VillagerEntityMCA villager,
                                                  long gameTime,
                                                  Predicate<ResourceLocation> outputAllowed) {
        if (stationAnchor == null) return null;
        WorkstationDef def = StationProtocols.defAt(level, stationAnchor);
        if (def == null || !servesDef(villager, def, blockIdAt(level, stationAnchor))) return null;
        // Potion outputs are component-bearing products which share only three physical item ids.
        // A brewing station therefore follows an exact Order Sheet line rather than selecting a
        // random registered mix autonomously.
        if (com.aetherianartificer.townstead.work.station.BrewingStandStationAdapter.NAME
                .equals(def.adapter())) {
            return null;
        }
        WorkTaskDef declared = declarationFor(villager, def, blockIdAt(level, stationAnchor));

        Set<Long> bounds = worksiteBounds(level, villager);
        Map<ResourceLocation, Integer> stock = StationCatalogs.stockIn(level, bounds);
        Map<ResourceLocation, Integer> lineStock = new java.util.HashMap<>();
        for (DiscoveredRecipe recipe : recipesFor(level, def)) {
            if (!outputAllowed.test(recipe.output())) continue;
            Long cooldown = recipeCooldownUntil.get(recipe.output());
            if (cooldown != null && gameTime < cooldown) continue;
            if (declared != null && !declared.allowsRecipe(
                    recipe.id(), recipe.output(), recipe.inputs())) continue;
            if (!inputsAvailable(level, villager, recipe, stock, bounds, lineStock)) continue;
            return recipe;
        }
        return null;
    }

    /**
     * Whether the worksite can feed this recipe right now: every ingredient group satisfied from
     * the villager's pockets plus the shelves. Supply-line groups (fuel) are judged by probing
     * the stocked ids against the line's own predicate, which is exactly the test staging uses.
     */
    private boolean inputsAvailable(ServerLevel level, VillagerEntityMCA villager,
                                    DiscoveredRecipe recipe, Map<ResourceLocation, Integer> stock,
                                    Set<Long> bounds,
                                    Map<ResourceLocation, Integer> lineStock) {
        for (RecipeIngredient input : requiredInputs(level, recipe)) {
            int needed = Math.max(1, input.count());
            if (SupplyLines.isLineId(input.primaryId())) {
                if (!lineAvailable(level, villager, input.primaryId(), needed, bounds,
                        lineStock)) return false;
                continue;
            }
            int have = 0;
            for (ResourceLocation id : input.itemIds()) {
                have += stock.getOrDefault(id, 0) + inventoryCount(villager, id);
                if (have >= needed) break;
            }
            if (have < needed) return false;
        }
        ResourceLocation vessel = recipe.containerItemId();
        if (vessel != null && BuiltInRegistries.ITEM.containsKey(vessel)) {
            int needed = Math.max(1, recipe.containerCount());
            if (stock.getOrDefault(vessel, 0) + inventoryCount(villager, vessel) < needed) return false;
        }
        return true;
    }

    private boolean lineAvailable(ServerLevel level, VillagerEntityMCA villager,
                                  ResourceLocation lineId, int needed, Set<Long> bounds,
                                  Map<ResourceLocation, Integer> lineStock) {
        int available = lineStock.computeIfAbsent(lineId, ignored -> {
            Predicate<ItemStack> matcher = SupplyLines.matcher(level, lineId);
            int count = inventoryCountMatching(villager, matcher);
            return count + StationCatalogs.countMatching(level, bounds, matcher);
        });
        return available >= needed;
    }

    @Override
    protected @Nullable WorkIngredients.PhysicalPull nextPhysicalPull(
            ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        DiscoveredRecipe recipe = recipe();
        if (recipe == null || stationAnchor == null) return null;
        takeEscrowWorkpiece(level, villager);
        Set<Long> bounds = worksiteBounds(level, villager);

        WorkIngredients.PhysicalPull tool = WorkIngredients.nextPhysicalRecipeToolPull(
                level, villager, recipe, bounds);
        if (tool != null) return tool;
        WorkstationDef def = StationProtocols.defAt(level, stationAnchor);
        if (def != null && !def.harvestTools().isEmpty()
                && !StationProtocols.hasHarvestTool(villager, def)) {
            Predicate<ItemStack> matcher = stack -> !stack.isEmpty()
                    && def.harvestTools().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            tool = WorkIngredients.nextPhysicalPull(level, villager, matcher, 1, true,
                    "harvest tool", bounds);
            if (tool != null) return tool;
        }

        boolean plainOnly = def != null
                && def.role() == com.aetherianartificer.townstead.work.recipe.StationType.CRAFT_SURFACE;
        for (RecipeIngredient input : requiredInputs(level, recipe)) {
            int needed = Math.max(1, input.count());
            WorkIngredients.PhysicalPull pull;
            if (SupplyLines.isLineId(input.primaryId())) {
                pull = WorkIngredients.nextPhysicalPull(level, villager,
                        SupplyLines.matcher(level, input.primaryId()), needed, false,
                        input.primaryId().getPath().replace('_', ' '), bounds);
            } else {
                pull = WorkIngredients.nextPhysicalIngredientPull(level, villager, input, needed,
                        plainOnly, itemName(input.primaryId()), bounds);
            }
            if (pull != null) return pull;
        }
        if (def != null) {
            WorkstationDef.Produce produce = StationProtocols.produceFor(def, recipe);
            if (produce != null && produce.extrasTag() != null && produce.extrasMax() > 0) {
                TagKey<Item> tag = TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                        produce.extrasTag());
                WorkIngredients.PhysicalPull extra = WorkIngredients.nextPhysicalPull(
                        level, villager, stack -> !stack.isEmpty() && stack.is(tag),
                        produce.extrasMax(), false, "optional extras", bounds);
                if (extra != null) return extra;
            }
        }
        ResourceLocation vessel = recipe.containerItemId();
        if (vessel != null && BuiltInRegistries.ITEM.containsKey(vessel)) {
            Predicate<ItemStack> matcher = stack -> !stack.isEmpty()
                    && vessel.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            return WorkIngredients.nextPhysicalPull(level, villager, matcher,
                    Math.max(1, recipe.containerCount()), false, itemName(vessel), bounds);
        }
        return null;
    }

    @Override
    protected Set<Long> transferWorksiteBounds(ServerLevel level, VillagerEntityMCA villager) {
        return worksiteBounds(level, villager);
    }

    @Override
    protected boolean isCycleOutput(ServerLevel level, ItemStack stack) {
        DiscoveredRecipe recipe = recipe();
        return recipe != null && stack != null && !stack.isEmpty()
                && recipe.output().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                && com.aetherianartificer.townstead.work.order.OrderProducts.matches(
                com.aetherianartificer.townstead.work.order.OrderProducts.key(recipe), stack);
    }

    @Override
    protected GatherResult gatherInputs(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        DiscoveredRecipe recipe = recipe();
        if (recipe == null || stationAnchor == null) return GatherResult.fail(null);
        Set<Long> bounds = worksiteBounds(level, villager);
        WorkstationDef def = StationProtocols.defAt(level, stationAnchor);
        // Crafts consume only plain stacks (the adapter refuses the rest), so pulling an
        // enchanted piece would strand it in the crafter's pockets. Don't take it off the
        // shelf in the first place.
        boolean plainOnly = def != null
                && def.role() == com.aetherianartificer.townstead.work.recipe.StationType.CRAFT_SURFACE;

        // A commission's workpiece comes out of the line's own escrow, not off a shelf: the
        // player handed the shop this exact stack. Collected once — from then on it travels
        // the ordinary flow, and everything it becomes lands back in storage.
        takeEscrowWorkpiece(level, villager);

        for (RecipeIngredient input : requiredInputs(level, recipe)) {
            int needed = Math.max(1, input.count());
            Predicate<ItemStack> matcher;
            String shortName;
            if (SupplyLines.isLineId(input.primaryId())) {
                matcher = SupplyLines.matcher(level, input.primaryId());
                shortName = TownsteadSupplyLines.FURNACE_FUEL.equals(input.primaryId())
                        ? "fuel" : input.primaryId().getPath().replace('_', ' ');
            } else {
                List<ResourceLocation> ids = input.itemIds();
                Predicate<ItemStack> byId = stack -> !stack.isEmpty()
                        && ids.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                matcher = plainOnly
                        ? byId.and(com.aetherianartificer.townstead.work.station.CraftSurfaceAdapter::isPlain)
                        : byId;
                shortName = itemName(input.primaryId());
            }
            if (!ensureOnHand(level, villager, matcher, needed, bounds)) {
                return GatherResult.fail(shortName);
            }
            stagedInputs.merge(input.primaryId(), needed, Integer::sum);
        }

        ResourceLocation vessel = recipe.containerItemId();
        if (vessel != null && BuiltInRegistries.ITEM.containsKey(vessel)) {
            int needed = Math.max(1, recipe.containerCount());
            Predicate<ItemStack> matcher = stack -> !stack.isEmpty()
                    && vessel.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (!ensureOnHand(level, villager, matcher, needed, bounds)) {
                return GatherResult.fail(itemName(vessel));
            }
        }
        return GatherResult.ok();
    }

    private List<RecipeIngredient> requiredInputs(ServerLevel level, DiscoveredRecipe recipe) {
        if (stationAnchor == null) return RecipeIngredient.merge(recipe.inputs());
        ResourceLocation block = blockIdAt(level, stationAnchor);
        var def = Workstations.v2ByBlockId(block);
        WorkstationDef stationDef = StationProtocols.defAt(level, stationAnchor);
        List<RecipeIngredient> additional = stationDef == null ? List.of()
                : java.util.Optional.ofNullable(
                        com.aetherianartificer.townstead.work.station.StationAdapters.forDef(stationDef))
                .map(adapter -> adapter.additionalInputs(level, stationAnchor, stationDef, recipe))
                .orElse(List.of());
        if (def == null || def.catalystSlots().isEmpty()) {
            List<RecipeIngredient> all = new ArrayList<>(recipe.inputs());
            all.addAll(additional);
            return RecipeIngredient.merge(all);
        }
        List<RecipeIngredient> required = new ArrayList<>();
        for (int i = 0; i < recipe.inputs().size(); i++) {
            if (com.aetherianartificer.townstead.work.station.DataDrivenStationAdapter
                    .hasStagedCatalyst(level, stationAnchor, def, recipe, i)) continue;
            required.add(recipe.inputs().get(i));
        }
        required.addAll(additional);
        return RecipeIngredient.merge(required);
    }

    private boolean ensureOnHand(ServerLevel level, VillagerEntityMCA villager,
                                 Predicate<ItemStack> matcher, int needed, Set<Long> bounds) {
        return inventoryCountMatching(villager, matcher) >= needed;
    }

    private void takeEscrowWorkpiece(ServerLevel level, VillagerEntityMCA villager) {
        com.aetherianartificer.townstead.work.order.Order line = claimedOrder();
        if (line == null || line.workpiece() == null) return;
        net.minecraft.nbt.CompoundTag escrow = line.takeWorkpiece();
        if (escrow == null) return;
        //? if >=1.21 {
        ItemStack held = ItemStack.parse(level.registryAccess(), escrow).orElse(ItemStack.EMPTY);
        //?} else {
        /*ItemStack held = ItemStack.of(escrow);
        *///?}
        if (!held.isEmpty()) StationProtocols.giveBack(villager, held);
        com.aetherianartificer.townstead.work.site.WorksiteRegister
                .get(level.getServer()).setDirty();
    }

    @Override
    protected void rollbackGather(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Nothing left the villager's hands yet — insert happens in beginProduce, and what was
        // pulled stays worksite property in worksite pockets for the next attempt.
        stagedInputs.clear();
    }

    @Override
    protected boolean beginProduce(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        DiscoveredRecipe recipe = recipe();
        if (recipe == null || stationAnchor == null) return false;
        Set<Long> bounds = worksiteBounds(level, villager);
        if (!StationProtocols.insert(level, villager, stationAnchor, recipe, bounds)) return false;
        produceDoneTick = gameTime + Math.max(MIN_PRODUCE_WAIT_TICKS, recipe.cookTimeTicks());
        return true;
    }

    @Override
    protected boolean isProduceDone(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) return true;
        return StationProtocols.isReady(level, villager, stationAnchor, recipe());
    }

    @Override
    protected CollectResult collectFromStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) return CollectResult.none();
        Set<Long> bounds = worksiteBounds(level, villager);
        DiscoveredRecipe recipe = recipe();
        if (recipe == null) return CollectResult.none();
        Set<ResourceLocation> outputs = Set.of(recipe.output());
        boolean carried = ProducerOutputHelper.collectSurfaceDrops(
                level, villager, stationAnchor, bounds, outputs);
        boolean harvested = StationProtocols.collect(level, villager, stationAnchor, recipe, bounds);
        carried |= ProducerOutputHelper.collectSurfaceDrops(
                level, villager, stationAnchor, bounds, outputs);
        carried |= inventoryCount(villager, recipe.output()) >= Math.max(1, recipe.outputCount());
        if (carried) return CollectResult.ofCollected();
        return harvested ? CollectResult.waiting(false) : CollectResult.none();
    }

    @Override
    protected void storeOutputs(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Collection leaves real stacks in the worker's inventory. ProducerWorkTask walks them
        // to storage in DELIVER; this hook intentionally performs no remote inventory mutation.
    }


    // ── Sessions / lifecycle hooks ──

    @Override
    protected void onSessionRefresh(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        DiscoveredRecipe recipe = recipe();
        if (stationAnchor == null || recipe == null) return;
        ProducerStationSessions.beginOrRefresh(level, villager.getUUID(), stationAnchor,
                recipe.id(), recipe.output(), recipe.outputCount(),
                stagedInputs, gameTime + STATION_SESSION_LEASE_TICKS);
    }

    @Override
    protected void onSessionRelease(ServerLevel level, VillagerEntityMCA villager,
                                    @Nullable BlockPos pos, long gameTime) {
        if (pos == null) return;
        ProducerStationSessions.release(level, villager.getUUID(), pos);
    }

    @Override
    protected boolean hasResumableStationSession(
            ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos pos, long gameTime) {
        if (pos == null) return false;
        ProducerStationSessions.SessionSnapshot session = ProducerStationSessions.snapshot(level, pos);
        return session != null && session.isOwner(villager.getUUID())
                && session.mayResume(currentActivity(villager) == net.minecraft.world.entity.schedule.Activity.WORK);
    }

    @Override
    protected void onStop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        clearCaches();
    }

    @Override
    protected void onClearAll(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        clearCaches();
    }

    private void clearCaches() {
        cachedAssignment = null;
        cachedAssignmentUntil = Long.MIN_VALUE;
        assignmentDecisionTick = Long.MIN_VALUE;
        cachedSnapshot = WorkBuildingNav.Snapshot.EMPTY;
        cachedSnapshotAnchor = null;
        cachedSnapshotUntil = Long.MIN_VALUE;
    }

    // ── Resolution helpers ──

    private @Nullable DiscoveredRecipe recipe() {
        return (DiscoveredRecipe) activeRecipe;
    }

    private @Nullable ProfessionWorksites.Assignment assignment(ServerLevel level, VillagerEntityMCA villager) {
        long gameTime = level.getGameTime();
        boolean deciding = state == ProducerState.PATH_TO_WORKSITE && stationAnchor == null;
        if (cachedAssignment != null && gameTime <= cachedAssignmentUntil
                && (!deciding || assignmentDecisionTick == gameTime)) return cachedAssignment;
        ProfessionWorksites.Assignment fresh = ProfessionWorksites.resolveForWork(level, villager);
        cachedAssignment = fresh;
        cachedAssignmentUntil = gameTime + ASSIGNMENT_CACHE_TICKS;
        assignmentDecisionTick = gameTime;
        return fresh;
    }

    private Set<Long> worksiteBounds(ServerLevel level, VillagerEntityMCA villager) {
        ProfessionWorksites.Assignment assignment = assignment(level, villager);
        if (assignment == null) return Set.of();
        return ProfessionWorksites.extentOf(level, assignment);
    }

    private WorkBuildingNav.Snapshot snapshot(ServerLevel level, VillagerEntityMCA villager) {
        ProfessionWorksites.Assignment assignment = assignment(level, villager);
        if (assignment == null) return WorkBuildingNav.Snapshot.EMPTY;
        long gameTime = level.getGameTime();
        if (cachedSnapshotAnchor != null && assignment.reference().equals(cachedSnapshotAnchor)
                && gameTime <= cachedSnapshotUntil
                && !cachedSnapshot.walkableInterior().isEmpty()) {
            return cachedSnapshot;
        }
        WorkBuildingNav.Snapshot snapshot = WorkBuildingNav.snapshot(
                level, ProfessionWorksites.extentOf(level, assignment), assignment.reference());
        cachedSnapshot = snapshot;
        cachedSnapshotAnchor = assignment.reference();
        cachedSnapshotUntil = gameTime + SNAPSHOT_CACHE_TICKS;
        return snapshot;
    }

    /** The def governing a snapshot slot, resolved through the block actually standing there. */
    private @Nullable WorkstationDef defFor(ServerLevel level, Stations.StationSlot slot) {
        WorkstationDef def = Workstations.byState(level.getBlockState(slot.pos()));
        return def != null ? def : Workstations.byBlockId(slot.blockId());
    }

    /**
     * Whether this villager's trade drives this def at this block: the def names a
     * station-driven task type, the villager's profession declares it, and the declaration's
     * workstation filter admits the block actually standing there. One routing question,
     * asked the same way at selection and at work.
     */
    private boolean servesDef(VillagerEntityMCA villager, WorkstationDef def,
                              @Nullable ResourceLocation blockId) {
        return declarationFor(villager, def, blockId) != null;
    }

    private @Nullable WorkTaskDef declarationFor(VillagerEntityMCA villager, WorkstationDef def,
                                                 @Nullable ResourceLocation blockId) {
        // V2 deliberately carries no work-task field. The profession declaration owns who uses
        // a station, and its workstation filter is the join. This keeps adding a new profession
        // from requiring edits to the station profile.
        if (Workstations.v2ByBlockId(blockId) != null) {
            for (WorkTaskDef declared : WorkTaskDeclarations.all(villager)) {
                if (!WorkTaskTypes.isStationDriven(declared.type())) continue;
                if (declared.anyWorkstation() || declared.allowsBlock(blockId)) return declared;
            }
            return null;
        }
        ResourceLocation task = def.workTask();
        if (task == null || !WorkTaskTypes.isStationDriven(task)) return null;
        WorkTaskDef declared = WorkTaskDeclarations.first(villager, task);
        return declared != null && (declared.anyWorkstation() || declared.allowsBlock(blockId))
                ? declared : null;
    }

    private @Nullable ResourceLocation blockIdAt(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
    }

    private List<DiscoveredRecipe> recipesFor(ServerLevel level, WorkstationDef def) {
        return ProtocolRecipes.discover(level, def);
    }

    private @Nullable DiscoveredRecipe recipeById(ServerLevel level, WorkstationDef def,
                                                  @Nullable ResourceLocation recipeId) {
        if (recipeId == null) return null;
        for (DiscoveredRecipe recipe : recipesFor(level, def)) {
            if (recipe.id().equals(recipeId)) return recipe;
        }
        return null;
    }

    private static int inventoryCount(VillagerEntityMCA villager, ResourceLocation id) {
        int count = 0;
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int inventoryCountMatching(VillagerEntityMCA villager, Predicate<ItemStack> matcher) {
        int count = 0;
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (matcher.test(stack)) count += stack.getCount();
        }
        return count;
    }

    private static String itemName(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString()
                : id.getPath().replace('_', ' ');
    }
}
