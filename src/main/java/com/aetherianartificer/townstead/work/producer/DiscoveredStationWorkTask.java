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

    /** What this instance serves and says; the rest of the class is trade-neutral. */
    public record Spec(
            String label,
            net.minecraft.resources.ResourceLocation taskType,
            @Nullable net.minecraft.resources.ResourceLocation secondaryTaskType,
            ProducerRole role,
            String historyCounter,
            net.minecraft.resources.ResourceLocation fallbackCareer,
            java.util.function.BooleanSupplier enabled,
            String requestKeyPrefix) {}

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

    // Subclass-only state
    private @Nullable StationType stationType;
    private ItemStack heldCuttingInput = ItemStack.EMPTY;
    private boolean cuttingBoardItemPlaced;
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

    // Kitchen bounds cache
    private Set<Long> cachedWorksiteWorkArea = Set.of();
    private @Nullable BlockPos cachedWorksiteWorkAnchor = null;
    private long cachedWorksiteWorkUntil = 0L;
    private WorkBuildingNav.Snapshot cachedWorksiteSnapshotNav = WorkBuildingNav.Snapshot.EMPTY;
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
        return com.aetherianartificer.townstead.profession.ProfessionSites.assignedSite(level, villager, com.aetherianartificer.townstead.profession.ProfessionSites.defForTask(spec.taskType())).isPresent();
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
    protected @Nullable BlockPos resolveWorksiteTarget(ServerLevel level, VillagerEntityMCA villager, long gameTime, WorkSiteView site) {
        WorkBuildingNav.Snapshot worksiteSnapshotLocal = activeWorksiteSnapshot(level, villager);
        return currentOrNewWorksiteTarget(villager, gameTime, worksiteSnapshotLocal);
    }

    @Override
    protected BlockPos worksiteReference(VillagerEntityMCA villager) {
        return activeWorksiteReference(villager);
    }

    @Override
    protected @Nullable BlockPos refreshStandPosition(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos stationAnchor) {
        if (stationAnchor == null) return null;
        BlockPos refreshed = WorkBuildingNav.nearestStationStand(activeWorksiteSnapshot(level, villager), villager, stationAnchor);
        if (refreshed == null) refreshed = Stations.findStandingPosition(level, villager, stationAnchor);
        return refreshed;
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
            if (!pathWorksites.isEmpty()) {
                best = ProducerStationIndex.chooseForRole(
                        spec.role(), level, villager, snapshot, worksiteBoundsLocal, abandonedUntilByStation,
                        gameTime, recipeCooldownUntil,
                        filter.and(slot -> pathWorksites.contains(slot.blockId())), taskTypes);
                if (best != null) break;
            }
            best = ProducerStationIndex.chooseForRole(
                    spec.role(), level, villager, snapshot, worksiteBoundsLocal, abandonedUntilByStation,
                    gameTime, recipeCooldownUntil, filter, taskTypes);
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
                        ProducerWorkSupport.beveragesOnly(spec.role()),
                        taskTypes)
                .stream()
                .map(com.aetherianartificer.townstead.work.recipe.RecipeSelector.ScoredRecipe::recipe)
                .toList();
    }

    @Override
    protected @Nullable ProducerRecipe pickRecipe(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || stationType == null) return null;
        if (!Stations.isStation(level, stationAnchor)) return null;
        Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
        DiscoveredRecipe recipe = ProducerWorkSupport.pickRecipe(
                spec.role(), level, villager, stationType, stationAnchor, worksiteBoundsLocal,
                recipeCooldownUntil, taskTypes);
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
    protected GatherResult gatherInputs(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null || stationAnchor == null || stationType == null) return GatherResult.fail(null);
        DiscoveredRecipe recipe = fdRecipe();
        Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
        WorkIngredients.PullResult pullResult = WorkIngredients.pullAndConsumeDetailed(
                level, villager, recipe, stationAnchor, stationType, stagedInputs, worksiteBoundsLocal);
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
        DiscoveredRecipe recipe = fdRecipe();
        if (com.aetherianartificer.townstead.work.station.StationProtocols.handles(level, stationAnchor)) {
            // The physical hand-off: place the work block and/or push the gathered items from
            // the villager's inventory into the station, the way a player's hands would.
            Set<Long> bounds = activeWorksiteBounds(level, villager);
            if (!com.aetherianartificer.townstead.work.station.StationProtocols.insert(
                    level, villager, stationAnchor, recipe, bounds)) {
                debugChat(level, villager, "COOK:station refused inputs");
                return false;
            }
        }
        produceDoneTick = stationType == StationType.CUTTING_BOARD
                ? gameTime + 4L
                : gameTime + recipe.cookTimeTicks();
        return true;
    }

    @Override
    protected boolean isProduceDone(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return true;
        DiscoveredRecipe recipe = fdRecipe();

        if (com.aetherianartificer.townstead.work.station.Workstations
                .v2ByState(level.getBlockState(stationAnchor)) != null
                && com.aetherianartificer.townstead.work.station.StationProtocols.handles(level, stationAnchor)) {
            com.aetherianartificer.townstead.work.station.StationProtocols.work(
                    level, villager, stationAnchor, recipe);
            return com.aetherianartificer.townstead.work.station.StationProtocols.isReady(
                    level, villager, stationAnchor, recipe);
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
        Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
        Set<ResourceLocation> outputIds = WorkRecipeRegistry.allOutputIds(level);
        boolean collected = ProducerOutputHelper.collectSurfaceDrops(level, villager, stationAnchor, worksiteBoundsLocal, outputIds);

        if (com.aetherianartificer.townstead.work.station.StationProtocols.handles(level, stationAnchor)) {
            boolean sweptAndDrained = sweptProducedOutput
                    && com.aetherianartificer.townstead.work.station.StationProtocols.isIdle(
                            level, stationAnchor, fdRecipe());
            boolean harvested = com.aetherianartificer.townstead.work.station.StationProtocols.collect(
                    level, villager, stationAnchor, fdRecipe(), worksiteBoundsLocal);
            // Harvest throws the product into the world (the peel lift); sweep it up.
            collected |= ProducerOutputHelper.collectSurfaceDrops(level, villager, stationAnchor, worksiteBoundsLocal, outputIds);
            if (harvested || collected || sweptAndDrained) {
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
        // Credit the villager's own canonical career (a cook-family career levels itself;
        // careers are flat, XP never propagates), falling back to Cook for aliases and
        // professions without defs.
        ResourceLocation careerId = spec.fallbackCareer();
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (com.aetherianartificer.townstead.work.WorkTaskDeclarations.professionDeclares(profession, taskTypes)) {
            ResourceLocation canonical = com.aetherianartificer.townstead.profession.def.ProfessionDefs
                    .canonicalId(BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession));
            if (canonical != null) careerId = canonical;
        }
        // The declared task may name its own counter; the spec's is the trade default.
        String counter = spec.historyCounter();
        com.aetherianartificer.townstead.profession.def.WorkTaskDef declared =
                com.aetherianartificer.townstead.work.WorkTaskDeclarations.first(villager, spec.taskType());
        if (declared != null && declared.historyCounter() != null) counter = declared.historyCounter();
        com.aetherianartificer.townstead.profession.career.CareerProgression.completeWork(
                villager, careerId, xp, level.getGameTime(),
                counter, activeRecipe.output(), "dish", activeRecipe.tier(),
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
    protected void onProduceTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationType == StationType.FIRE_STATION && stationAnchor != null) {
            Set<ResourceLocation> outputIds = WorkRecipeRegistry.allOutputIds(level);
            Set<Long> worksiteBoundsLocal = activeWorksiteBounds(level, villager);
            List<ItemStack> drops = com.aetherianartificer.townstead.work.station.StationDropOutputs.collectWithinWorksite(
                    level, stationAnchor, outputIds, worksiteBoundsLocal);
            if (!drops.isEmpty()) sweptProducedOutput = true;
            for (ItemStack drop : drops) {
                WorkIngredients.storeOutputInWorksiteStorage(level, villager, drop, stationAnchor, worksiteBoundsLocal);
                if (!drop.isEmpty()) {
                    ItemStack remainder = villager.getInventory().addItem(drop);
                    if (!remainder.isEmpty()) {
                        ItemEntity entity = new ItemEntity(level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder);
                        entity.setPickUpDelay(0);
                        level.addFreshEntity(entity);
                    }
                }
            }
        }
    }

    @Override
    protected boolean mustWaitBeyondCollectTimeout(ServerLevel level, VillagerEntityMCA villager) {
        return stationType == StationType.HOT_STATION || stationType == StationType.CUTTING_BOARD;
    }

    @Override
    protected void onSessionRefresh(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || activeRecipe == null) return;
        DiscoveredRecipe recipe = fdRecipe();
        ProducerStationSessions.beginOrRefresh(
                level, villager.getUUID(), stationAnchor,
                recipe.id(), recipe.output(), recipe.outputCount(),
                stagedInputs, gameTime + STATION_SESSION_LEASE_TICKS);
    }

    @Override
    protected void onSessionRelease(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos pos, long gameTime) {
        if (pos == null) return;
        ProducerStationSessions.release(level, villager.getUUID(), pos);
    }

    @Override
    protected void onOpportunisticSweep(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        ResourceLocation watchedOutput = activeRecipe == null ? null : activeRecipe.output();
        boolean sweptActiveOutput = ProducerOutputHelper.sweepWorksiteOutputs(
                level, villager, stationAnchor, watchedOutput,
                activeWorksiteBounds(level, villager), WorkRecipeRegistry.allOutputIds(level));
        if (stationType == StationType.FIRE_STATION && sweptActiveOutput) {
            sweptProducedOutput = true;
        }
    }

    @Override
    protected void onStop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        resetBoardSession(villager);
        stationType = null;
        lastAppraisal = null;
        sweptProducedOutput = false;
        cachedWorksiteWorkArea = Set.of();
        cachedWorksiteWorkAnchor = null;
        cachedWorksiteWorkUntil = 0L;
        cachedWorksiteSnapshotNav = WorkBuildingNav.Snapshot.EMPTY;
        scopedSnapshots.clear();
    }

    @Override
    protected void onClearAll(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        resetBoardSession(villager);
        stationType = null;
        lastAppraisal = null;
        sweptProducedOutput = false;
        cachedWorksiteWorkArea = Set.of();
        cachedWorksiteWorkAnchor = null;
        cachedWorksiteWorkUntil = 0L;
        cachedWorksiteSnapshotNav = WorkBuildingNav.Snapshot.EMPTY;
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
        boolean barista = spec.role() == ProducerRole.BARISTA;
        boolean butcher = spec.role() == ProducerRole.BUTCHER;
        if (barista ? !TownsteadConfig.isBaristaRequestChatEnabled()
                : butcher ? !TownsteadConfig.ENABLE_FARMER_REQUEST_CHAT.get()
                : !TownsteadConfig.ENABLE_COOK_REQUEST_CHAT.get()) return;
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
        switch (reason) {
            case NO_WORKSITE -> villager.sendChatToAllAround(spec.requestKeyPrefix()
                    + (barista ? "no_cafe/" : butcher ? "no_smoker/" : "no_kitchen/")
                    + (1 + level.random.nextInt(4)));
            case NO_INGREDIENTS -> {
                if (butcher) {
                    villager.sendChatToAllAround(spec.requestKeyPrefix() + "no_input/"
                            + (1 + level.random.nextInt(6)));
                } else if (detail != null && !detail.isBlank()) {
                    villager.sendChatToAllAround(spec.requestKeyPrefix() + "no_ingredients_item", detail);
                } else {
                    villager.sendChatToAllAround(spec.requestKeyPrefix() + "no_ingredients/"
                            + (1 + level.random.nextInt(barista ? 4 : 6)));
                }
            }
            case NO_STORAGE -> villager.sendChatToAllAround(spec.requestKeyPrefix()
                    + (butcher ? "output_blocked/" : "no_storage/") + (1 + level.random.nextInt(4)));
            case UNREACHABLE -> villager.sendChatToAllAround(spec.requestKeyPrefix() + "unreachable/"
                    + (1 + level.random.nextInt(barista ? 4 : 6)));
            default -> {}
        }
        nextRequestTick = gameTime + Math.max(200, barista
                ? TownsteadConfig.BARISTA_REQUEST_INTERVAL_TICKS.get()
                : butcher ? TownsteadConfig.FARMER_REQUEST_INTERVAL_TICKS.get()
                : TownsteadConfig.COOK_REQUEST_INTERVAL_TICKS.get());
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
        String station = stationType == null ? "none" : stationType.name().toLowerCase();
        String idleInfo = gameTime < idleUntilTick ? " idle=" + (idleUntilTick - gameTime) : "";
        StorageSearchContext.Snapshot storageSnapshot = StorageSearchContext.Profiler.snapshot();
        VillageAiBudget.Snapshot budgetSnapshot = VillageAiBudget.snapshot();
        WorkNavigationMetrics.Snapshot navSnapshot = WorkNavigationMetrics.snapshot();
        ConsumableTargetClaims.Snapshot claimSnapshot = ConsumableTargetClaims.snapshot();
        WorkBuildingNav.Snapshot worksiteSnapshotLocal = activeWorksiteSnapshot(level, villager);
        String assignedSiteDesc = com.aetherianartificer.townstead.profession.ProfessionSites.assignedSite(level, villager, com.aetherianartificer.townstead.profession.ProfessionSites.defForTask(spec.taskType()))
                .map(site -> site.building() != null
                        ? townstead$describeAssignedBuilding(level, site.building())
                        : "post@" + site.post().getX() + "," + site.post().getY() + "," + site.post().getZ())
                .orElse("none");
        String navMode = townstead$navigationMode();
        player.sendSystemMessage(Component.literal("[" + spec.label() + "DBG:" + cookName + "#" + cookId + "] state=" + state.name()
                + " station=" + station + " anchor=" + anchor + " recipe=" + recipe
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

    /**
     * The cook's assigned site, resolved once. Every step below used to ask for this twice — once
     * to find the reference block and again to find the bounds — and each ask rebuilds the village's
     * whole cook-site list and re-sorts its residents. Threading one resolution through halves that
     * with no caching involved.
     */
    private @Nullable com.aetherianartificer.townstead.profession.ProfessionSites.Site resolveAssignedSite(
            ServerLevel level, VillagerEntityMCA villager) {
        return com.aetherianartificer.townstead.profession.ProfessionSites.assignedSite(level, villager, com.aetherianartificer.townstead.profession.ProfessionSites.defForTask(spec.taskType())).orElse(null);
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
            VillagerEntityMCA villager, long gameTime, WorkBuildingNav.Snapshot worksiteSnapshotLocal) {
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
        if (!standCandidates.isEmpty()) {
            currentWorksiteTargetKind = "stand";
            currentWorksiteTarget = standCandidates.get(0);
            return currentWorksiteTarget;
        }

        List<BlockPos> fallbackCandidates = worksiteSnapshotLocal.approachTargets().stream()
                .filter(pos -> !worksiteTargetFailures.isBlacklisted(pos, gameTime))
                .sorted(Comparator.comparingDouble(pos ->
                        villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .toList();
        if (fallbackCandidates.isEmpty()) {
            currentWorksiteTarget = null;
            return null;
        }
        currentWorksiteTargetKind = "fallback";
        currentWorksiteTarget = fallbackCandidates.get(0);
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
