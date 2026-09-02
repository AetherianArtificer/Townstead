package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.work.producer.ProducerTaskDeclarations;

import com.aetherianartificer.townstead.work.producer.ProducerOutputHelper;

import com.aetherianartificer.townstead.work.producer.ProducerRole;

import com.aetherianartificer.townstead.work.station.Stations;

import com.aetherianartificer.townstead.work.recipe.RecipeSelector;
import com.aetherianartificer.townstead.work.producer.ProducerWorkSupport;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.work.WorkBuildingNav;
import com.aetherianartificer.townstead.work.WorkNavigationMetrics;
import com.aetherianartificer.townstead.work.WorkSiteView;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.Worksites;
import com.aetherianartificer.townstead.work.producer.ProducerBlockedReason;
import com.aetherianartificer.townstead.work.producer.ProducerRecipe;
import com.aetherianartificer.townstead.work.producer.ProducerStationClaims;
import com.aetherianartificer.townstead.work.producer.ProducerStationSessions;
import com.aetherianartificer.townstead.work.producer.ProducerStationState;
import com.aetherianartificer.townstead.work.producer.ProducerStationIndex;
import com.aetherianartificer.townstead.work.recipe.WorkIngredients;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.station.Stations.StationSlot;
import com.aetherianartificer.townstead.hunger.ConsumableTargetClaims;
import com.aetherianartificer.townstead.storage.StorageSearchContext;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The producer engine for trades whose recipes are DISCOVERED — read out of installed mods'
 * recipe types and families — rather than declared inline. One state machine serves any such
 * trade: which task types it answers, which career it credits, what it says when short of
 * ingredients, are all construction-time spec, and everything a specific mod contributes
 * (stations, recipes, block lists) arrives through workstation and profession JSON.
 *
 * <p>This is the generalization of what used to be {@code CookWorkTask}: nothing here knows
 * what a kitchen is. A pack that ships a station def and a profession declaring the task type
 * gets this whole engine — selection buckets, ingredient staging, fire/pot/board protocols,
 * shortage requests, appraisal XP — with no Java.</p>
 */
public class DiscoveredStationWorkTask extends ProducerWorkTask {
    private static final int WORK_ACTION_INTERVAL_TICKS = 4;

    /** What this instance serves and says; the rest of the class is trade-neutral. */
    public record Spec(
            String label,
            net.minecraft.resources.ResourceLocation taskType,
            @Nullable net.minecraft.resources.ResourceLocation secondaryTaskType,
            ProducerRole role,
            java.util.function.BooleanSupplier enabled) {}

    private final Spec spec;
    private final net.minecraft.resources.ResourceLocation[] taskTypes;

    public DiscoveredStationWorkTask(Spec spec) {
        super();
        this.spec = spec;
        this.taskTypes = spec.secondaryTaskType() == null
                ? new net.minecraft.resources.ResourceLocation[]{spec.taskType()}
                : new net.minecraft.resources.ResourceLocation[]{spec.taskType(), spec.secondaryTaskType()};
    }

    private static final int REQUEST_RANGE = 24;
    private static final int REQUEST_INITIAL_DELAY_TICKS = 1200;
    private static final long ROOM_BOUNDS_CACHE_TICKS = 80L;
    private static final long SERVICE_SITE_CACHE_TICKS = 20L;

    // Subclass-only state
    private @Nullable StationType stationType;
    private ItemStack heldCuttingInput = ItemStack.EMPTY;
    private boolean cuttingBoardItemPlaced;
    /** A tool interaction processes one staged input; repeating it can place the tool on an empty board. */
    private boolean toolWorkActionPerformed;
    /** Strokes still owed before the tool interaction fires; a cut is a motion, not a teleport. */
    private int toolWorkSwingsRemaining;
    /** Tool interactions that reported nothing observable; bounded so a wrong board never stalls a shift. */
    private int toolWorkAttempts;
    private static final int TOOL_WORK_SWINGS = 3;
    private static final int TOOL_SWING_INTERVAL_TICKS = 6;
    private static final int MAX_TOOL_WORK_ATTEMPTS = 3;
    private ItemStack previousBoardMainHand = ItemStack.EMPTY;
    private ItemStack previousBoardOffHand = ItemStack.EMPTY;
    private boolean boardHandsVisible;
    private @Nullable BlockPos stickyBoardStationAnchor;
    private @Nullable ResourceLocation stickyBoardRecipeId;
    private @Nullable ResourceLocation stickyBoardInputId;
    private @Nullable com.aetherianartificer.townstead.work.OutputAppraisal.Appraisal lastAppraisal;
    /**
     * Surface stations can eject their result before the recipe clock expires. The safety sweep
     * stores those entities immediately; this latch preserves the completion signal after the
     * physical entity is gone.
     */
    private boolean sweptProducedOutput;
    /** Output already carried after this cycle committed its inputs. A direct interaction return
     * must exceed this baseline before it can prove that the current operation completed. */
    private int carriedOutputBaseline;
    /** The station consumed/ejected its inputs without exposing the selected recipe's output. */
    private boolean interruptedProduction;
    /** A finished station recovered after reload may need to fetch its collection tool first. */
    private @Nullable WorkIngredients.PhysicalPull harvestToolPull;
    /** Entity-powered stations share this lifecycle; their mod contributes only the JSON tag. */
    private final com.aetherianartificer.townstead.work.station.StationDriverCoordinator drivers =
            new com.aetherianartificer.townstead.work.station.StationDriverCoordinator();

    // Kitchen bounds cache
    private Set<Long> cachedWorksiteWorkArea = Set.of();
    private @Nullable BlockPos cachedWorksiteWorkAnchor = null;
    private long cachedWorksiteWorkUntil = 0L;
    private WorkBuildingNav.Snapshot cachedWorksiteSnapshotNav = WorkBuildingNav.Snapshot.EMPTY;
    private @Nullable com.aetherianartificer.townstead.profession.ProfessionSites.Site cachedServiceSite;
    private long cachedServiceSiteUntil = Long.MIN_VALUE;
    /** Wider-scope station searches, kept apart from the worksite snapshot and on the same TTL. */
    private final java.util.EnumMap<com.aetherianartificer.townstead.profession.def.WorkTaskDef.Scope, ScopedSnapshot>
            scopedSnapshots = new java.util.EnumMap<>(com.aetherianartificer.townstead.profession.def.WorkTaskDef.Scope.class);

    private record ScopedSnapshot(@Nullable BlockPos anchor, WorkBuildingNav.Snapshot snapshot, long expiresAt) {}


    // ── Identity / guards ──

    @Override
    protected boolean isTaskEnabled() {
        return spec.enabled().getAsBoolean();
    }

    @Override
    protected boolean isEligibleVillager(ServerLevel level, VillagerEntityMCA villager) {
        if (!com.aetherianartificer.townstead.work.WorkTaskDeclarations.permitsTask(
                villager, taskTypes)) return false;
        if (com.aetherianartificer.townstead.work.WorkActivities.hasHigherPriorityWork(
                level, villager, spec.taskType())) return false;
        return resolveAssignedSite(level, villager) != null;
    }

    // ── Worksite ──

    @Override
    protected @Nullable WorkSiteView resolveWorksite(ServerLevel level, VillagerEntityMCA villager) {
        com.aetherianartificer.townstead.profession.ProfessionSites.Site site = resolveAssignedSite(level, villager);
        BlockPos reference = referenceFor(villager, site);
        Set<Long> bounds = activeWorksiteBounds(villager, reference, site);
        if (bounds.isEmpty()) return null;
        Worksite record = site == null ? null
                : site.building() != null
                        ? Worksites.of(level, site.building())
                        : Worksites.of(level, site.post());
        return WorkSiteView.building(reference, bounds, record);
    }

    @Override
    protected boolean isVillagerAtWorksite(ServerLevel level, VillagerEntityMCA villager) {
        return isVillagerInActiveWorksite(villager);
    }

    @Override
    protected Set<Long> atEaseCells(ServerLevel level, VillagerEntityMCA villager) {
        return activeWorksiteBounds(level, villager);
    }

    @Override
    protected @Nullable BlockPos resolveWorksiteTarget(ServerLevel level, VillagerEntityMCA villager, long gameTime, WorkSiteView site) {
        WorkBuildingNav.Snapshot worksiteSnapshotLocal = activeWorksiteSnapshot(level, villager);
        return currentOrNewWorksiteTarget(level, villager, gameTime, worksiteSnapshotLocal);
    }

    @Override
    protected BlockPos worksiteReference(VillagerEntityMCA villager) {
        return activeWorksiteReference(villager);
    }

    @Override
    protected @Nullable BlockPos refreshStandPosition(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos stationAnchor) {
        if (stationAnchor == null) return null;
        return WorkBuildingNav.nearestReachableStationStand(
                level, activeWorksiteSnapshot(level, villager), villager, stationAnchor);
    }

    // ── Station acquisition ──

    @Override
    protected @Nullable ProducerStationSelection selectStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
        WorkBuildingNav.Snapshot worksiteSnapshotLocal = activeWorksiteSnapshot(level, villager);
        boolean sawAnyStation = !worksiteSnapshotLocal.stations().isEmpty();
        ProducerStationIndex.Selection best = null;
        // Declared tasks gate stations per weight bucket: a bucket's stations are exhausted
        // before the next (lower-weight) bucket is considered. A villager specced into a
        // path tries the path's stations first within each bucket, then the rest.
        Set<ResourceLocation> pathWorksites =
                com.aetherianartificer.townstead.profession.career.PathAffinity
                        .preferredWorksites(villager);
        // Computed once per pass: it walks the room for an empty plate, and the ranking below
        // asks about every candidate recipe at every station.
        Set<ResourceLocation> menuDemand = com.aetherianartificer.townstead.food.ServingDemand
                .standing(level, villager, worksiteBoundsLocal);
        java.util.function.ToIntFunction<DiscoveredRecipe> orderPriority = recipe ->
                com.aetherianartificer.townstead.work.order.WorksiteOrders.recipePriority(
                        level, villager, activeWorksite(), recipe, menuDemand);
        // Counted so the failure below can name its cause: a station this career does not work
        // is a different problem from a station with nothing to cook at it, and they are
        // indistinguishable from a villager standing still.
        int worksHere = 0;
        java.util.Set<String> worked = new java.util.LinkedHashSet<>();
        for (List<com.aetherianartificer.townstead.profession.def.WorkTaskDef> bucket
                : ProducerTaskDeclarations.buckets(villager, spec.taskType(), spec.secondaryTaskType())) {
            java.util.function.Predicate<StationSlot> filter =
                    ProducerTaskDeclarations.stationFilter(bucket);
            // Where the bucket may look. Buckets run highest weight first and the loop breaks on
            // the first hit, so a wide search only ever runs once the villager has found nothing
            // to do at its own work site — the expensive scan is the last resort, not the first.
            WorkBuildingNav.Snapshot snapshot =
                    scopedSnapshot(level, villager, gameTime, ProducerTaskDeclarations.scopeOf(bucket), worksiteSnapshotLocal);
            if (snapshot.stations().isEmpty()) continue;
            sawAnyStation = true;
            for (StationSlot slot : snapshot.stations()) {
                if (!filter.test(slot)) continue;
                worksHere++;
                worked.add(slot.blockId().toString());
            }
            // Ingredients still come from the villager's own kitchen: a wider scope lets a cook
            // walk to a shared station, not raid the whole village's pantries.
            best = ProducerStationIndex.chooseForRole(
                    spec.role(), level, villager, snapshot, worksiteBoundsLocal, abandonedUntilByStation,
                    gameTime, recipeCooldownUntil, filter,
                    slot -> pathWorksites.isEmpty() || pathWorksites.contains(slot.blockId()) ? 0 : 1,
                    orderPriority, taskTypes);
            if (best != null) break;
        }
        if (!sawAnyStation) {
            debugChat(level, villager, "ACQUIRE:no stations found in worksite (" + worksiteBoundsLocal.size() + " bounds)");
            setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_WORKSITE, "");
            return null;
        }
        if (best == null) {
            // The one exit that used to be silent, which is why a cook standing in a working
            // kitchen was undiagnosable. Say which half failed and record it, so the Orders
            // screen shows a reason too rather than an idle worker and no explanation.
            if (worksHere == 0) {
                debugChat(level, villager, "ACQUIRE:" + worksiteSnapshotLocal.stations().size()
                        + " station(s) here, none this career is declared to work");
                setBlocked(level, villager, gameTime, ProducerBlockedReason.UNSUPPORTED_RECIPE, "");
            } else {
                // Deliberately does NOT blame ingredients: a station rejected earlier (blocked,
                // claimed, no stand) never reaches the recipe check at all, and saying "check
                // ingredients" sent us hunting a full chest. The SKIP lines carry the real reason.
                debugChat(level, villager, "ACQUIRE:can work " + worksHere + " station(s) "
                        + worked + " but none yielded a recipe — see SKIP lines above for why");
                setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_RECIPE, "");
            }
            return null;
        }

        stationType = best.station().type();
        debugChat(level, villager, "ACQUIRE:" + stationType.name()
                + " at " + best.station().pos().getX() + "," + best.station().pos().getY() + "," + best.station().pos().getZ());
        return new ProducerStationSelection(best.station().pos(), best.standPos(), best.recipe());
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
        if (stationAnchor == null || stationType == null) return ProducerStationState.BLOCKED;
        ProducerStationSessions.SessionSnapshot session = ProducerStationSessions.snapshot(level, stationAnchor);
        if (activeRecipe == null && session != null) {
            activeRecipe = ProducerWorkSupport.findSessionRecipe(spec.role(), level, session, stationType);
        }
        if (activeRecipe == null) {
            var def = com.aetherianartificer.townstead.work.station.StationProtocols
                    .defAt(level, stationAnchor);
            if (def != null && def.role() == StationType.PLACE_SURFACE) {
                ResourceLocation standing = BuiltInRegistries.BLOCK.getKey(
                        level.getBlockState(stationAnchor).getBlock());
                if (standing != null && standing.equals(def.doneBlock())) {
                    activeRecipe = com.aetherianartificer.townstead.work.station.ProtocolRecipes
                            .discover(level, def).stream()
                            .filter(candidate -> candidate.output().equals(def.doneBlock()))
                            .findFirst().orElse(null);
                    if (activeRecipe != null) {
                        debugChat(level, villager, "RECONCILE:recovered finished recipe "
                                + activeRecipe.output());
                    }
                }
            }
        }
        return ProductionStations.classify(level, villager, stationAnchor, stationType, fdRecipe(), session);
    }

    @Override
    protected boolean cleanupForeignStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || stationType == null) return false;
        Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
        boolean cleaned = ProductionStations.cleanup(level, villager, stationAnchor, stationType, worksiteBoundsLocal);
        return cleaned && !com.aetherianartificer.townstead.work.station.StationProtocols.hasAnyContents(level, stationAnchor);
    }

    // ── Recipe / gather / produce / collect ──

    /**
     * Everything this cook could make at the station they are standing at, for orders to choose
     * among. The same viability filter the autonomous pick uses, so an order can only ever select
     * something the cook would have been allowed to make anyway.
     */
    @Override
    protected java.util.List<? extends ProducerRecipe> orderCandidates(
            ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || stationType == null) return java.util.List.of();
        if (!Stations.isStation(level, stationAnchor)) return java.util.List.of();
        Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
        return com.aetherianartificer.townstead.work.recipe.RecipeSelector
                .viableRecipes(level, villager, stationType, stationAnchor, worksiteBoundsLocal,
                        recipeCooldownUntil,
                        ProducerWorkSupport.excludeBeverages(spec.role(), level, villager),
                        ProducerWorkSupport.restrictToBeverages(spec.role()),
                        taskTypes)
                .stream()
                .map(com.aetherianartificer.townstead.work.recipe.RecipeSelector.ScoredRecipe::recipe)
                .toList();
    }

    @Override
    protected @Nullable String noRecipeMissingRequirement(
            ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) return null;
        var def = com.aetherianartificer.townstead.work.station.StationProtocols
                .defAt(level, stationAnchor);
        if (def == null || def.harvestTools().isEmpty()
                || com.aetherianartificer.townstead.work.station.StationProtocols
                .hasHarvestTool(villager, def)) return null;
        Predicate<ItemStack> matcher = stack -> !stack.isEmpty()
                && def.harvestTools().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        String label = WorkRecipeRegistry.harvestToolRequirementName(def);
        // A tool in affiliated worksite storage is actionable and will be acquired normally.
        // Only report the prerequisite when neither the worker nor that storage can supply it.
        return WorkIngredients.nextPhysicalPull(level, villager, matcher, 1, true, label,
                activeWorksiteBounds(level, villager)) == null ? label : null;
    }

    @Override
    protected @Nullable ProducerRecipe pickRecipe(ServerLevel level, VillagerEntityMCA villager,
                                                  long gameTime,
                                                  java.util.function.Predicate<ResourceLocation> outputAllowed) {
        if (stationAnchor == null || stationType == null) return null;
        if (!Stations.isStation(level, stationAnchor)) return null;
        Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
        // An empty plate asks for a menu dish before the worker's own preference gets a say.
        // Nothing viable on the menu at this station is not a failure: fall through to the
        // ordinary pick, which is how the intermediate steps of a menu dish still get made.
        Set<ResourceLocation> menuDemand = com.aetherianartificer.townstead.food.ServingDemand
                .standing(level, villager, worksiteBoundsLocal);
        if (!menuDemand.isEmpty()) {
            DiscoveredRecipe dish = ProducerWorkSupport.pickRecipe(
                    spec.role(), level, villager, stationType, stationAnchor, worksiteBoundsLocal,
                    recipeCooldownUntil, output -> outputAllowed.test(output) && menuDemand.contains(output),
                    taskTypes);
            if (dish != null) {
                debugChat(level, villager, "SELECT:menu " + dish.output() + " for an empty plate");
                return dish;
            }
        }
        DiscoveredRecipe recipe = ProducerWorkSupport.pickRecipe(
                spec.role(), level, villager, stationType, stationAnchor, worksiteBoundsLocal,
                recipeCooldownUntil, outputAllowed, taskTypes);
        if (recipe == null) {
            int available = WorkRecipeRegistry.getRecipesForStation(level, stationType).size();
            debugChat(level, villager, "SELECT:no recipe for " + stationType.name()
                    + " (candidates=" + available + "), rotating");
        } else {
            debugChat(level, villager, "SELECT:" + recipe.output() + " tier=" + recipe.tier());
        }
        return recipe;
    }

    @Override
    protected int maximumBatchOperations(ServerLevel level, VillagerEntityMCA villager,
                                         ProducerRecipe candidate) {
        if (!(candidate instanceof DiscoveredRecipe recipe) || stationAnchor == null) return 1;
        int physical = com.aetherianartificer.townstead.work.station.StationProtocols
                .batchCapacity(level, stationAnchor, recipe);
        if (physical <= 1) return 1;
        var v2 = com.aetherianartificer.townstead.work.station.Workstations
                .v2ByState(level.getBlockState(stationAnchor));
        List<com.aetherianartificer.townstead.work.recipe.RecipeIngredient> scalableInputs =
                v2 == null ? recipe.inputs() : v2.ordinaryInputs(recipe.inputs());
        return WorkIngredients.craftableCopies(level, villager, recipe,
                activeWorksiteBounds(level, villager), physical, scalableInputs);
    }

    @Override
    protected @Nullable WorkIngredients.PhysicalPull nextPhysicalPull(
            ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null || stationAnchor == null) return null;
        Set<Long> bounds = activeWorksiteBounds(level, villager);
        DiscoveredRecipe recipe = fdRecipe();
        WorkIngredients.PhysicalPull pull = WorkIngredients.nextPhysicalRecipePull(
                level, villager, recipe, stationAnchor, bounds, activeBatchOperations());
        if (pull != null) return pull;
        var def = com.aetherianartificer.townstead.work.station.StationProtocols
                .defAt(level, stationAnchor);
        if (def == null || def.harvestTools().isEmpty()
                || com.aetherianartificer.townstead.work.station.StationProtocols
                .hasHarvestTool(villager, def)) return null;
        Predicate<ItemStack> matcher = stack -> !stack.isEmpty()
                && def.harvestTools().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return WorkIngredients.nextPhysicalPull(level, villager, matcher, 1, true,
                "harvest tool", bounds);
    }

    @Override
    protected Set<Long> transferWorksiteBounds(ServerLevel level, VillagerEntityMCA villager) {
        return activeWorksiteBounds(level, villager);
    }

    @Override
    protected boolean isCycleOutput(ServerLevel level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return CycleOutputMatcher.matches(fdRecipe(), BuiltInRegistries.ITEM.getKey(stack.getItem()),
                com.aetherianartificer.townstead.work.order.OrderProducts.key(stack));
    }

    @Override
    protected GatherResult gatherInputs(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null || stationAnchor == null || stationType == null) return GatherResult.fail(null);
        DiscoveredRecipe recipe = fdRecipe();
        Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
        WorkIngredients.PullResult pullResult = WorkIngredients.pullAndConsumeDetailed(
                level, villager, recipe, stationAnchor, stationType, stagedInputs,
                worksiteBoundsLocal, activeBatchOperations(), false);
        if (!pullResult.success()) {
            String recipeName = townstead$itemDisplayName(level, recipe.output());
            String missing = WorkIngredients.describeMissingRequirements(level, villager, recipe, stationAnchor, worksiteBoundsLocal);
            if (missing == null || missing.isBlank()) missing = pullResult.detail();
            String detail = (missing == null || missing.isBlank()) ? recipeName : missing;
            return GatherResult.fail(detail);
        }

        if (stationType == StationType.CUTTING_BOARD && !recipe.inputs().isEmpty()) {
            heldCuttingInput = findMatchingCuttingInput(villager.getInventory(), recipe);
            cuttingBoardItemPlaced = false;
            stickyBoardStationAnchor = stationAnchor.immutable();
            stickyBoardRecipeId = recipe.id();
            stickyBoardInputId = firstCuttingInputId(recipe);
        }

        debugChat(level, villager, "GATHER:success for " + recipe.output());
        return GatherResult.ok();
    }

    @Override
    protected void rollbackGather(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        WorkIngredients.rollbackStagedInputs(level, villager, stationAnchor, stagedInputs);
    }

    @Override
    protected boolean beginProduce(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return false;
        sweptProducedOutput = false;
        interruptedProduction = false;
        toolWorkActionPerformed = false;
        toolWorkAttempts = 0;
        toolWorkSwingsRemaining = TOOL_WORK_SWINGS;
        DiscoveredRecipe recipe = fdRecipe();
        if (com.aetherianartificer.townstead.work.station.StationProtocols.handles(level, stationAnchor)) {
            // The physical hand-off: place the work block and/or push the gathered items from
            // the villager's inventory into the station, the way a player's hands would.
            Set<Long> bounds = activeWorksiteBounds(level, villager);
            if (!com.aetherianartificer.townstead.work.station.StationProtocols.insert(
                    level, villager, stationAnchor, recipe, bounds, activeBatchOperations())) {
                debugChat(level, villager, "COOK:station refused inputs");
                return false;
            }
        }
        carriedOutputBaseline = countInventoryItem(villager.getInventory(), recipe.output());
        var v2 = stationAnchor == null ? null
                : com.aetherianartificer.townstead.work.station.Workstations
                        .v2ByState(level.getBlockState(stationAnchor));
        produceDoneTick = stationType == StationType.CUTTING_BOARD
                || (v2 != null && v2.hasRepeatableWorkAction())
                ? gameTime + 4L
                : gameTime + (long) recipe.cookTimeTicks() * activeBatchOperations();
        return true;
    }

    @Override
    protected boolean isProduceDone(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return true;
        DiscoveredRecipe recipe = fdRecipe();

        if (com.aetherianartificer.townstead.work.station.Workstations
                .v2ByState(level.getBlockState(stationAnchor)) != null
                && com.aetherianartificer.townstead.work.station.StationProtocols.handles(level, stationAnchor)) {
            var v2 = com.aetherianartificer.townstead.work.station.Workstations
                    .v2ByState(level.getBlockState(stationAnchor));
            boolean readyBeforeWork = com.aetherianartificer.townstead.work.station.StationProtocols.isReady(
                    level, villager, stationAnchor, recipe);
            if (ToolWorkActionGate.shouldPerform(v2 != null && v2.behaviorUses("tool"),
                    readyBeforeWork, toolWorkActionPerformed)) {
                if (toolWorkSwingsRemaining > 0) {
                    toolWorkSwingsRemaining--;
                    villager.swing(villager.getDominantHand());
                    produceDoneTick = gameTime + TOOL_SWING_INTERVAL_TICKS;
                    return false;
                }
                villager.swing(villager.getDominantHand());
                boolean acted = com.aetherianartificer.townstead.work.station.StationProtocols.work(
                        level, villager, stationAnchor, recipe);
                // A board hands its product straight to the worker and is empty afterwards, so
                // polling the block would wait for a result that is already in the apron pocket.
                if (carriedOutputComplete(villager, recipe)) {
                    toolWorkActionPerformed = true;
                    return true;
                }
                boolean idleAfterWork = com.aetherianartificer.townstead.work.station.StationProtocols
                        .isIdle(level, stationAnchor, recipe);
                if (!acted || idleAfterWork) {
                    // The block may report a consumed interaction while processing nothing (a
                    // board displaying a knife, a knife nobody carries). Repeating that every tick
                    // until the lease ends was the stall; give it a few honest tries, then move on.
                    toolWorkAttempts++;
                    if (toolWorkAttempts >= MAX_TOOL_WORK_ATTEMPTS) {
                        debugChat(level, villager, "COOK:tool action produced nothing at "
                                + stationAnchor.toShortString() + "; rotating station");
                        failCuttingBoard(level, villager, gameTime, recipe.output());
                        return false;
                    }
                    produceDoneTick = gameTime + TOOL_SWING_INTERVAL_TICKS;
                    return false;
                }
                toolWorkActionPerformed = true;
            }
            boolean ready = readyBeforeWork
                    || com.aetherianartificer.townstead.work.station.StationProtocols.isReady(
                            level, villager, stationAnchor, recipe);
            if (activeBatchOperations() > 1) {
                if (com.aetherianartificer.townstead.work.station.StationProtocols.hasPendingInputs(
                        level, stationAnchor, recipe)) return false;
                return ready || sweptProducedOutput;
            }
            if (sweptProducedOutput
                    && com.aetherianartificer.townstead.work.station.StationProtocols.isIdle(
                            level, stationAnchor, recipe)) return true;
            return ready;
        }

        if (stationType != StationType.CUTTING_BOARD
                && com.aetherianartificer.townstead.work.station.StationProtocols.handles(level, stationAnchor)) {
            if (sweptProducedOutput
                    && com.aetherianartificer.townstead.work.station.StationProtocols.isIdle(
                            level, stationAnchor, recipe)) {
                return true;
            }
            return com.aetherianartificer.townstead.work.station.StationProtocols.isReady(
                    level, villager, stationAnchor, recipe);
        }

        if (stationType == StationType.CUTTING_BOARD) {
            if (!cuttingBoardItemPlaced) {
                // Place phase
                if (!com.aetherianartificer.townstead.work.station.StationProtocols.insert(
                        level, villager, stationAnchor, recipe, activeWorksiteBounds(level, villager))) {
                    debugChat(level, villager, "COOK:missing cutting input");
                    failCuttingBoard(level, villager, gameTime, recipe.output());
                    return false;
                }
                cuttingBoardItemPlaced = true;
                heldCuttingInput = ItemStack.EMPTY;
                produceDoneTick = gameTime + 12L;
                villager.swing(InteractionHand.MAIN_HAND);
                return false;
            }
            // Process phase
            boolean processed = com.aetherianartificer.townstead.work.station.StationProtocols.work(
                    level, villager, stationAnchor, recipe);
            heldCuttingInput = ItemStack.EMPTY;
            cuttingBoardItemPlaced = false;
            if (!processed) {
                debugChat(level, villager, "COOK:cutting board failed");
                failCuttingBoard(level, villager, gameTime, recipe.output());
                return false;
            }
            return true;
        }

        if (stationType == StationType.HOT_STATION
                && stationAnchor != null
                && !ProducerOutputHelper.hotStationOutputCollectible(level, stationAnchor, recipe)
                && !com.aetherianartificer.townstead.work.station.StationContents.hasOutput(level, stationAnchor, WorkRecipeRegistry.allOutputIds(level))) {
            return false;
        }

        return true;
    }

    private void failCuttingBoard(ServerLevel level, VillagerEntityMCA villager, long gameTime, ResourceLocation failedOutput) {
        onSessionRelease(level, villager, stationAnchor, gameTime);
        activeRecipe = null;
        clearStickyBoardVisuals();
        recipeCooldownUntil.put(failedOutput, gameTime + RECIPE_REPEAT_COOLDOWN_TICKS);
        abandonCurrentStation(level, villager, gameTime, true);
    }

    @Override
    protected CollectResult collectFromStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Reconciliation may discover a finished station after the task which started it was
        // stopped (schedule change, unload, reassignment). In that recovery path there is no
        // active recipe, but the station adapter can still collect its available output. Keep
        // the nullable recipe in one local: dereferencing fdRecipe() here crashed the server
        // while an unrelated cook recovered an already-finished station.
        DiscoveredRecipe recipe = fdRecipe();
        Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
        Set<ResourceLocation> outputIds = WorkRecipeRegistry.allOutputIds(level);
        var def = stationAnchor == null ? null
                : com.aetherianartificer.townstead.work.station.StationProtocols
                        .defAt(level, stationAnchor);
        if (def != null && !def.harvestTools().isEmpty()
                && !com.aetherianartificer.townstead.work.station.StationProtocols
                        .hasHarvestTool(villager, def)) {
            String label = WorkRecipeRegistry.harvestToolRequirementName(def);
            Predicate<ItemStack> matcher = stack -> !stack.isEmpty()
                    && def.harvestTools().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (harvestToolPull == null) {
                harvestToolPull = WorkIngredients.nextPhysicalPull(
                        level, villager, matcher, 1, true, label, worksiteBoundsLocal);
            }
            if (harvestToolPull == null) {
                setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_INGREDIENTS, label);
                return CollectResult.waiting(false);
            }
            BlockPos source = harvestToolPull.source();
            net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(
                    villager, source, WALK_SPEED, 1);
            villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                    new net.minecraft.world.entity.ai.behavior.BlockPosTracker(source));
            if (villager.distanceToSqr(source.getX() + 0.5, source.getY() + 0.5,
                    source.getZ() + 0.5) > 5.0d) {
                return CollectResult.waiting(false);
            }
            WorkIngredients.PhysicalPullResult pulled = WorkIngredients.executePhysicalPull(
                    level, villager, harvestToolPull);
            harvestToolPull = null;
            if (pulled.count() <= 0) return CollectResult.waiting(false);
            rememberBorrowedTools(pulled.itemIds());
            villager.swing(villager.getDominantHand());
            debugChat(level, villager, "COLLECT:acquired " + label + " from "
                    + source.toShortString());
            return CollectResult.waiting(false);
        }
        harvestToolPull = null;
        // Tool recovery may have walked away from the station. Return before invoking the
        // adapter; harvesting across the room would satisfy the code but not the physical story.
        if (def != null && !def.harvestTools().isEmpty() && standPos != null
                && villager.distanceToSqr(standPos.getX() + 0.5, standPos.getY() + 0.5,
                        standPos.getZ() + 0.5) > NEAR_STATION_DISTANCE_SQ) {
            net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(
                    villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
            return CollectResult.waiting(false);
        }
        int producedInHand = recipe == null ? 0
                : countInventoryItem(villager.getInventory(), recipe.output()) - carriedOutputBaseline;
        if (recipe != null && producedInHand
                >= Math.max(1, recipe.outputCount()) * activeBatchOperations()) {
            // A real player interaction may return its product directly to the actor instead of
            // leaving it resident in the block (or as a world drop). The station is already
            // drained in that case; the new carried stack is the physical completion evidence.
            rememberAppraisal(villager);
            return CollectResult.ofCollected();
        }
        boolean collected = ProducerOutputHelper.collectSurfaceDrops(level, villager, stationAnchor, worksiteBoundsLocal, outputIds);

        if (com.aetherianartificer.townstead.work.station.StationProtocols.handles(level, stationAnchor)) {
            boolean sweptAndDrained = sweptProducedOutput
                    && com.aetherianartificer.townstead.work.station.StationProtocols.isIdle(
                            level, stationAnchor, recipe);
            boolean harvested = com.aetherianartificer.townstead.work.station.StationProtocols.collect(
                    level, villager, stationAnchor, recipe, worksiteBoundsLocal);
            // Harvest throws the product into the world (the peel lift); sweep it up.
            collected |= ProducerOutputHelper.collectSurfaceDrops(level, villager, stationAnchor, worksiteBoundsLocal, outputIds);
            // collectAvailable() is the recipe-less recovery operation. A successful adapter
            // call has already moved its output into the villager's inventory, so it is the
            // evidence itself; with a known recipe retain the stricter expected-count check.
            boolean carriedOutput = recipe == null
                    ? harvested
                    : countInventoryItem(villager.getInventory(), recipe.output())
                            >= Math.max(1, recipe.outputCount());
            if (harvested || collected || sweptAndDrained) {
                if (!collected && !carriedOutput && !sweptAndDrained) {
                    return CollectResult.waiting(false);
                }
                rememberAppraisal(villager);
                return CollectResult.ofCollected();
            }
            return CollectResult.waiting(false);
        }

        if (stationType == StationType.HOT_STATION) {
            ProducerOutputHelper.CollectResult result = ProducerOutputHelper.collectHotStationOutputs(
                    level, villager, stationAnchor, fdRecipe(), worksiteBoundsLocal, outputIds, true);
            if (result.shouldWait()) return CollectResult.waiting(false);
            collected |= result.collected();
        }

        if ((stationType == StationType.FIRE_STATION || stationType == StationType.CUTTING_BOARD) && !collected) {
            return CollectResult.waiting(false);
        }

        return collected ? CollectResult.ofCollected() : CollectResult.none();
    }

    @Override
    protected void storeOutputs(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
        Set<ResourceLocation> outputIds = WorkRecipeRegistry.allOutputIds(level);
        ProducerOutputHelper.finishCollectInventoryOutputs(level, villager, pendingOutput, stationAnchor, worksiteBoundsLocal, outputIds);
    }

    @Override
    protected void awardProductionXp(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return;
        // Appraisable outputs (a pizza's taste) speak for themselves: a better product
        // out-earns its recipe tier.
        int xp = Math.max(1, activeRecipe.tier());
        java.util.Map<String, String> metadata = new java.util.LinkedHashMap<>(java.util.Map.of(
                "recipe", activeRecipe.id().toString(),
                "station", stationType.name().toLowerCase(java.util.Locale.ROOT),
                "tier", Integer.toString(activeRecipe.tier()),
                "amount", Integer.toString(Math.max(1, activeRecipe.outputCount()))));
        if (lastAppraisal != null) {
            xp = Math.max(xp, lastAppraisal.quality());
            metadata.put("taste", lastAppraisal.label());
            lastAppraisal = null;
        }
        // Credit the villager's own canonical career. Careers are flat: a profession composing
        // this engine levels itself, with no privileged built-in fallback.
        ResourceLocation careerId = null;
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (com.aetherianartificer.townstead.work.WorkTaskDeclarations.professionDeclares(profession, taskTypes)) {
            careerId = com.aetherianartificer.townstead.profession.def.ProfessionDefs
                    .canonicalId(BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession));
        }
        if (careerId == null) return;
        // The engine owns the completed-work activity. A profession chooses this task; it does
        // not get to rename the same work in the Chronicle.
        String activity = spec.taskType().toString();
        com.aetherianartificer.townstead.profession.career.CareerProgression.completeWork(
                villager, careerId, xp, level.getGameTime(),
                activity, activeRecipe.output(), "dish", activeRecipe.tier(),
                metadata);
    }

    /** After a protocol harvest, read quality off the actual product now in inventory. */
    private void rememberAppraisal(VillagerEntityMCA villager) {
        DiscoveredRecipe recipe = fdRecipe();
        if (recipe == null) return;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            var stack = villager.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (!recipe.output().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
            var appraisal = com.aetherianartificer.townstead.work.OutputAppraisal.appraise(stack);
            if (appraisal != null) {
                lastAppraisal = appraisal;
                return;
            }
        }
    }

    // ── Hooks ──

    @Override
    protected PreparationResult prepareStation(
            ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) return PreparationResult.blocked("The station is no longer here.");
        var def = com.aetherianartificer.townstead.work.station.Workstations
                .v2ByState(level.getBlockState(stationAnchor));
        if (def == null) {
            drivers.release(level);
            return PreparationResult.ready();
        }
        Worksite site = activeWorksite();
        if (def.hasReservation() && site == null) {
            return PreparationResult.blocked("This station is not attached to a worksite.");
        }
        return drivers.prepare(level, villager, site, stationAnchor, def, claimedOrder());
    }

    @Override
    protected void onProduceTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        drivers.maintainWorkerEngagement(villager);
        if (stationAnchor != null && activeRecipe != null
                && gameTime % WORK_ACTION_INTERVAL_TICKS == 0) {
            var v2 = com.aetherianartificer.townstead.work.station.Workstations
                    .v2ByState(level.getBlockState(stationAnchor));
            boolean pendingBatch = activeBatchOperations() > 1
                    && com.aetherianartificer.townstead.work.station.StationProtocols.hasPendingInputs(
                            level, stationAnchor, fdRecipe());
            if (v2 != null && v2.hasRepeatableWorkAction()
                    && (pendingBatch
                    || !com.aetherianartificer.townstead.work.station.StationProtocols.isReady(
                            level, villager, stationAnchor, fdRecipe()))) {
                boolean pendingBefore = com.aetherianartificer.townstead.work.station.StationProtocols
                        .hasPendingInputs(level, stationAnchor, fdRecipe());
                boolean acted = com.aetherianartificer.townstead.work.station.StationProtocols.work(
                        level, villager, stationAnchor, fdRecipe());
                boolean pendingAfter = com.aetherianartificer.townstead.work.station.StationProtocols
                        .hasPendingInputs(level, stationAnchor, fdRecipe());
                boolean readyAfter = com.aetherianartificer.townstead.work.station.StationProtocols
                        .isReady(level, villager, stationAnchor, fdRecipe());
                if (acted && pendingBefore && !pendingAfter && !readyAfter && !sweptProducedOutput) {
                    recoverInterruptedInputs(level, villager);
                    interruptedProduction = true;
                }
            }
        }
        if (stationType == StationType.FIRE_STATION && stationAnchor != null) {
            Set<ResourceLocation> outputIds = WorkRecipeRegistry.allOutputIds(level);
            Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
            List<ItemStack> drops = com.aetherianartificer.townstead.work.station.StationDropOutputs.collectWithinWorksite(
                    level, stationAnchor, outputIds, worksiteBoundsLocal);
            boolean carriedAll = !drops.isEmpty();
            for (ItemStack drop : drops) {
                carriedAll &= ProducerOutputHelper.storeOutput(
                        level, villager, drop, stationAnchor, worksiteBoundsLocal);
            }
            if (carriedAll) sweptProducedOutput = true;
        }
    }

    @Override
    protected boolean mustWaitBeyondCollectTimeout(ServerLevel level, VillagerEntityMCA villager) {
        var v2 = stationAnchor == null ? null
                : com.aetherianartificer.townstead.work.station.Workstations
                        .v2ByState(level.getBlockState(stationAnchor));
        var legacy = stationAnchor == null ? null
                : com.aetherianartificer.townstead.work.station.StationProtocols
                        .defAt(level, stationAnchor);
        return stationType == StationType.HOT_STATION || stationType == StationType.CUTTING_BOARD
                || (v2 != null && v2.collect() != null)
                || (legacy != null && legacy.role() == StationType.PLACE_SURFACE
                        && !legacy.harvestTools().isEmpty());
    }

    @Override
    protected void onSessionRefresh(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || activeRecipe == null) return;
        DiscoveredRecipe recipe = fdRecipe();
        ProducerStationSessions.beginOrRefresh(
                level, villager.getUUID(), stationAnchor,
                recipe.id(), recipe.output(), recipe.outputCount() * activeBatchOperations(),
                stagedInputs, gameTime + STATION_SESSION_LEASE_TICKS);
    }

    @Override
    protected void onSessionRelease(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos pos, long gameTime) {
        if (pos == null) return;
        ProducerStationSessions.release(level, villager.getUUID(), pos);
    }

    @Override
    protected void onOrderCompleted(ServerLevel level, VillagerEntityMCA villager,
                                    @Nullable BlockPos pos, long gameTime) {
        // The participant belongs to the order, not permanently to the machine. Keep them engaged
        // across that order's batches, then free them as soon as the stored/produced count really
        // reaches its target. The coordinator handles workers and animals through the same public
        // reservation lifecycle.
        releaseStationDriver(level, villager, pos);
    }

    @Override
    protected void onNoWork(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        releaseStationDriver(level, villager, stationAnchor);
    }

    @Override
    protected void onStationAbandoned(ServerLevel level, VillagerEntityMCA villager,
                                      @Nullable BlockPos pos, long gameTime) {
        harvestToolPull = null;
        releaseStationDriver(level, villager, pos);
    }

    /** Releases both a task-owned participant and a persisted binding recovered after reload. */
    private void releaseStationDriver(ServerLevel level, VillagerEntityMCA villager,
                                      @Nullable BlockPos pos) {
        Worksite site = activeWorksite();
        if (pos != null) {
            var def = com.aetherianartificer.townstead.work.station.Workstations
                    .v2ByState(level.getBlockState(pos));
            drivers.releaseObserved(level, villager, site, pos, def);
            return;
        }
        // A fresh task has no stationAnchor, but the machine can retain its binding across a world
        // save. If this worksite has no usable work, inspect only its authored driven stations and
        // release any participant physically observed there.
        if (site != null) {
            for (long packed : com.aetherianartificer.townstead.work.site.Worksites.extentOf(level, site)) {
                BlockPos candidate = BlockPos.of(packed);
                var def = com.aetherianartificer.townstead.work.station.Workstations
                        .v2ByState(level.getBlockState(candidate));
                if (def == null || !def.hasReservation() || !def.isOperational(level, candidate)) continue;
                drivers.releaseObserved(level, villager, site, candidate, def);
            }
        } else {
            drivers.release(level);
        }
    }

    @Override
    protected boolean isProductionInterrupted(ServerLevel level, VillagerEntityMCA villager,
                                              long gameTime) {
        return interruptedProduction;
    }

    @Override
    protected void onProductionInterrupted(ServerLevel level, VillagerEntityMCA villager,
                                           long gameTime) {
        interruptedProduction = false;
        sweptProducedOutput = false;
    }

    /**
     * A real block interaction may eject its rejected/reset inputs as item entities. Recover only
     * items accepted by the active recipe, then let the ordinary gather path reload the station.
     */
    private void recoverInterruptedInputs(ServerLevel level, VillagerEntityMCA villager) {
        if (stationAnchor == null || activeRecipe == null) return;
        java.util.LinkedHashSet<ResourceLocation> recoverable = new java.util.LinkedHashSet<>();
        for (com.aetherianartificer.townstead.work.recipe.RecipeIngredient ingredient : fdRecipe().inputs()) {
            recoverable.addAll(ingredient.itemIds());
        }
        if (fdRecipe().containerItemId() != null) recoverable.add(fdRecipe().containerItemId());
        if (recoverable.isEmpty()) return;
        Set<Long> bounds = activeWorksiteBounds(level, villager);
        List<ItemStack> drops = com.aetherianartificer.townstead.work.station.StationDropOutputs
                .collectWithinWorksite(level, stationAnchor, recoverable, bounds);
        for (ItemStack drop : drops) {
            ProducerOutputHelper.storeOutput(level, villager, drop, stationAnchor, bounds);
        }
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
    protected void onOpportunisticSweep(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        ResourceLocation watchedOutput = activeRecipe == null ? null : activeRecipe.output();
        boolean sweptActiveOutput = ProducerOutputHelper.sweepWorksiteOutputs(
                level, villager, stationAnchor, watchedOutput,
                activeWorksiteBounds(level, villager), WorkRecipeRegistry.allOutputIds(level));
        // Some stations expose their result only as an item entity (campfires and the Farm &
        // Charm mincer), while interaction-driven stations may eject a completed inventory result
        // before the next phase poll. The worksite sweep has already conserved that real stack in
        // storage; remember it for every active protocol station so an empty, drained machine can
        // finish instead of repeating its work action until the Behavior's one-minute lease ends.
        if (sweptActiveOutput && stationAnchor != null && activeRecipe != null
                && com.aetherianartificer.townstead.work.station.StationProtocols.handles(
                        level, stationAnchor)) {
            sweptProducedOutput = true;
        }
    }

    @Override
    protected void onStop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        releaseStationDriver(level, villager, stationAnchor);
        resetBoardSession(villager);
        stationType = null;
        lastAppraisal = null;
        sweptProducedOutput = false;
        carriedOutputBaseline = 0;
        interruptedProduction = false;
        harvestToolPull = null;
        cachedWorksiteWorkArea = Set.of();
        cachedWorksiteWorkAnchor = null;
        cachedWorksiteWorkUntil = 0L;
        cachedWorksiteSnapshotNav = WorkBuildingNav.Snapshot.EMPTY;
        cachedServiceSite = null;
        cachedServiceSiteUntil = Long.MIN_VALUE;
        scopedSnapshots.clear();
    }

    @Override
    protected void onClearAll(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        releaseStationDriver(level, villager, stationAnchor);
        resetBoardSession(villager);
        stationType = null;
        lastAppraisal = null;
        sweptProducedOutput = false;
        carriedOutputBaseline = 0;
        interruptedProduction = false;
        harvestToolPull = null;
        cachedWorksiteWorkArea = Set.of();
        cachedWorksiteWorkAnchor = null;
        cachedWorksiteWorkUntil = 0L;
        cachedWorksiteSnapshotNav = WorkBuildingNav.Snapshot.EMPTY;
        cachedServiceSite = null;
        cachedServiceSiteUntil = Long.MIN_VALUE;
        scopedSnapshots.clear();
    }

    @Override
    protected void playGatherSound(ServerLevel level, VillagerEntityMCA villager) {
        if (stationAnchor == null || stationType == null) return;
        if (stationType == StationType.CUTTING_BOARD) {
            level.playSound(null, stationAnchor, SoundEvents.AXE_STRIP, net.minecraft.sounds.SoundSource.BLOCKS, 0.35f, 1.1f);
        } else {
            level.playSound(null, stationAnchor, SoundEvents.CAMPFIRE_CRACKLE, net.minecraft.sounds.SoundSource.BLOCKS, 0.35f, 1.0f);
        }
    }

    @Override
    protected void announceBlocked(ServerLevel level, VillagerEntityMCA villager, long gameTime,
                                   ProducerBlockedReason reason, @Nullable String detail) {
        if (reason == ProducerBlockedReason.NONE || reason == ProducerBlockedReason.NO_RECIPE) return;
        if (!com.aetherianartificer.townstead.work.feedback.WorkFeedbackTicker
                .repeatedRequestsEnabled()) return;
        if (shouldSuppressStaleRequest(level, villager, reason)) return;
        if (nextRequestTick == 0) {
            nextRequestTick = gameTime + REQUEST_INITIAL_DELAY_TICKS;
            return;
        }
        if (gameTime < nextRequestTick) return;
        if (level.getNearestPlayer(villager, REQUEST_RANGE) == null) return;
        if (reason == ProducerBlockedReason.UNREACHABLE
                && !shouldAnnounceBlockedNavigation(level, villager, activeWorkTarget(level, villager))) {
            return;
        }
        String rule = switch (reason) {
            case NO_WORKSITE -> "no_worksite";
            case NO_INGREDIENTS -> detail != null && !detail.isBlank() ? "no_input_item" : "no_input";
            case NO_STORAGE -> "no_storage";
            case UNREACHABLE -> "unreachable";
            default -> null;
        };
        if (rule == null) return;
        Object[] arguments = reason == ProducerBlockedReason.NO_INGREDIENTS
                && detail != null && !detail.isBlank()
                ? new Object[]{detail} : new Object[0];
        ResourceLocation career = com.aetherianartificer.townstead.profession.def.ProfessionDefs
                .canonicalId(BuiltInRegistries.VILLAGER_PROFESSION
                        .getKey(villager.getVillagerData().getProfession()));
        if (career == null) return;
        if (!com.aetherianartificer.townstead.work.feedback.WorkFeedbackTicker.send(
                villager, career, rule, gameTime, arguments)) {
            // The item-specific form is optional. A profession may author only the general input
            // request and still receive useful feedback.
            if (!"no_input_item".equals(rule)
                    || !com.aetherianartificer.townstead.work.feedback.WorkFeedbackTicker.send(
                    villager, career, "no_input", gameTime)) return;
        }
        nextRequestTick = gameTime
                + com.aetherianartificer.townstead.work.feedback.WorkFeedbackTicker
                .effectiveInterval(career);
    }

    private boolean shouldSuppressStaleRequest(
            ServerLevel level, VillagerEntityMCA villager, ProducerBlockedReason reason) {
        return switch (reason) {
            case NO_WORKSITE -> !activeWorksiteSnapshot(level, villager).stations().isEmpty();
            case NO_INGREDIENTS -> {
                DiscoveredRecipe recipe = fdRecipe();
                String missing = recipe == null ? null : WorkIngredients.describeMissingRequirements(
                        level, villager, recipe, stationAnchor, cachedWorksiteWorkArea);
                yield missing != null && missing.isBlank();
            }
            default -> false;
        };
    }

    @Override
    protected void debugTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        maintainStickyBoardVisuals(villager);
        if (!TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (gameTime < nextDebugTick) return;
        if (!(level.getNearestPlayer(villager, REQUEST_RANGE) instanceof ServerPlayer player)) return;
        String cookName = villager.getName().getString();
        String cookId = villager.getUUID().toString();
        if (cookId.length() > 8) cookId = cookId.substring(0, 8);
        String recipe = activeRecipe == null ? "none" : activeRecipe.output().toString();
        String anchor = stationAnchor == null ? "none" : stationAnchor.getX() + "," + stationAnchor.getY() + "," + stationAnchor.getZ();
        String here = villager.blockPosition().toShortString();
        String stand = standPos == null ? "none" : standPos.toShortString();
        String interaction = stationAnchor == null ? "n/a"
                : Boolean.toString(canInteractWithStation(level, villager, stationAnchor));
        String station = stationType == null ? "none" : stationType.name().toLowerCase();
        String idleInfo = gameTime < idleUntilTick ? " idle=" + (idleUntilTick - gameTime) : "";
        StorageSearchContext.Snapshot storageSnapshot = StorageSearchContext.Profiler.snapshot();
        VillageAiBudget.Snapshot budgetSnapshot = VillageAiBudget.snapshot();
        WorkNavigationMetrics.Snapshot navSnapshot = WorkNavigationMetrics.snapshot();
        ConsumableTargetClaims.Snapshot claimSnapshot = ConsumableTargetClaims.snapshot();
        WorkBuildingNav.Snapshot worksiteSnapshotLocal = activeWorksiteSnapshot(level, villager);
        String assignedSiteDesc = com.aetherianartificer.townstead.profession.ProfessionSites.serviceSite(level, villager, com.aetherianartificer.townstead.profession.ProfessionSites.defForTask(spec.taskType()))
                .map(site -> site.building() != null
                        ? townstead$describeAssignedBuilding(level, site.building())
                        : "post@" + site.post().getX() + "," + site.post().getY() + "," + site.post().getZ())
                .orElse("none");
        String navMode = townstead$navigationMode();
        player.sendSystemMessage(Component.literal("[" + spec.label() + "DBG:" + cookName + "#" + cookId + "] state=" + state.name()
                + " station=" + station + " anchor=" + anchor + " here=" + here
                + " stand=" + stand + " interact=" + interaction + " recipe=" + recipe
                + " doneAt=" + produceDoneTick + " blocked=" + blocked.name()
                + " mode=" + navMode + " site=" + assignedSiteDesc + " stations=" + worksiteSnapshotLocal.stations().size()
                + " storage=" + storageSnapshot.observedBlocks()
                + "/" + storageSnapshot.handlerLookups()
                + " budget=" + budgetSnapshot.granted() + "/" + budgetSnapshot.throttled()
                + " nav=" + navSnapshot.snapshotRebuilds() + "/" + navSnapshot.pathAttempts()
                + "/" + navSnapshot.pathSuccesses() + "/" + navSnapshot.pathFailures()
                + " claims=" + claimSnapshot.grants() + "/" + claimSnapshot.conflicts() + "/" + claimSnapshot.activeClaims()
                + idleInfo));
        nextDebugTick = gameTime + 100L;
    }

    @Override
    protected String debugLabel() { return spec.label(); }

    // ── Subclass helpers ──

    /** Typed downcast — base stores {@code activeRecipe} as {@link ProducerRecipe}. */
    private @Nullable DiscoveredRecipe fdRecipe() {
        return (DiscoveredRecipe) activeRecipe;
    }

    /** Whether this cycle's expected product has arrived in the worker's own inventory. */
    private boolean carriedOutputComplete(VillagerEntityMCA villager, DiscoveredRecipe recipe) {
        int expected = Math.max(1, recipe.outputCount()) * activeBatchOperations();
        return countInventoryItem(villager.getInventory(), recipe.output()) - carriedOutputBaseline
                >= expected;
    }

    private static int countInventoryItem(SimpleContainer inventory, ResourceLocation itemId) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()
                    && itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * The cook's active service site, kept briefly across eligibility and worksite probes. Secondary
     * order sheets can still redirect the worker within one second, while a waiting producer no
     * longer re-resolves every compatible building and its effective MCA type every brain tick.
     */
    private @Nullable com.aetherianartificer.townstead.profession.ProfessionSites.Site resolveAssignedSite(
            ServerLevel level, VillagerEntityMCA villager) {
        long now = level.getGameTime();
        if (now > cachedServiceSiteUntil) {
            cachedServiceSite = com.aetherianartificer.townstead.profession.ProfessionSites
                    .serviceSite(level, villager,
                            com.aetherianartificer.townstead.profession.ProfessionSites
                                    .defForTask(spec.taskType()))
                    .orElse(null);
            cachedServiceSiteUntil = now + SERVICE_SITE_CACHE_TICKS;
        }
        return cachedServiceSite;
    }

    /** Reference block and bounds from one resolution. The form every work step should use. */
    private Set<Long> activeWorksiteBounds(ServerLevel level, VillagerEntityMCA villager) {
        com.aetherianartificer.townstead.profession.ProfessionSites.Site site = resolveAssignedSite(level, villager);
        return activeWorksiteBounds(villager, referenceFor(villager, site), site);
    }

    /** Resolve the set of blocks belonging to this villager's active kitchen (assigned or nearest). */
    private Set<Long> activeWorksiteBounds(VillagerEntityMCA villager, BlockPos anchor,
                                          @Nullable com.aetherianartificer.townstead.profession.ProfessionSites.Site site) {
        if (villager.level() instanceof ServerLevel level && site != null) {
            // The walkable room derived from the world, remembered on the worksite record so two
            // cooks in one kitchen share one flood fill instead of each running their own.
            Building assigned = site.building();
            Set<Long> extent = assigned != null
                    ? Worksites.extentOf(level, Worksites.of(level, assigned), assigned, null)
                    : site.post() != null
                            ? Worksites.extentOf(level, Worksites.of(level, site.post()), null, site.post())
                            : Set.of();
            // An assignment that yields nothing standable is not an answer: fall through to the
            // nearest kitchen exactly as before, or a cook with a degenerate room stops working.
            if (!extent.isEmpty()) return extent;
        }

        List<Building> kitchens = fallbackBuildings(villager);
        if (kitchens.isEmpty()) return Set.of();

        Building selected = null;
        if (anchor != null) {
            long anchorKey = anchor.asLong();
            for (Building building : kitchens) {
                for (BlockPos bp : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                    if (bp.asLong() == anchorKey) { selected = building; break; }
                }
                if (selected != null) break;
            }
        }
        if (selected == null) {
            BlockPos reference = anchor != null ? anchor : villager.blockPosition();
            double best = Double.MAX_VALUE;
            for (Building building : kitchens) {
                BlockPos center = building.getCenter();
                if (center == null) continue;
                double dist = reference.distSqr(center);
                if (dist < best) { best = dist; selected = building; }
            }
            if (selected == null) selected = kitchens.get(0);
        }

        if (!(villager.level() instanceof ServerLevel serverLevel)) return Set.of();
        Building fallback = selected;
        return Worksites.extentOf(serverLevel, Worksites.of(serverLevel, fallback), fallback, null);
    }

    /** Cache the expensive kitchen bounds / walkable-interior snapshot for ROOM_BOUNDS_CACHE_TICKS. */
    private void cacheWorksiteWorkArea(BlockPos anchor, long gameTime, Set<Long> bounds) {
        cachedWorksiteWorkAnchor = anchor == null ? null : anchor.immutable();
        cachedWorksiteWorkArea = bounds == null ? Set.of() : bounds;
        cachedWorksiteWorkUntil = gameTime + ROOM_BOUNDS_CACHE_TICKS;
    }

    /** Returns a cached WorkBuildingNav.Snapshot for the active kitchen (rebuilds at TTL expiry). */
    private WorkBuildingNav.Snapshot activeWorksiteSnapshot(ServerLevel level, VillagerEntityMCA villager) {
        com.aetherianartificer.townstead.profession.ProfessionSites.Site site = resolveAssignedSite(level, villager);
        BlockPos anchor = referenceFor(villager, site);
        long gameTime = level.getGameTime();
        if (anchor != null && cachedWorksiteWorkAnchor != null
                && anchor.equals(cachedWorksiteWorkAnchor)
                && gameTime <= cachedWorksiteWorkUntil
                && !cachedWorksiteSnapshotNav.walkableInterior().isEmpty()) {
            return cachedWorksiteSnapshotNav;
        }
        Set<Long> bounds = activeWorksiteBounds(villager, anchor, site);
        WorkBuildingNav.Snapshot snapshot = WorkBuildingNav.snapshot(level, bounds, anchor);
        cachedWorksiteSnapshotNav = snapshot;
        cacheWorksiteWorkArea(anchor, gameTime, snapshot.walkableInterior());
        return snapshot;
    }

    /** How far out {@code nearby} reaches from the work site, in blocks. */
    private static final int NEARBY_SCOPE_RADIUS = 12;

    /**
     * The station search for one scope. {@code worksite} reuses the kitchen snapshot the task
     * already keeps; the wider scopes get their own, cached on the same TTL and behind the village
     * AI budget so several cooks widening at once cannot stack full rescans in one tick.
     */
    private WorkBuildingNav.Snapshot scopedSnapshot(
            ServerLevel level, VillagerEntityMCA villager, long gameTime,
            com.aetherianartificer.townstead.profession.def.WorkTaskDef.Scope scope,
            WorkBuildingNav.Snapshot worksiteSnapshot) {
        if (scope == com.aetherianartificer.townstead.profession.def.WorkTaskDef.Scope.WORKSITE) {
            return worksiteSnapshot;
        }
        BlockPos anchor = activeWorksiteReference(villager);
        ScopedSnapshot cached = scopedSnapshots.get(scope);
        if (cached != null && gameTime <= cached.expiresAt()
                && java.util.Objects.equals(anchor, cached.anchor())) {
            return cached.snapshot();
        }
        // Budgeted per work site, not globally: cooks in different kitchens widen independently,
        // and only cooks sharing one kitchen contend. A refusal is not a failure — the previous
        // snapshot still answers, and with none yet the villager keeps to its work site this tick
        // and widens on a later one.
        String budgetKey = "cook-scope:" + scope.name() + ":" + (anchor == null ? "none" : anchor.asLong());
        if (!com.aetherianartificer.townstead.storage.VillageAiBudget.tryConsume(level, budgetKey, 1)) {
            return cached != null ? cached.snapshot() : worksiteSnapshot;
        }

        Set<Long> bounds = scopedBounds(level, villager, scope, anchor);
        WorkBuildingNav.Snapshot snapshot = bounds.isEmpty()
                ? worksiteSnapshot
                : WorkBuildingNav.snapshot(level, bounds, anchor);
        scopedSnapshots.put(scope, new ScopedSnapshot(
                anchor == null ? null : anchor.immutable(), snapshot, gameTime + ROOM_BOUNDS_CACHE_TICKS));
        return snapshot;
    }

    /** The cells a scope's station search covers, always including the villager's own work site. */
    private Set<Long> scopedBounds(
            ServerLevel level, VillagerEntityMCA villager,
            com.aetherianartificer.townstead.profession.def.WorkTaskDef.Scope scope,
            @Nullable BlockPos anchor) {
        Set<Long> bounds = new java.util.HashSet<>(activeWorksiteBounds(level, villager));
        switch (scope) {
            case NEARBY -> {
                if (anchor == null) break;
                int r = NEARBY_SCOPE_RADIUS;
                for (BlockPos pos : BlockPos.betweenClosed(
                        anchor.offset(-r, -4, -r), anchor.offset(r, 4, r))) {
                    bounds.add(pos.asLong());
                }
            }
            case VILLAGE -> {
                Optional<Village> village = com.aetherianartificer.townstead.profession.ProfessionCapacity.resolveVillage(villager);
                if (village.isEmpty()) break;
                for (Building building : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village.get())) {
                    bounds.addAll(com.aetherianartificer.townstead.work.WorkSiteBounds.workArea(level, building));
                }
            }
            case WORKSITE -> { }
        }
        return bounds;
    }

    /** Pick the best reference block for site queries: assigned kitchen center, outdoor post, station anchor, or nearest. */
    private BlockPos activeWorksiteReference(VillagerEntityMCA villager) {
        com.aetherianartificer.townstead.profession.ProfessionSites.Site site = villager.level() instanceof ServerLevel level
                ? resolveAssignedSite(level, villager)
                : null;
        return referenceFor(villager, site);
    }

    /** The same choice, made from a site somebody already resolved. */
    private BlockPos referenceFor(VillagerEntityMCA villager,
                                  @Nullable com.aetherianartificer.townstead.profession.ProfessionSites.Site site) {
        if (site != null) {
            Building assigned = site.building();
            if (assigned != null) {
                BlockPos center = assigned.getCenter();
                if (center != null) return center;
                for (BlockPos bp : (Iterable<BlockPos>) assigned.getBlockPosStream()::iterator) {
                    return bp.immutable();
                }
            }
            // Outdoor post: the workstation block itself is the stable cache anchor.
            if (site.post() != null) return site.post();
        }
        if (stationAnchor != null) return stationAnchor;
        BlockPos nearest = nearestKitchenAnchor(villager);
        return nearest != null ? nearest : villager.blockPosition();
    }

    /** True when the villager is inside the active kitchen (or standing on one of its station stands). */
    private boolean isVillagerInActiveWorksite(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        return WorkBuildingNav.isInsideOrOnStationStand(activeWorksiteSnapshot(level, villager), villager.blockPosition());
    }

    /** Find the nearest registered kitchen anchor block to the villager (fallback when no assignment). */
    private @Nullable BlockPos nearestKitchenAnchor(VillagerEntityMCA villager) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos anchor : com.aetherianartificer.townstead.work.site.KitchenWorksites.anchors(villager)) {
            double dist = villager.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
            if (dist < bestDist) { bestDist = dist; best = anchor; }
        }
        return best;
    }

    /** Re-use the existing worksite target if still valid, otherwise pick a nearest non-blacklisted stand/approach. */
    private @Nullable BlockPos currentOrNewWorksiteTarget(
            ServerLevel level, VillagerEntityMCA villager, long gameTime,
            WorkBuildingNav.Snapshot worksiteSnapshotLocal) {
        if (currentWorksiteTarget != null
                && !worksiteTargetFailures.isBlacklisted(currentWorksiteTarget, gameTime)) {
            return currentWorksiteTarget;
        }

        List<BlockPos> standCandidates = worksiteSnapshotLocal.stationStandPositions().values().stream()
                .flatMap(List::stream)
                .filter(pos -> !worksiteTargetFailures.isBlacklisted(pos, gameTime))
                .distinct()
                .sorted(Comparator.comparingDouble(pos ->
                        villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .toList();
        BlockPos reachableStand = WorkBuildingNav.chooseReachableTarget(
                level, villager, standCandidates, CLOSE_ENOUGH, false);
        if (reachableStand != null) {
            currentWorksiteTargetKind = "stand";
            currentWorksiteTarget = reachableStand;
            return currentWorksiteTarget;
        }

        List<BlockPos> fallbackCandidates = worksiteSnapshotLocal.approachTargets().stream()
                .filter(pos -> !worksiteTargetFailures.isBlacklisted(pos, gameTime))
                .sorted(Comparator.comparingDouble(pos ->
                        villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .toList();
        BlockPos reachableFallback = WorkBuildingNav.chooseReachableTarget(
                level, villager, fallbackCandidates, CLOSE_ENOUGH, false);
        if (reachableFallback == null) {
            List<BlockPos> intermediateCandidates = WorkBuildingNav
                    .intermediateApproachTargets(level, villager, worksiteSnapshotLocal).stream()
                    .filter(pos -> !worksiteTargetFailures.isBlacklisted(pos, gameTime))
                    .toList();
            reachableFallback = WorkBuildingNav.chooseReachableTarget(
                    level, villager, intermediateCandidates, CLOSE_ENOUGH, false);
        }
        if (reachableFallback == null) {
            currentWorksiteTarget = null;
            return null;
        }
        currentWorksiteTargetKind = "fallback";
        currentWorksiteTarget = reachableFallback;
        return currentWorksiteTarget;
    }

    /** Peek at inventory for any item matching the first (sole) cutting-board input ingredient. */
    private ItemStack findMatchingCuttingInput(SimpleContainer inv, DiscoveredRecipe recipe) {
        if (recipe == null || recipe.inputs().isEmpty()) return ItemStack.EMPTY;
        Set<ResourceLocation> ids = new HashSet<>(recipe.inputs().get(0).itemIds());
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && ids.contains(id)) {
                ItemStack one = stack.copy();
                one.setCount(1);
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Extract and consume one matching cutting-board input from the villager inventory. */
    private ItemStack takeMatchingCuttingInput(SimpleContainer inv, DiscoveredRecipe recipe) {
        if (recipe == null || recipe.inputs().isEmpty()) return ItemStack.EMPTY;
        Set<ResourceLocation> ids = new HashSet<>(recipe.inputs().get(0).itemIds());
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && ids.contains(id)) {
                return stack.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Find a valid tool stack (e.g. knife) matching the active recipe's requirement. */
    private ItemStack findRecipeTool(SimpleContainer inv) {
        DiscoveredRecipe recipe = fdRecipe();
        if (recipe == null) return ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (WorkRecipeRegistry.recipeToolMatches(recipe, stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** First item id from the first cutting-board input ingredient, for board visuals. */
    private @Nullable ResourceLocation firstCuttingInputId(DiscoveredRecipe recipe) {
        if (recipe == null || recipe.inputs().isEmpty() || recipe.inputs().get(0).itemIds().isEmpty()) return null;
        return recipe.inputs().get(0).itemIds().get(0);
    }

    /** Keep the villager's hands showing the cutting-board tool + input while near the sticky station. */
    private void maintainStickyBoardVisuals(VillagerEntityMCA villager) {
        if (stickyBoardStationAnchor == null || stickyBoardRecipeId == null || stickyBoardInputId == null) return;
        if (stationType != StationType.CUTTING_BOARD || stationAnchor == null || !stationAnchor.equals(stickyBoardStationAnchor)) {
            clearStickyBoardVisuals();
            clearBoardHands(villager);
            return;
        }
        DiscoveredRecipe recipe = fdRecipe();
        if (recipe != null && !stickyBoardRecipeId.equals(recipe.id())) {
            clearStickyBoardVisuals();
            clearBoardHands(villager);
            return;
        }
        if (villager.distanceToSqr(stationAnchor.getX() + 0.5, stationAnchor.getY() + 0.5, stationAnchor.getZ() + 0.5) > NEAR_STATION_DISTANCE_SQ) {
            clearStickyBoardVisuals();
            clearBoardHands(villager);
            return;
        }

        ItemStack tool = findRecipeTool(villager.getInventory());
        Item inputItem = BuiltInRegistries.ITEM.get(stickyBoardInputId);
        ItemStack input = inputItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(inputItem, 1);
        if (!cuttingBoardItemPlaced) {
            setBoardHands(villager, tool, input);
        } else {
            setBoardHands(villager, tool, ItemStack.EMPTY);
            villager.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    /** Clear the cutting-board "sticky" pointers (station/recipe/input) without touching hand slots. */
    private void clearStickyBoardVisuals() {
        stickyBoardStationAnchor = null;
        stickyBoardRecipeId = null;
        stickyBoardInputId = null;
    }

    /** End an in-progress cutting-board session and restore the villager's hand items. */
    private void resetBoardSession(VillagerEntityMCA villager) {
        heldCuttingInput = ItemStack.EMPTY;
        cuttingBoardItemPlaced = false;
        toolWorkActionPerformed = false;
        clearStickyBoardVisuals();
        clearBoardHands(villager);
    }

    /** Save the villager's hand items (first time) then overwrite them with the board tool/input. */
    private void setBoardHands(VillagerEntityMCA villager, ItemStack mainHand, ItemStack offHand) {
        if (!boardHandsVisible) {
            previousBoardMainHand = villager.getMainHandItem().copy();
            previousBoardOffHand = villager.getOffhandItem().copy();
            boardHandsVisible = true;
        }
        villager.setItemInHand(InteractionHand.MAIN_HAND, mainHand.isEmpty() ? ItemStack.EMPTY : mainHand.copy());
        villager.setItemInHand(InteractionHand.OFF_HAND, offHand.isEmpty() ? ItemStack.EMPTY : offHand.copy());
    }

    /** Restore the villager's previously-saved hand items (no-op if nothing was saved). */
    private void clearBoardHands(VillagerEntityMCA villager) {
        if (!boardHandsVisible) return;
        villager.stopUsingItem();
        villager.setItemInHand(InteractionHand.MAIN_HAND, previousBoardMainHand.copy());
        villager.setItemInHand(InteractionHand.OFF_HAND, previousBoardOffHand.copy());
        previousBoardMainHand = ItemStack.EMPTY;
        previousBoardOffHand = ItemStack.EMPTY;
        boardHandsVisible = false;
    }

    private String townstead$navigationMode() {
        if (state == ProducerState.PATH_TO_WORKSITE) return "approach:" + currentWorksiteTargetKind;
        if (state == ProducerState.PATH_TO_STATION) return stationAnchor != null ? "path_to_station" : "search";
        return "station";
    }

    private String townstead$describeAssignedBuilding(ServerLevel level, Building building) {
        if (building == null) return "none";
        BlockPos center = building.getCenter();
        int blockCount = 0;
        for (BlockPos ignored : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
            blockCount++;
        }
        String centerDesc = center == null ? "none" : center.getX() + "," + center.getY() + "," + center.getZ();
        int boxCells = com.aetherianartificer.townstead.work.WorkSiteBounds.workArea(level, building).size();
        return building.getType() + "@" + centerDesc + "[" + blockCount + "b/" + boxCells + "c]";
    }

    private static String townstead$itemDisplayName(ServerLevel level, ResourceLocation itemId) {
        //? if >=1.21 {
        var item = BuiltInRegistries.ITEM.get(itemId);
        //?} else {
        /*var item = BuiltInRegistries.ITEM.get(itemId);
        *///?}
        if (item == null) return itemId.getPath();
        return item.getDefaultInstance().getHoverName().getString();
    }

    /**
     * The buildings this trade's career claims, for the nearest-worksite fallback when no seat
     * is assigned. Read off the career def's own building prefixes, so it is whatever the
     * profession JSON says a workplace is.
     */
    private List<Building> fallbackBuildings(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return List.of();
        var def = com.aetherianartificer.townstead.profession.ProfessionSites.defForTask(spec.taskType());
        if (def == null) return List.of();
        return com.aetherianartificer.townstead.profession.ProfessionCapacity
                .resolveVillage(villager)
                .map(village -> com.aetherianartificer.townstead.profession.ProfessionCapacity
                        .countedBuildings(village, def))
                .orElse(List.of());
    }
}
