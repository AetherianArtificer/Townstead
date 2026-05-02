package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.ai.work.WorkBuildingNav;
import com.aetherianartificer.townstead.ai.work.WorkNavigationMetrics;
import com.aetherianartificer.townstead.ai.work.WorkSiteRef;
import com.aetherianartificer.townstead.ai.work.producer.ProducerBlockedReason;
import com.aetherianartificer.townstead.ai.work.producer.ProducerRecipe;
import com.aetherianartificer.townstead.ai.work.producer.ProducerStationClaims;
import com.aetherianartificer.townstead.ai.work.producer.ProducerStationSessions;
import com.aetherianartificer.townstead.ai.work.producer.ProducerStationState;
import com.aetherianartificer.townstead.ai.work.producer.ProducerWorkTask;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.IngredientResolver;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler.StationSlot;
import com.aetherianartificer.townstead.hunger.ConsumableTargetClaims;
import com.aetherianartificer.townstead.hunger.CookProgressData;
import com.aetherianartificer.townstead.storage.StorageSearchContext;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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

public class CookWorkTask extends ProducerWorkTask {

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

    // Kitchen bounds cache
    private Set<Long> cachedKitchenWorkArea = Set.of();
    private @Nullable BlockPos cachedKitchenWorkAnchor = null;
    private long cachedKitchenWorkUntil = 0L;
    private WorkBuildingNav.Snapshot cachedKitchenSnapshot = WorkBuildingNav.Snapshot.EMPTY;

    public CookWorkTask() {
        super();
    }

    // ── Identity / guards ──

    @Override
    protected boolean isTaskEnabled() {
        return TownsteadConfig.isTownsteadCookEnabled();
    }

    @Override
    protected boolean isEligibleVillager(ServerLevel level, VillagerEntityMCA villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!FarmersDelightCookAssignment.isExternalCookProfession(profession)) return false;
        return FarmersDelightCookAssignment.canVillagerWorkAsCook(level, villager);
    }

    // ── Worksite ──

    @Override
    protected @Nullable WorkSiteRef resolveWorksite(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos reference = activeKitchenReference(villager);
        Set<Long> bounds = activeKitchenBounds(villager, reference);
        return bounds.isEmpty() ? null : WorkSiteRef.building(reference, bounds);
    }

    @Override
    protected boolean isVillagerAtWorksite(ServerLevel level, VillagerEntityMCA villager) {
        return isVillagerInActiveKitchen(villager);
    }

    @Override
    protected @Nullable BlockPos resolveWorksiteTarget(ServerLevel level, VillagerEntityMCA villager, long gameTime, WorkSiteRef site) {
        WorkBuildingNav.Snapshot kitchenSnapshot = activeKitchenSnapshot(level, villager);
        return currentOrNewKitchenWorksiteTarget(villager, gameTime, kitchenSnapshot);
    }

    @Override
    protected BlockPos worksiteReference(VillagerEntityMCA villager) {
        return activeKitchenReference(villager);
    }

    @Override
    protected @Nullable BlockPos refreshStandPosition(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos stationAnchor) {
        if (stationAnchor == null) return null;
        BlockPos refreshed = WorkBuildingNav.nearestStationStand(activeKitchenSnapshot(level, villager), villager, stationAnchor);
        if (refreshed == null) refreshed = StationHandler.findStandingPosition(level, villager, stationAnchor);
        return refreshed;
    }

    // ── Station acquisition ──

    @Override
    protected @Nullable ProducerStationSelection selectStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        WorkBuildingNav.Snapshot kitchenSnapshot = activeKitchenSnapshot(level, villager);
        List<StationSlot> stations = kitchenSnapshot.stations();
        if (stations.isEmpty()) {
            debugChat(level, villager, "ACQUIRE:no stations found in kitchen (" + kitchenBounds.size() + " bounds)");
            setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_WORKSITE, "");
            return null;
        }
        ProducerStationIndex.Selection best = ProducerStationIndex.chooseCookSelection(
                level, villager, kitchenSnapshot, kitchenBounds, abandonedUntilByStation, gameTime, recipeCooldownUntil);
        if (best == null) return null;

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
            activeRecipe = ProducerWorkSupport.findSessionRecipe(ProducerRole.COOK, level, session, stationType);
        }
        return StationHandler.classifyProducerStation(level, villager, stationAnchor, stationType, fdRecipe(), session);
    }

    @Override
    protected boolean cleanupForeignStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || stationType == null) return false;
        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        boolean cleaned = StationHandler.cleanupForeignProducerStation(level, villager, stationAnchor, stationType, kitchenBounds);
        return cleaned && !StationHandler.stationHasAnyContents(level, stationAnchor, stationType);
    }

    // ── Recipe / gather / produce / collect ──

    @Override
    protected @Nullable ProducerRecipe pickRecipe(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || stationType == null) return null;
        if (!StationHandler.isStation(level, stationAnchor)) return null;
        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        DiscoveredRecipe recipe = ProducerWorkSupport.pickRecipe(
                ProducerRole.COOK, level, villager, stationType, stationAnchor, kitchenBounds, recipeCooldownUntil);
        if (recipe == null) {
            int available = ModRecipeRegistry.getRecipesForStation(level, stationType).size();
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
        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        IngredientResolver.PullResult pullResult = IngredientResolver.pullAndConsumeDetailed(
                level, villager, recipe, stationAnchor, stationType, stagedInputs, kitchenBounds);
        if (!pullResult.success()) {
            String recipeName = townstead$itemDisplayName(level, recipe.output());
            String missing = IngredientResolver.describeMissingRequirements(level, villager, recipe, stationAnchor, kitchenBounds);
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
        IngredientResolver.rollbackStagedInputs(level, villager, stationAnchor, stagedInputs);
    }

    @Override
    protected boolean beginProduce(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return false;
        DiscoveredRecipe recipe = fdRecipe();
        produceDoneTick = stationType == StationType.CUTTING_BOARD
                ? gameTime + 4L
                : gameTime + recipe.cookTimeTicks();
        return true;
    }

    @Override
    protected boolean isProduceDone(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return true;
        DiscoveredRecipe recipe = fdRecipe();

        if (stationType == StationType.CUTTING_BOARD) {
            if (!cuttingBoardItemPlaced) {
                // Place phase
                ItemStack actualInput = takeMatchingCuttingInput(villager.getInventory(), recipe);
                if (actualInput.isEmpty()) {
                    debugChat(level, villager, "COOK:missing cutting input");
                    failCuttingBoard(level, villager, gameTime, recipe.output());
                    return false;
                }
                boolean placed = StationHandler.placeCuttingBoardInput(level, stationAnchor, actualInput.copy());
                if (!placed) {
                    ItemStack remainder = villager.getInventory().addItem(actualInput);
                    if (!remainder.isEmpty()) {
                        ItemEntity entity = new ItemEntity(level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder);
                        entity.setPickUpDelay(0);
                        level.addFreshEntity(entity);
                    }
                    debugChat(level, villager, "COOK:cutting board place failed");
                    failCuttingBoard(level, villager, gameTime, recipe.output());
                    return false;
                }
                cuttingBoardItemPlaced = true;
                heldCuttingInput = actualInput.copy();
                produceDoneTick = gameTime + 12L;
                villager.swing(InteractionHand.MAIN_HAND);
                return false;
            }
            // Process phase
            ItemStack knifeStack = findRecipeTool(villager.getInventory());
            boolean processed = StationHandler.processCuttingBoardStoredItem(level, stationAnchor, knifeStack);
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
                && !StationHandler.stationHasCollectibleOutput(level, stationAnchor, ModRecipeRegistry.allOutputIds(level))) {
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
        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
        boolean collected = ProducerOutputHelper.collectSurfaceDrops(level, villager, stationAnchor, kitchenBounds, outputIds);

        if (stationType == StationType.HOT_STATION) {
            ProducerOutputHelper.CollectResult result = ProducerOutputHelper.collectHotStationOutputs(
                    level, villager, stationAnchor, fdRecipe(), kitchenBounds, outputIds, true);
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
        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
        ProducerOutputHelper.finishCollectInventoryOutputs(level, villager, pendingOutput, stationAnchor, kitchenBounds, outputIds);
    }

    @Override
    protected void awardProductionXp(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return;
        int xp = Math.max(1, activeRecipe.tier());
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        CookProgressData.addXp(data, xp, level.getGameTime());
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    // ── Hooks ──

    @Override
    protected void onProduceTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationType == StationType.FIRE_STATION && stationAnchor != null) {
            Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
            List<ItemStack> drops = StationHandler.collectSurfaceCookDrops(level, stationAnchor, outputIds);
            Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
            for (ItemStack drop : drops) {
                IngredientResolver.storeOutputInCookStorage(level, villager, drop, stationAnchor, kitchenBounds);
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
        ProducerOutputHelper.sweepNearbyOutputs(
                level, villager, stationAnchor,
                activeKitchenBounds(villager, activeKitchenReference(villager)),
                ModRecipeRegistry.allOutputIds(level));
    }

    @Override
    protected void onStop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        resetBoardSession(villager);
        stationType = null;
        cachedKitchenWorkArea = Set.of();
        cachedKitchenWorkAnchor = null;
        cachedKitchenWorkUntil = 0L;
        cachedKitchenSnapshot = WorkBuildingNav.Snapshot.EMPTY;
    }

    @Override
    protected void onClearAll(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        resetBoardSession(villager);
        stationType = null;
        cachedKitchenWorkArea = Set.of();
        cachedKitchenWorkAnchor = null;
        cachedKitchenWorkUntil = 0L;
        cachedKitchenSnapshot = WorkBuildingNav.Snapshot.EMPTY;
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
        if (!TownsteadConfig.ENABLE_COOK_REQUEST_CHAT.get()) return;
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
            case NO_WORKSITE -> villager.sendChatToAllAround("dialogue.chat.cook_request.no_kitchen/" + (1 + level.random.nextInt(4)));
            case NO_INGREDIENTS -> {
                if (detail != null && !detail.isBlank()) {
                    villager.sendChatToAllAround("dialogue.chat.cook_request.no_ingredients_item", detail);
                } else {
                    villager.sendChatToAllAround("dialogue.chat.cook_request.no_ingredients/" + (1 + level.random.nextInt(6)));
                }
            }
            case NO_STORAGE -> villager.sendChatToAllAround("dialogue.chat.cook_request.no_storage/" + (1 + level.random.nextInt(4)));
            case UNREACHABLE -> villager.sendChatToAllAround("dialogue.chat.cook_request.unreachable/" + (1 + level.random.nextInt(6)));
            default -> {}
        }
        nextRequestTick = gameTime + Math.max(200, TownsteadConfig.COOK_REQUEST_INTERVAL_TICKS.get());
    }

    private boolean shouldSuppressStaleRequest(
            ServerLevel level, VillagerEntityMCA villager, ProducerBlockedReason reason) {
        return switch (reason) {
            case NO_WORKSITE -> !activeKitchenSnapshot(level, villager).stations().isEmpty();
            case NO_INGREDIENTS -> {
                DiscoveredRecipe recipe = fdRecipe();
                String missing = recipe == null ? null : IngredientResolver.describeMissingRequirements(
                        level, villager, recipe, stationAnchor, cachedKitchenWorkArea);
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
        WorkBuildingNav.Snapshot kitchenSnapshot = activeKitchenSnapshot(level, villager);
        Optional<Building> assignedKitchen = FarmersDelightCookAssignment.assignedKitchen(level, villager);
        String assignedKitchenDesc = assignedKitchen.map(this::townstead$describeAssignedBuilding).orElse("none");
        String navMode = townstead$navigationMode();
        player.sendSystemMessage(Component.literal("[CookDBG:" + cookName + "#" + cookId + "] state=" + state.name()
                + " station=" + station + " anchor=" + anchor + " recipe=" + recipe
                + " doneAt=" + produceDoneTick + " blocked=" + blocked.name()
                + " mode=" + navMode + " site=" + assignedKitchenDesc + " stations=" + kitchenSnapshot.stations().size()
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
    protected void debugChat(ServerLevel level, VillagerEntityMCA villager, String msg) {
        if (!TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (!(level.getNearestPlayer(villager, REQUEST_RANGE) instanceof ServerPlayer player)) return;
        player.sendSystemMessage(Component.literal("[Cook:" + villager.getName().getString() + "] " + msg));
    }

    // ── Subclass helpers ──

    /** Typed downcast — base stores {@code activeRecipe} as {@link ProducerRecipe}. */
    private @Nullable DiscoveredRecipe fdRecipe() {
        return (DiscoveredRecipe) activeRecipe;
    }

    /** Resolve the set of blocks belonging to this villager's active kitchen (assigned or nearest). */
    private Set<Long> activeKitchenBounds(VillagerEntityMCA villager, BlockPos anchor) {
        if (villager.level() instanceof ServerLevel level) {
            Set<Long> assigned = FarmersDelightCookAssignment.assignedKitchenBounds(level, villager);
            if (!assigned.isEmpty()) {
                return assigned;
            }
        }

        List<Building> kitchens = StationHandler.kitchenBuildings(villager);
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

        Set<Long> bounds = new HashSet<>();
        for (BlockPos bp : (Iterable<BlockPos>) selected.getBlockPosStream()::iterator) {
            bounds.add(bp.asLong());
        }
        return bounds;
    }

    /** Cache the expensive kitchen bounds / walkable-interior snapshot for ROOM_BOUNDS_CACHE_TICKS. */
    private void cacheKitchenWorkArea(BlockPos anchor, long gameTime, Set<Long> bounds) {
        cachedKitchenWorkAnchor = anchor == null ? null : anchor.immutable();
        cachedKitchenWorkArea = bounds == null ? Set.of() : bounds;
        cachedKitchenWorkUntil = gameTime + ROOM_BOUNDS_CACHE_TICKS;
    }

    /** Returns a cached WorkBuildingNav.Snapshot for the active kitchen (rebuilds at TTL expiry). */
    private WorkBuildingNav.Snapshot activeKitchenSnapshot(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos anchor = activeKitchenReference(villager);
        long gameTime = level.getGameTime();
        if (anchor != null && cachedKitchenWorkAnchor != null
                && anchor.equals(cachedKitchenWorkAnchor)
                && gameTime <= cachedKitchenWorkUntil
                && !cachedKitchenSnapshot.walkableInterior().isEmpty()) {
            return cachedKitchenSnapshot;
        }
        Set<Long> bounds = activeKitchenBounds(villager, anchor);
        WorkBuildingNav.Snapshot snapshot = WorkBuildingNav.snapshot(level, bounds, anchor);
        cachedKitchenSnapshot = snapshot;
        cacheKitchenWorkArea(anchor, gameTime, snapshot.walkableInterior());
        return snapshot;
    }

    /** Pick the best reference block for kitchen queries: assigned center, station anchor, or nearest. */
    private BlockPos activeKitchenReference(VillagerEntityMCA villager) {
        if (villager.level() instanceof ServerLevel level) {
            Optional<Building> assigned = FarmersDelightCookAssignment.assignedKitchen(level, villager);
            if (assigned.isPresent()) {
                BlockPos center = assigned.get().getCenter();
                if (center != null) return center;
                for (BlockPos bp : (Iterable<BlockPos>) assigned.get().getBlockPosStream()::iterator) {
                    return bp.immutable();
                }
            }
        }
        if (stationAnchor != null) return stationAnchor;
        BlockPos nearest = nearestKitchenAnchor(villager);
        return nearest != null ? nearest : villager.blockPosition();
    }

    /** True when the villager is inside the active kitchen (or standing on one of its station stands). */
    private boolean isVillagerInActiveKitchen(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        return WorkBuildingNav.isInsideOrOnStationStand(activeKitchenSnapshot(level, villager), villager.blockPosition());
    }

    /** Find the nearest registered kitchen anchor block to the villager (fallback when no assignment). */
    private @Nullable BlockPos nearestKitchenAnchor(VillagerEntityMCA villager) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos anchor : StationHandler.kitchenAnchors((ServerLevel) villager.level(), villager)) {
            double dist = villager.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
            if (dist < bestDist) { bestDist = dist; best = anchor; }
        }
        return best;
    }

    /** Re-use the existing worksite target if still valid, otherwise pick a nearest non-blacklisted stand/approach. */
    private @Nullable BlockPos currentOrNewKitchenWorksiteTarget(
            VillagerEntityMCA villager, long gameTime, WorkBuildingNav.Snapshot kitchenSnapshot) {
        if (currentWorksiteTarget != null
                && !worksiteTargetFailures.isBlacklisted(currentWorksiteTarget, gameTime)) {
            return currentWorksiteTarget;
        }

        List<BlockPos> standCandidates = kitchenSnapshot.stationStandPositions().values().stream()
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

        List<BlockPos> fallbackCandidates = kitchenSnapshot.approachTargets().stream()
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
            if (ModRecipeRegistry.recipeToolMatches(recipe, stack)) {
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

    private String townstead$describeAssignedBuilding(Building building) {
        if (building == null) return "none";
        BlockPos center = building.getCenter();
        int blockCount = 0;
        for (BlockPos ignored : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
            blockCount++;
        }
        String centerDesc = center == null ? "none" : center.getX() + "," + center.getY() + "," + center.getZ();
        return building.getType() + "@" + centerDesc + "[" + blockCount + "]";
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
}
