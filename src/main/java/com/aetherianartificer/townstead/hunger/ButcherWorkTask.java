package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.butchery.ButcheryCompat;
import com.aetherianartificer.townstead.compat.butchery.CarcassWorkTask;
import com.aetherianartificer.townstead.compat.butchery.GrinderWorkTask;
import com.aetherianartificer.townstead.ai.work.WorkNavigationMetrics;
import com.aetherianartificer.townstead.ai.work.WorkSiteRef;
import com.aetherianartificer.townstead.ai.work.WorkTarget;
import com.aetherianartificer.townstead.ai.work.producer.ProducerBlockedReason;
import com.aetherianartificer.townstead.ai.work.producer.ProducerRecipe;
import com.aetherianartificer.townstead.ai.work.producer.ProducerStationClaims;
import com.aetherianartificer.townstead.ai.work.producer.ProducerStationState;
import com.aetherianartificer.townstead.ai.work.producer.ProducerWorkTask;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
//? if >=1.21 {
import net.minecraft.world.item.crafting.SingleRecipeInput;
//?}
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import javax.annotation.Nullable;
import java.util.List;

public class ButcherWorkTask extends ProducerWorkTask {
    private static final int ANCHOR_SEARCH_RADIUS = 24;
    private static final int VERTICAL_RADIUS = 3;
    private static final int WORK_RADIUS = 12;
    private static final int REQUEST_RANGE = 24;
    private static final int SMOKE_WAIT_TICKS = 20;
    private static final int VANILLA_SMOKER_COOK_TICKS = 100;
    private static final long SMOKER_ANCHOR_CACHE_TICKS = 40L;

    // Subclass-only state
    private @Nullable BlockPos cachedSmokerAnchor;
    private long cachedSmokerUntil = Long.MIN_VALUE;
    private String unsupportedItemName = "";

    public ButcherWorkTask() {
        super();
    }

    // ── Identity / guards ──

    @Override
    protected boolean isTaskEnabled() {
        return true;
    }

    @Override
    protected boolean isEligibleVillager(ServerLevel level, VillagerEntityMCA villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession != VillagerProfession.BUTCHER) return false;
        // Yield the smoker while there's higher-priority carcass OR grinder
        // work pending. Without this, the smoker keeps WALK_TARGET held
        // continuously and blocks those tasks (both require
        // WALK_TARGET=VALUE_ABSENT) from ever starting even when a fresh
        // kill is hanging next door or the grinder is ready to turn raw
        // pork into sausages.
        if (CarcassWorkTask.hasPendingWork(level, villager)) return false;
        if (GrinderWorkTask.hasPendingWork(level, villager)) return false;
        // Don't path to the smoker if there's nothing to smoke. selectStation
        // returns the smoker before pickRecipe runs, so without this gate the
        // butcher walks the full distance to the smoker, blocks NO_RECIPE,
        // ping-pongs PATH_TO_STATION ↔ SELECT_RECIPE, and reads as "walking
        // to the butcher shop and standing there" between other tasks.
        BlockPos smoker = activeSmokerAnchor(level, villager);
        if (smoker == null) return false;
        if (!smokerNeedsAttention(level, smoker)
                && !ButcherSupplyManager.hasRawInputAvailable(level, villager, smoker)) {
            return false;
        }
        return true;
    }

    // ── Worksite ──

    @Override
    protected @Nullable WorkSiteRef resolveWorksite(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos smoker = activeSmokerAnchor(level, villager);
        if (smoker == null) return null;
        return WorkSiteRef.zone(smoker, WORK_RADIUS, VERTICAL_RADIUS);
    }

    @Override
    protected boolean isVillagerAtWorksite(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos smoker = activeSmokerAnchor(level, villager);
        if (smoker == null) return false;
        if (!villager.blockPosition().closerThan(smoker, WORK_RADIUS)) return false;
        // Ensure we're not standing on the smoker itself (old ensureOffSmoker behavior).
        return !villager.blockPosition().equals(smoker);
    }

    @Override
    protected @Nullable BlockPos resolveWorksiteTarget(ServerLevel level, VillagerEntityMCA villager, long gameTime, WorkSiteRef site) {
        BlockPos smoker = activeSmokerAnchor(level, villager);
        if (smoker == null) return null;
        // If we're already within radius but standing on the smoker block, redirect to the stand position.
        if (villager.blockPosition().closerThan(smoker, WORK_RADIUS) && villager.blockPosition().equals(smoker)) {
            BlockPos stand = findWorkStandPos(level, villager, smoker);
            if (stand != null) {
                currentWorksiteTargetKind = "approach";
                return stand;
            }
        }
        if (currentWorksiteTarget != null
                && !worksiteTargetFailures.isBlacklisted(currentWorksiteTarget, gameTime)) {
            return currentWorksiteTarget;
        }
        currentWorksiteTargetKind = "approach";
        return smoker;
    }

    @Override
    protected BlockPos worksiteReference(VillagerEntityMCA villager) {
        BlockPos smoker = stationAnchor != null ? stationAnchor : cachedSmokerAnchor;
        return smoker != null ? smoker : villager.blockPosition();
    }

    @Override
    protected @Nullable BlockPos refreshStandPosition(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos stationAnchor) {
        if (stationAnchor == null) return null;
        return ButcherWorkIndex.nearestStandPos(level, villager, stationAnchor);
    }

    // ── Station acquisition ──

    @Override
    protected @Nullable ProducerStationSelection selectStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BlockPos smoker = activeSmokerAnchor(level, villager);
        if (smoker == null || !level.getBlockState(smoker).is(Blocks.SMOKER)) {
            invalidateSmokerCache();
            setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_WORKSITE, null);
            return null;
        }
        BlockPos stand = findWorkStandPos(level, villager, smoker);
        if (stand == null) {
            setBlocked(level, villager, gameTime, ProducerBlockedReason.UNREACHABLE, null);
            return null;
        }
        // Recipe will be chosen lazily in pickRecipe; selection may carry a null recipe here.
        return new ProducerStationSelection(smoker, stand, null);
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
        SmokerBlockEntity smoker = getSmoker(level);
        if (smoker == null) return ProducerStationState.BLOCKED;

        ItemStack inputSlot = smoker.getItem(0);
        ItemStack fuelSlot = smoker.getItem(1);
        ItemStack outputSlot = smoker.getItem(2);

        if (!outputSlot.isEmpty()) return ProducerStationState.FINISHED_OUTPUT;

        boolean inputBlocker = !inputSlot.isEmpty() && ButcherSupplyManager.isSmokerBlockerInput(inputSlot, level);
        boolean fuelBlocker = !fuelSlot.isEmpty() && !ButcherSupplyManager.isFuel(fuelSlot);
        if (inputBlocker || fuelBlocker) {
            if (inputBlocker) unsupportedItemName = inputSlot.getHoverName().getString();
            else unsupportedItemName = fuelSlot.getHoverName().getString();
            return ProducerStationState.FOREIGN_CONTENTS;
        }

        if (!inputSlot.isEmpty() && !fuelSlot.isEmpty()) return ProducerStationState.OWNED_STAGED;

        return ProducerStationState.EMPTY_READY;
    }

    @Override
    protected boolean cleanupForeignStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        SmokerBlockEntity smoker = getSmoker(level);
        if (smoker == null) return false;

        SlotClearResult inputResult = relocateSmokerSlotIfInvalid(level, villager, smoker, 0,
                stack -> !ButcherSupplyManager.isSmokerBlockerInput(stack, level));
        SlotClearResult fuelResult = relocateSmokerSlotIfInvalid(level, villager, smoker, 1,
                ButcherSupplyManager::isFuel);
        if (inputResult == SlotClearResult.CLEARED || fuelResult == SlotClearResult.CLEARED) smoker.setChanged();

        if (inputResult == SlotClearResult.FAILED || fuelResult == SlotClearResult.FAILED) {
            setBlocked(level, villager, gameTime, ProducerBlockedReason.UNSUPPORTED_RECIPE, unsupportedItemName);
            return false;
        }

        boolean hadBlocker = inputResult != SlotClearResult.NO_BLOCKER || fuelResult != SlotClearResult.NO_BLOCKER;
        if (hadBlocker) {
            unsupportedItemName = "";
            return true;
        }
        return false;
    }

    // ── Recipe / gather / produce / collect ──

    @Override
    protected @Nullable ProducerRecipe pickRecipe(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) return null;
        if (!level.getBlockState(stationAnchor).is(Blocks.SMOKER)) return null;

        // Find a raw input: either in inventory or pullable from nearby storage.
        ItemStack sample = findInventoryRawInput(villager.getInventory(), level);
        if (sample.isEmpty()) {
            if (!ButcherSupplyManager.pullRawInput(level, villager, stationAnchor)) {
                return null;
            }
            sample = findInventoryRawInput(villager.getInventory(), level);
            if (sample.isEmpty()) return null;
        }
        return VanillaSmelterRecipe.of(level, sample);
    }

    @Override
    protected GatherResult gatherInputs(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        SmokerBlockEntity smoker = getSmoker(level);
        if (smoker == null) return GatherResult.fail("missing smoker");

        // Slot 0: raw input.
        if (smoker.getItem(0).isEmpty()) {
            if (!doFetchInput(level, villager, smoker)) {
                if (!ButcherSupplyManager.pullRawInput(level, villager, stationAnchor)
                        || !doFetchInput(level, villager, smoker)) {
                    setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_INGREDIENTS, null);
                    return GatherResult.fail("missing input");
                }
            }
        }

        // Slot 1: fuel.
        if (smoker.getItem(1).isEmpty()) {
            if (!doFetchFuel(villager, smoker)) {
                if (!ButcherSupplyManager.pullFuel(level, villager, stationAnchor)
                        || !doFetchFuel(villager, smoker)) {
                    setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_FUEL, null);
                    return GatherResult.fail("missing fuel");
                }
            }
        }

        return GatherResult.ok();
    }

    @Override
    protected void rollbackGather(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Vanilla smoker doesn't need rollback; anything staged already lives in the block entity.
    }

    @Override
    protected boolean beginProduce(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return false;
        // The vanilla smoker block entity drives the real cook; our produceDoneTick is a floor for re-checks.
        produceDoneTick = gameTime + SMOKE_WAIT_TICKS;
        return true;
    }

    @Override
    protected boolean isProduceDone(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        SmokerBlockEntity smoker = getSmoker(level);
        if (smoker == null) return false;
        return !smoker.getItem(2).isEmpty();
    }

    @Override
    protected CollectResult collectFromStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        SmokerBlockEntity smoker = getSmoker(level);
        if (smoker == null) return CollectResult.none();
        ItemStack output = smoker.getItem(2);
        if (output.isEmpty()) return CollectResult.none();

        ItemStack moving = output.copy();
        ItemStack remainder = villager.getInventory().addItem(moving);
        smoker.setItem(2, remainder);
        smoker.setChanged();
        return CollectResult.ofCollected();
    }

    @Override
    protected void storeOutputs(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Output stays in villager inventory; ButcherDeliveryTask walks it
        // to storage. No more teleporty inventory-to-chest transfer from
        // within the smoker task itself.
        //
        // If cooked output is still sitting in the smoker's slot 2 (e.g.
        // the villager got interrupted before collecting), pull it into
        // inventory now. The delivery task will move it onward.
        SmokerBlockEntity smoker = getSmoker(level);
        if (smoker == null) return;
        ItemStack leftover = smoker.getItem(2);
        if (leftover.isEmpty()) return;
        ItemStack moving = leftover.copy();
        ItemStack remaining = villager.getInventory().addItem(moving);
        if (remaining.getCount() != leftover.getCount()) {
            smoker.setItem(2, remaining);
            smoker.setChanged();
        }
    }

    @Override
    protected void awardProductionXp(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // XP only meaningful when Butchery is loaded; it gates the Butchery
        // trade catalog. Without Butchery there are no Butchery-specific trades
        // to unlock, so the data layer stays dormant.
        if (!ButcheryCompat.isLoaded()) return;
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        ButcherProgressData.addXp(data, 1, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    // ── Hooks ──

    @Override
    protected boolean mustWaitBeyondCollectTimeout(ServerLevel level, VillagerEntityMCA villager) {
        // Vanilla smoker finishes on its own; waiting doesn't help recover an incomplete cook any further.
        return false;
    }

    @Override
    protected void onStop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        invalidateSmokerCache();
        unsupportedItemName = "";
    }

    @Override
    protected void onClearAll(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        invalidateSmokerCache();
        unsupportedItemName = "";
    }

    @Override
    protected void announceBlocked(ServerLevel level, VillagerEntityMCA villager, long gameTime,
                                   ProducerBlockedReason reason, @Nullable String detail) {
        HungerData.ButcherBlockedReason mapped = toHungerReason(reason);
        syncBlockedReasonIfChanged(level, villager, mapped);

        if (mapped == HungerData.ButcherBlockedReason.NONE) return;
        if (!TownsteadConfig.ENABLE_FARMER_REQUEST_CHAT.get()) return;
        if (gameTime < nextRequestTick) return;
        if (level.getNearestPlayer(villager, REQUEST_RANGE) == null) {
            nextRequestTick = gameTime + 200;
            return;
        }
        if (reason == ProducerBlockedReason.UNREACHABLE
                && !shouldAnnounceBlockedNavigation(level, villager, activeWorkTarget(level, villager))) {
            return;
        }

        int interval = Math.max(200, TownsteadConfig.FARMER_REQUEST_INTERVAL_TICKS.get());

        if (mapped == HungerData.ButcherBlockedReason.UNSUPPORTED_RECIPE) {
            String item = (detail != null && !detail.isBlank()) ? detail
                    : (unsupportedItemName == null || unsupportedItemName.isBlank() ? "that" : unsupportedItemName);
            villager.sendChatToAllAround("dialogue.chat.butcher_request.unsupported_recipe_item", item);
            villager.getLongTermMemory().remember("townstead.butcher_request.any");
            villager.getLongTermMemory().remember("townstead.butcher_request." + mapped.id());
            nextRequestTick = gameTime + interval;
            return;
        }

        String key = switch (mapped) {
            case NO_SMOKER -> "dialogue.chat.butcher_request.no_smoker/" + (1 + level.random.nextInt(4));
            case NO_INPUT -> "dialogue.chat.butcher_request.no_input/" + (1 + level.random.nextInt(6));
            case NO_FUEL -> "dialogue.chat.butcher_request.no_fuel/" + (1 + level.random.nextInt(6));
            case OUTPUT_BLOCKED -> "dialogue.chat.butcher_request.output_blocked/" + (1 + level.random.nextInt(4));
            case UNREACHABLE -> "dialogue.chat.butcher_request.unreachable/" + (1 + level.random.nextInt(6));
            case OUT_OF_SCOPE -> "dialogue.chat.butcher_request.out_of_scope/" + (1 + level.random.nextInt(4));
            case NO_VALID_TARGET -> "dialogue.chat.butcher_request.no_valid_target/" + (1 + level.random.nextInt(4));
            default -> null;
        };
        if (key == null) return;

        villager.sendChatToAllAround(key);
        villager.getLongTermMemory().remember("townstead.butcher_request.any");
        villager.getLongTermMemory().remember("townstead.butcher_request." + mapped.id());
        nextRequestTick = gameTime + interval;
    }

    @Override
    protected void debugTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (gameTime < nextDebugTick) return;
        if (!(level.getNearestPlayer(villager, REQUEST_RANGE) instanceof ServerPlayer player)) return;
        String name = villager.getName().getString();
        String id = villager.getUUID().toString();
        if (id.length() > 8) id = id.substring(0, 8);
        WorkSiteRef site = activeWorkSite(level, villager);
        WorkTarget target = activeWorkTarget(level, villager);
        WorkNavigationMetrics.Snapshot navSnapshot = WorkNavigationMetrics.snapshot();
        String anchor = stationAnchor == null ? "none" : stationAnchor.getX() + "," + stationAnchor.getY() + "," + stationAnchor.getZ();
        String navMode = stationAnchor == null ? "search" : (target == null ? "search" : "station");
        player.sendSystemMessage(Component.literal("[ButcherDBG:" + name + "#" + id + "] state=" + state.name()
                + " anchor=" + anchor
                + " target=" + (target == null ? "none" : target.describe())
                + " blocked=" + blocked.name()
                + " mode=" + navMode + " site=" + (site == null ? "none" : site.describe())
                + " nav=" + navSnapshot.snapshotRebuilds() + "/" + navSnapshot.pathAttempts()
                + "/" + navSnapshot.pathSuccesses() + "/" + navSnapshot.pathFailures()));
        nextDebugTick = gameTime + 100L;
    }

    // ── Subclass helpers ──

    /** Cached nearest-smoker lookup; invalidated when the smoker block is missing or destroyed. */
    private @Nullable BlockPos activeSmokerAnchor(ServerLevel level, VillagerEntityMCA villager) {
        long gameTime = level.getGameTime();
        if (cachedSmokerAnchor != null && gameTime <= cachedSmokerUntil) {
            if (level.getBlockState(cachedSmokerAnchor).is(Blocks.SMOKER)) return cachedSmokerAnchor;
            invalidateSmokerCache();
        }
        BlockPos fresh = ButcherWorkIndex.nearestSmoker(level, villager, ANCHOR_SEARCH_RADIUS, VERTICAL_RADIUS);
        if (fresh != null) {
            cachedSmokerAnchor = fresh.immutable();
            cachedSmokerUntil = gameTime + SMOKER_ANCHOR_CACHE_TICKS;
            return cachedSmokerAnchor;
        }
        invalidateSmokerCache();
        return null;
    }

    private void invalidateSmokerCache() {
        cachedSmokerAnchor = null;
        cachedSmokerUntil = Long.MIN_VALUE;
    }

    private @Nullable BlockPos findWorkStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        return ButcherWorkIndex.nearestStandPos(level, villager, anchor);
    }

    private @Nullable SmokerBlockEntity getSmoker(ServerLevel level) {
        if (stationAnchor == null) return null;
        if (!(level.getBlockEntity(stationAnchor) instanceof SmokerBlockEntity smoker)) return null;
        return smoker;
    }

    private boolean smokerNeedsAttention(ServerLevel level, BlockPos smokerPos) {
        if (!(level.getBlockEntity(smokerPos) instanceof SmokerBlockEntity smoker)) return false;
        ItemStack input = smoker.getItem(0);
        ItemStack fuel = smoker.getItem(1);
        ItemStack output = smoker.getItem(2);
        if (!output.isEmpty()) return true;
        if (!input.isEmpty()) return true;
        return !fuel.isEmpty() && !ButcherSupplyManager.isFuel(fuel);
    }

    private ItemStack findInventoryRawInput(SimpleContainer inv, ServerLevel level) {
        int slot = ButcherSupplyManager.findRawInputSlot(inv, level);
        if (slot < 0) return ItemStack.EMPTY;
        ItemStack stack = inv.getItem(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        //? if >=1.21 {
        return stack.copyWithCount(1);
        //?} else {
        /*ItemStack one = stack.copy(); one.setCount(1); return one;
        *///?}
    }

    private boolean doFetchInput(ServerLevel level, VillagerEntityMCA villager, SmokerBlockEntity smoker) {
        if (!smoker.getItem(0).isEmpty()) return false;
        int slot = ButcherSupplyManager.findRawInputSlot(villager.getInventory(), level);
        if (slot < 0) return false;
        ItemStack stack = villager.getInventory().getItem(slot);
        if (stack.isEmpty()) return false;
        //? if >=1.21 {
        smoker.setItem(0, stack.copyWithCount(1));
        //?} else {
        /*ItemStack one0 = stack.copy(); one0.setCount(1); smoker.setItem(0, one0);
        *///?}
        stack.shrink(1);
        smoker.setChanged();
        return true;
    }

    private boolean doFetchFuel(VillagerEntityMCA villager, SmokerBlockEntity smoker) {
        if (!smoker.getItem(1).isEmpty()) return false;
        int slot = ButcherSupplyManager.findFuelSlot(villager.getInventory());
        if (slot < 0) return false;
        ItemStack stack = villager.getInventory().getItem(slot);
        if (stack.isEmpty()) return false;
        //? if >=1.21 {
        smoker.setItem(1, stack.copyWithCount(1));
        //?} else {
        /*ItemStack one1 = stack.copy(); one1.setCount(1); smoker.setItem(1, one1);
        *///?}
        stack.shrink(1);
        smoker.setChanged();
        return true;
    }

    private SlotClearResult relocateSmokerSlotIfInvalid(
            ServerLevel level,
            VillagerEntityMCA villager,
            SmokerBlockEntity smoker,
            int slotIndex,
            java.util.function.Predicate<ItemStack> isValid
    ) {
        ItemStack stack = smoker.getItem(slotIndex);
        if (stack.isEmpty() || isValid.test(stack)) return SlotClearResult.NO_BLOCKER;

        ItemStack moving = stack.copy();
        smoker.setItem(slotIndex, ItemStack.EMPTY);

        NearbyItemSources.insertIntoNearbyStorage(level, villager, moving, 16, 3, stationAnchor);
        if (!moving.isEmpty()) {
            ItemStack remainder = villager.getInventory().addItem(moving);
            if (!remainder.isEmpty()) {
                smoker.setItem(slotIndex, remainder);
                return SlotClearResult.FAILED;
            }
        }
        return SlotClearResult.CLEARED;
    }

    private void syncBlockedReasonIfChanged(ServerLevel level, VillagerEntityMCA villager, HungerData.ButcherBlockedReason mapped) {
        //? if neoforge {
        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag hunger = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        if (HungerData.getButcherBlockedReason(hunger) == mapped) return;

        HungerData.setButcherBlockedReason(hunger, mapped);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, hunger);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", hunger);
        *///?}

        //? if neoforge {
        PacketDistributor.sendToPlayersTrackingEntity(villager, new ButcherStatusSyncPayload(villager.getId(), mapped.id()));
        //?} else if forge {
        /*TownsteadNetwork.sendToTrackingEntity(villager, new ButcherStatusSyncPayload(villager.getId(), mapped.id()));
        *///?}

        if (mapped == HungerData.ButcherBlockedReason.NONE) {
            nextRequestTick = 0;
        } else {
            long soonest = level.getGameTime() + 40;
            if (nextRequestTick < soonest) nextRequestTick = soonest;
        }
    }

    private static HungerData.ButcherBlockedReason toHungerReason(ProducerBlockedReason reason) {
        return switch (reason) {
            case NONE -> HungerData.ButcherBlockedReason.NONE;
            case NO_WORKSITE -> HungerData.ButcherBlockedReason.NO_SMOKER;
            case NO_INGREDIENTS -> HungerData.ButcherBlockedReason.NO_INPUT;
            case NO_FUEL -> HungerData.ButcherBlockedReason.NO_FUEL;
            case UNREACHABLE -> HungerData.ButcherBlockedReason.UNREACHABLE;
            case OUTPUT_BLOCKED -> HungerData.ButcherBlockedReason.OUTPUT_BLOCKED;
            case UNSUPPORTED_RECIPE -> HungerData.ButcherBlockedReason.UNSUPPORTED_RECIPE;
            case NO_RECIPE -> HungerData.ButcherBlockedReason.NO_VALID_TARGET;
            case NO_STORAGE -> HungerData.ButcherBlockedReason.OUTPUT_BLOCKED;
        };
    }

    private enum SlotClearResult { NO_BLOCKER, CLEARED, FAILED }

    /** Synthetic ProducerRecipe backing a vanilla smoker input. Output key is used for per-recipe cooldowns. */
    private record VanillaSmelterRecipe(
            ResourceLocation id,
            ResourceLocation output,
            int outputCount,
            int cookTimeTicks,
            int tier,
            List<ResolvedIngredient> inputs
    ) implements ProducerRecipe {

        static VanillaSmelterRecipe of(ServerLevel level, ItemStack sample) {
            ResourceLocation inputId = BuiltInRegistries.ITEM.getKey(sample.getItem());
            ResourceLocation outputId = resolveSmeltingOutputId(level, sample, inputId);
            //? if >=1.21 {
            ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(
                    Townstead.MOD_ID,
                    "butcher/" + inputId.getNamespace() + "_" + inputId.getPath());
            //?} else {
            /*ResourceLocation recipeId = new ResourceLocation(
                    Townstead.MOD_ID,
                    "butcher/" + inputId.getNamespace() + "_" + inputId.getPath());
            *///?}
            return new VanillaSmelterRecipe(
                    recipeId,
                    outputId,
                    1,
                    VANILLA_SMOKER_COOK_TICKS,
                    1,
                    List.of(new Ingredient(List.of(inputId), 1))
            );
        }

        private static ResourceLocation resolveSmeltingOutputId(ServerLevel level, ItemStack sample, ResourceLocation fallbackInputId) {
            //? if >=1.21 {
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(sample), level);
            if (recipe.isEmpty()) return fallbackInputId;
            ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(sample), level.registryAccess());
            //?} else {
            /*net.minecraft.world.SimpleContainer wrapper = new net.minecraft.world.SimpleContainer(sample);
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMOKING, wrapper, level);
            if (recipe.isEmpty()) return fallbackInputId;
            ItemStack result = recipe.get().assemble(wrapper, level.registryAccess());
            *///?}
            if (result.isEmpty()) return fallbackInputId;
            Item item = result.getItem();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            return id != null ? id : fallbackInputId;
        }

        record Ingredient(List<ResourceLocation> acceptableIds, int count) implements ResolvedIngredient {}
    }
}
