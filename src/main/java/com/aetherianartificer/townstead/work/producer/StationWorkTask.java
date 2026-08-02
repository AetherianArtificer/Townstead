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
 * <p>Bespoke engines (cook, butcher) keep their types; {@link WorkTaskTypes} routes each type
 * to exactly one driver, so a station is never worked by two engines at once.</p>
 */
public class StationWorkTask extends ProducerWorkTask {

    private static final long ASSIGNMENT_CACHE_TICKS = 100L;
    private static final long SNAPSHOT_CACHE_TICKS = 40L;
    private static final int MIN_PRODUCE_WAIT_TICKS = 20;

    private @Nullable ProfessionWorksites.Assignment cachedAssignment;
    private long cachedAssignmentUntil = Long.MIN_VALUE;
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
        return WorkTaskDeclarations.permitsTask(villager, types);
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
        BlockPos pos = villager.blockPosition();
        if (assignment.building().containsPos(pos)
                || assignment.building().containsPos(pos.below())
                || assignment.building().containsPos(pos.above())) {
            return true;
        }
        return WorkBuildingNav.isInsideOrOnStationStand(snapshot(level, villager), pos);
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
        BlockPos stand = WorkBuildingNav.nearestStationStand(snapshot(level, villager), villager, stationAnchor);
        return stand != null ? stand : Stations.findStandingPosition(level, villager, stationAnchor);
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
            BlockPos stand = WorkBuildingNav.nearestStationStand(snapshot, villager, slot.pos());
            if (stand == null) stand = Stations.findStandingPosition(level, villager, slot.pos());
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
        WorkTaskDef declared = WorkTaskDeclarations.first(villager, def.workTask());
        List<DiscoveredRecipe> out = new ArrayList<>();
        for (DiscoveredRecipe recipe : recipesFor(level, def)) {
            if (declared != null && !declared.allowsRecipe(recipe.id(), recipe.output())) continue;
            out.add(recipe);
        }
        return out;
    }

    @Override
    protected @Nullable ProducerRecipe pickRecipe(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) return null;
        WorkstationDef def = StationProtocols.defAt(level, stationAnchor);
        if (def == null || !servesDef(villager, def, blockIdAt(level, stationAnchor))) return null;
        WorkTaskDef declared = WorkTaskDeclarations.first(villager, def.workTask());

        Set<Long> bounds = worksiteBounds(level, villager);
        Map<ResourceLocation, Integer> stock = StationCatalogs.stockIn(level, bounds);
        for (DiscoveredRecipe recipe : recipesFor(level, def)) {
            Long cooldown = recipeCooldownUntil.get(recipe.output());
            if (cooldown != null && gameTime < cooldown) continue;
            if (declared != null && !declared.allowsRecipe(recipe.id(), recipe.output())) continue;
            if (!inputsAvailable(level, villager, recipe, stock)) continue;
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
                                    DiscoveredRecipe recipe, Map<ResourceLocation, Integer> stock) {
        for (RecipeIngredient input : RecipeIngredient.merge(recipe.inputs())) {
            int needed = Math.max(1, input.count());
            if (SupplyLines.isLineId(input.primaryId())) {
                if (!lineAvailable(level, villager, input.primaryId(), stock)) return false;
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
                                  ResourceLocation lineId, Map<ResourceLocation, Integer> stock) {
        Predicate<ItemStack> matcher = SupplyLines.matcher(level, lineId);
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (matcher.test(inv.getItem(i))) return true;
        }
        for (ResourceLocation id : stock.keySet()) {
            if (!BuiltInRegistries.ITEM.containsKey(id)) continue;
            if (matcher.test(new ItemStack(BuiltInRegistries.ITEM.get(id)))) return true;
        }
        return false;
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
        com.aetherianartificer.townstead.work.order.Order line = claimedOrder();
        if (line != null && line.workpiece() != null) {
            net.minecraft.nbt.CompoundTag escrow = line.takeWorkpiece();
            if (escrow != null) {
                //? if >=1.21 {
                ItemStack held = ItemStack.parse(level.registryAccess(), escrow)
                        .orElse(ItemStack.EMPTY);
                //?} else {
                /*ItemStack held = ItemStack.of(escrow);
                *///?}
                if (!held.isEmpty()) StationProtocols.giveBack(villager, held);
                com.aetherianartificer.townstead.work.site.WorksiteRegister
                        .get(level.getServer()).setDirty();
            }
        }

        for (RecipeIngredient input : RecipeIngredient.merge(recipe.inputs())) {
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

    private boolean ensureOnHand(ServerLevel level, VillagerEntityMCA villager,
                                 Predicate<ItemStack> matcher, int needed, Set<Long> bounds) {
        int have = inventoryCountMatching(villager, matcher);
        if (have >= needed) return true;
        StationSupplies.pullMatching(level, villager, matcher, needed - have, stationAnchor, bounds);
        return inventoryCountMatching(villager, matcher) >= needed;
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
        boolean collected = StationProtocols.collect(level, villager, stationAnchor, recipe(), bounds);
        return collected ? CollectResult.ofCollected() : CollectResult.none();
    }

    @Override
    protected void storeOutputs(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        DiscoveredRecipe recipe = recipe();
        if (recipe == null) return;
        Set<Long> bounds = worksiteBounds(level, villager);
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!recipe.output().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
            int stored = StationSupplies.storeOutput(level, villager, stack, stationAnchor, bounds);
            if (stored > 0) stack.shrink(stored);
        }
    }

    @Override
    protected void awardProductionXp(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        DiscoveredRecipe recipe = recipe();
        if (recipe == null) return;
        ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        if (profession == null) return;
        int xp = Math.max(1, recipe.tier());
        CareerProgression.completeWork(villager, ProfessionDefs.canonicalId(profession), xp, gameTime,
                "townstead:produced", recipe.output(), "item", recipe.tier());
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
        if (cachedAssignment != null && gameTime <= cachedAssignmentUntil) return cachedAssignment;
        ProfessionWorksites.Assignment fresh = ProfessionWorksites.resolve(level, villager);
        cachedAssignment = fresh;
        cachedAssignmentUntil = gameTime + ASSIGNMENT_CACHE_TICKS;
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
        ResourceLocation task = def.workTask();
        if (task == null || !WorkTaskTypes.isStationDriven(task)) return false;
        WorkTaskDef declared = WorkTaskDeclarations.first(villager, task);
        if (declared == null) return false;
        return declared.anyWorkstation() || declared.allowsBlock(blockId);
    }

    private @Nullable ResourceLocation blockIdAt(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
    }

    private List<DiscoveredRecipe> recipesFor(ServerLevel level, WorkstationDef def) {
        List<DiscoveredRecipe> recipes = new ArrayList<>(ProtocolRecipes.discoverFor(def));
        recipes.addAll(ProtocolRecipes.discoverByType(level, def));
        return recipes;
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
