package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import com.aetherianartificer.townstead.hunger.profile.ButcherProfileDefinition;
import com.aetherianartificer.townstead.hunger.profile.ButcherProfileRegistry;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public class ButcherWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int ANCHOR_SEARCH_RADIUS = 24;
    private static final int VERTICAL_RADIUS = 3;
    private static final int WORK_RADIUS = 12;
    private static final int NATURAL_RETURN_PADDING = 8;
    private static final int CLOSE_ENOUGH = 0;
    private static final double ARRIVAL_DISTANCE_SQ = 0.36d;
    private static final int MAX_DURATION = 1200;
    private static final int TARGET_STUCK_TICKS = 60;
    private static final int TARGET_BLACKLIST_TICKS = 200;
    private static final int PATHFAIL_MAX_RETRIES = 3;
    private static final int IDLE_BACKOFF_TICKS = 60;
    private static final int SMOKE_WAIT_TICKS = 20;
    private static final int STOCK_INTERVAL_TICKS = 200;
    private static final int REQUEST_RANGE = 24;
    private static final float WALK_SPEED_NORMAL = 0.52f;

    private enum ActionType {
        NONE,
        RETURN,
        FETCH_INPUT,
        FETCH_FUEL,
        UNBLOCK_SMOKER,
        SMOKE,
        COLLECT_OUTPUT,
        STOCK_SMOKER_OUTPUT,
        STOCK
    }

    private ActionType actionType = ActionType.NONE;
    private BlockPos smokerAnchor;
    private BlockPos targetPos;
    private int actionCooldown;
    private long nextAcquireTick;
    private long lastStockTick = Long.MIN_VALUE;
    private long nextRequestTick;
    private HungerData.ButcherBlockedReason blockedReason = HungerData.ButcherBlockedReason.NONE;
    private String unsupportedItemName = "";

    private long currentTargetKey = Long.MIN_VALUE;
    private double lastTargetDistSq = Double.MAX_VALUE;
    private int targetStuckTicks;

    private final Map<Long, Integer> targetRetries = new HashMap<>();
    private final Map<Long, Long> targetBlacklistUntil = new HashMap<>();

    public ButcherWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        VillagerBrain<?> brain = villager.getVillagerBrain();
        VillagerProfession profession = villager.getVillagerData().getProfession();
        boolean isButcher = profession == VillagerProfession.BUTCHER;
        boolean isCook = FarmersDelightCookAssignment.isExternalCookProfession(profession);
        if (!isButcher && !isCook) return false;
        if (isCook && !FarmersDelightCookAssignment.canVillagerWorkAsCook(level, villager)) return false;
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;
        if (townstead$getCurrentScheduleActivity(villager) != Activity.WORK) return false;
        return townstead$findNearestSmoker(level, villager, level.getGameTime()) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        actionType = ActionType.NONE;
        targetPos = null;
        smokerAnchor = townstead$findNearestSmoker(level, villager, gameTime);
        actionCooldown = 0;
        if (nextAcquireTick < gameTime) nextAcquireTick = 0;
        nextRequestTick = 0;
        townstead$resetPathTracking();
        townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NONE);
        townstead$acquireTarget(level, villager, gameTime);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (smokerAnchor == null || !level.getBlockState(smokerAnchor).is(Blocks.SMOKER)) {
            smokerAnchor = townstead$findNearestSmoker(level, villager, gameTime);
            if (smokerAnchor == null) {
                townstead$clearMovementIntent(villager);
                townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NO_SMOKER);
                townstead$maybeAnnounceRequest(level, villager, gameTime);
                return;
            }
        }
        if (townstead$ensureOffSmoker(level, villager, gameTime)) {
            townstead$maybeAnnounceRequest(level, villager, gameTime);
            return;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
            townstead$maybeAnnounceRequest(level, villager, gameTime);
            return;
        }
        if (gameTime < nextAcquireTick) {
            townstead$maybeAnnounceRequest(level, villager, gameTime);
            return;
        }

        if (targetPos == null || !townstead$isTargetStillValid(level, gameTime)) {
            townstead$acquireTarget(level, villager, gameTime);
            if (targetPos == null) {
                townstead$clearMovementIntent(villager);
                townstead$maybeAnnounceRequest(level, villager, gameTime);
                return;
            }
        }
        if (targetPos.equals(smokerAnchor)) {
            BlockPos safeTarget = townstead$findWorkStandPos(level, villager, smokerAnchor);
            if (safeTarget == null) {
                actionType = ActionType.NONE;
                targetPos = null;
                townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.UNREACHABLE);
                nextAcquireTick = gameTime + townstead$idleBackoffTicks(villager);
                townstead$clearMovementIntent(villager);
                townstead$maybeAnnounceRequest(level, villager, gameTime);
                return;
            }
            targetPos = safeTarget;
        }

        BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED_NORMAL, CLOSE_ENOUGH);
        double distSq = villager.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        if (distSq > ARRIVAL_DISTANCE_SQ) {
            townstead$trackPathProgress(level, villager, gameTime, distSq);
            townstead$maybeAnnounceRequest(level, villager, gameTime);
            return;
        }

        townstead$resetPathTracking();
        ActionType executed = actionType;
        ButcherRuntimeContext runtime = townstead$runtime(level, villager);
        int tier = runtime.effectiveTier();
        switch (executed) {
            case RETURN -> {}
            case FETCH_INPUT -> {
                if (townstead$doFetchInput(level, villager, tier, runtime.profile())) {
                    townstead$awardButcherXp(level, villager, gameTime, 1, "fetch_input");
                }
            }
            case FETCH_FUEL -> {
                if (townstead$doFetchFuel(level, villager, runtime.profile())) {
                    townstead$awardButcherXp(level, villager, gameTime, 1, "fetch_fuel");
                }
            }
            case UNBLOCK_SMOKER -> {
                if (townstead$doUnblockSmoker(level, villager, tier, runtime.profile())) {
                    townstead$awardButcherXp(level, villager, gameTime, 1, "clear_blocker");
                }
            }
            case SMOKE -> townstead$awardButcherXp(level, villager, gameTime, 1, "smoke_watch");
            case COLLECT_OUTPUT -> {
                if (townstead$doCollectOutput(level, villager)) {
                    townstead$awardButcherXp(level, villager, gameTime, 3, "collect_output");
                }
            }
            case STOCK_SMOKER_OUTPUT -> {
                boolean moved = townstead$doStockSmokerOutput(level, villager);
                if (!moved) {
                    moved = townstead$doCollectOutput(level, villager);
                }
                if (moved) {
                    townstead$awardButcherXp(level, villager, gameTime, 2, "stock_smoker_output");
                }
            }
            case STOCK -> {
                if (townstead$doStock(level, villager, tier, runtime.profile())) {
                    lastStockTick = gameTime;
                    townstead$awardButcherXp(level, villager, gameTime, 2, "stock_output");
                }
            }
            default -> {}
        }

        actionType = ActionType.NONE;
        targetPos = null;
        actionCooldown = 10;
        nextAcquireTick = gameTime + (executed == ActionType.SMOKE ? townstead$smokeWaitTicks(villager, tier, runtime.profile()) : 0);
        townstead$acquireTarget(level, villager, gameTime);
        townstead$maybeAnnounceRequest(level, villager, gameTime);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return checkExtraStartConditions(level, villager);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        actionType = ActionType.NONE;
        smokerAnchor = null;
        targetPos = null;
        actionCooldown = 0;
        nextAcquireTick = 0;
        lastStockTick = Long.MIN_VALUE;
        nextRequestTick = 0;
        unsupportedItemName = "";
        targetRetries.clear();
        targetBlacklistUntil.clear();
        townstead$clearMovementIntent(villager);
        townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NONE);
        townstead$resetPathTracking();
    }

    private void townstead$acquireTarget(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (smokerAnchor == null) {
            actionType = ActionType.NONE;
            targetPos = null;
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NO_SMOKER);
            nextAcquireTick = gameTime + townstead$idleBackoffTicks(villager);
            return;
        }

        double returnDistSq = villager.distanceToSqr(
                smokerAnchor.getX() + 0.5,
                smokerAnchor.getY() + 0.5,
                smokerAnchor.getZ() + 0.5
        );
        double maxDist = (double) (WORK_RADIUS + NATURAL_RETURN_PADDING) * (WORK_RADIUS + NATURAL_RETURN_PADDING);
        if (returnDistSq > maxDist) {
            actionType = ActionType.RETURN;
            BlockPos standPos = townstead$findWorkStandPos(level, villager, smokerAnchor);
            if (standPos == null) {
                actionType = ActionType.NONE;
                targetPos = null;
                townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.UNREACHABLE);
                nextAcquireTick = gameTime + townstead$idleBackoffTicks(villager);
                return;
            }
            targetPos = standPos;
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.OUT_OF_SCOPE);
            return;
        }

        SmokerBlockEntity smoker = townstead$getSmoker(level);
        if (smoker == null) {
            actionType = ActionType.NONE;
            targetPos = null;
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NO_SMOKER);
            nextAcquireTick = gameTime + townstead$idleBackoffTicks(villager);
            return;
        }
        BlockPos workPos = townstead$findWorkStandPos(level, villager, smokerAnchor);
        if (workPos == null) {
            actionType = ActionType.NONE;
            targetPos = null;
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.UNREACHABLE);
            nextAcquireTick = gameTime + townstead$idleBackoffTicks(villager);
            return;
        }

        if (!smoker.getItem(2).isEmpty()) {
            actionType = ActionType.STOCK_SMOKER_OUTPUT;
            targetPos = workPos;
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NONE);
            return;
        }

        if (townstead$shouldStock(villager, gameTime)) {
            actionType = ActionType.STOCK;
            targetPos = workPos;
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NONE);
            return;
        }

        ButcherRuntimeContext runtime = townstead$runtime(level, villager);
        int tier = runtime.effectiveTier();
        ButcherProfileDefinition profile = runtime.profile();

        if (ButcherSupplyManager.isSmokerBlockerInput(smoker.getItem(0), level)
                || !ButcherSupplyManager.isFuel(smoker.getItem(1), null) && !smoker.getItem(1).isEmpty()) {
            ItemStack invalidInput = smoker.getItem(0);
            ItemStack invalidFuel = smoker.getItem(1);
            if (!invalidInput.isEmpty() && ButcherSupplyManager.isSmokerBlockerInput(invalidInput, level)) {
                unsupportedItemName = invalidInput.getHoverName().getString();
            } else if (!invalidFuel.isEmpty() && !ButcherSupplyManager.isFuel(invalidFuel, null)) {
                unsupportedItemName = invalidFuel.getHoverName().getString();
            }
            actionType = ActionType.UNBLOCK_SMOKER;
            targetPos = workPos;
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NONE);
            return;
        }

        if (smoker.getItem(0).isEmpty()) {
            if (ButcherSupplyManager.hasRawInput(villager.getInventory(), level, tier, profile)
                    || ButcherSupplyManager.pullRawInput(level, villager, smokerAnchor, tier, profile)) {
                actionType = ActionType.FETCH_INPUT;
                targetPos = workPos;
                townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NONE);
                return;
            }
            actionType = ActionType.NONE;
            targetPos = null;
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NO_INPUT);
            nextAcquireTick = gameTime + townstead$idleBackoffTicks(villager);
            return;
        }

        if (smoker.getItem(1).isEmpty()) {
            if (ButcherSupplyManager.hasFuel(villager.getInventory(), profile)
                    || ButcherSupplyManager.pullFuel(level, villager, smokerAnchor, profile)) {
                actionType = ActionType.FETCH_FUEL;
                targetPos = workPos;
                townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NONE);
                return;
            }
            actionType = ActionType.NONE;
            targetPos = null;
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NO_FUEL);
            nextAcquireTick = gameTime + townstead$idleBackoffTicks(villager);
            return;
        }

        actionType = ActionType.SMOKE;
        targetPos = workPos;
        townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NONE);
        nextAcquireTick = gameTime + townstead$smokeWaitTicks(villager, tier, profile);
    }

    private boolean townstead$doFetchInput(
            ServerLevel level,
            VillagerEntityMCA villager,
            int tier,
            ButcherProfileDefinition profile
    ) {
        SmokerBlockEntity smoker = townstead$getSmoker(level);
        if (smoker == null) return false;
        if (!smoker.getItem(0).isEmpty()) return false;
        int slot = ButcherSupplyManager.findRawInputSlot(villager.getInventory(), level, tier, profile);
        if (slot < 0) return false;

        ItemStack stack = villager.getInventory().getItem(slot);
        if (stack.isEmpty()) return false;
        smoker.setItem(0, stack.copyWithCount(1));
        stack.shrink(1);
        smoker.setChanged();
        return true;
    }

    private boolean townstead$doFetchFuel(
            ServerLevel level,
            VillagerEntityMCA villager,
            ButcherProfileDefinition profile
    ) {
        SmokerBlockEntity smoker = townstead$getSmoker(level);
        if (smoker == null) return false;
        if (!smoker.getItem(1).isEmpty()) return false;
        int slot = ButcherSupplyManager.findFuelSlot(villager.getInventory(), profile);
        if (slot < 0) return false;

        ItemStack stack = villager.getInventory().getItem(slot);
        if (stack.isEmpty()) return false;
        smoker.setItem(1, stack.copyWithCount(1));
        stack.shrink(1);
        smoker.setChanged();
        return true;
    }

    private boolean townstead$doCollectOutput(ServerLevel level, VillagerEntityMCA villager) {
        SmokerBlockEntity smoker = townstead$getSmoker(level);
        if (smoker == null) return false;
        ItemStack output = smoker.getItem(2);
        if (output.isEmpty()) return false;

        ItemStack moving = output.copy();
        ItemStack remainder = villager.getInventory().addItem(moving);
        smoker.setItem(2, remainder);
        smoker.setChanged();
        return true;
    }

    private boolean townstead$doStockSmokerOutput(ServerLevel level, VillagerEntityMCA villager) {
        SmokerBlockEntity smoker = townstead$getSmoker(level);
        if (smoker == null) return false;
        ItemStack output = smoker.getItem(2);
        if (output.isEmpty()) return false;

        ItemStack moving = output.copy();
        NearbyItemSources.insertIntoNearbyStorage(level, villager, moving, 16, 3, smokerAnchor);
        if (moving.getCount() == output.getCount()) return false;
        smoker.setItem(2, moving);
        smoker.setChanged();
        return true;
    }

    private boolean townstead$doStock(
            ServerLevel level,
            VillagerEntityMCA villager,
            int tier,
            ButcherProfileDefinition profile
    ) {
        return ButcherSupplyManager.offloadOutput(level, villager, smokerAnchor, tier, profile);
    }

    private boolean townstead$doUnblockSmoker(
            ServerLevel level,
            VillagerEntityMCA villager,
            int tier,
            ButcherProfileDefinition profile
    ) {
        SmokerBlockEntity smoker = townstead$getSmoker(level);
        if (smoker == null) return false;
        SlotClearResult inputResult = townstead$relocateSmokerSlotIfInvalid(level, villager, smoker, 0, stack ->
                !ButcherSupplyManager.isSmokerBlockerInput(stack, level));
        SlotClearResult fuelResult = townstead$relocateSmokerSlotIfInvalid(level, villager, smoker, 1, stack ->
                ButcherSupplyManager.isFuel(stack, null));
        if (inputResult == SlotClearResult.CLEARED || fuelResult == SlotClearResult.CLEARED) smoker.setChanged();

        boolean hadBlocker = inputResult != SlotClearResult.NO_BLOCKER || fuelResult != SlotClearResult.NO_BLOCKER;
        boolean anyFailed = inputResult == SlotClearResult.FAILED || fuelResult == SlotClearResult.FAILED;
        if (anyFailed) {
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.UNSUPPORTED_RECIPE);
            return false;
        }
        if (hadBlocker) {
            unsupportedItemName = "";
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.NONE);
            return true;
        }
        return false;
    }

    private SlotClearResult townstead$relocateSmokerSlotIfInvalid(
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

        NearbyItemSources.insertIntoNearbyStorage(level, villager, moving, 16, 3, smokerAnchor);
        if (!moving.isEmpty()) {
            ItemStack remainder = villager.getInventory().addItem(moving);
            if (!remainder.isEmpty()) {
                smoker.setItem(slotIndex, remainder);
                return SlotClearResult.FAILED;
            }
        }
        return SlotClearResult.CLEARED;
    }

    private boolean townstead$shouldStock(VillagerEntityMCA villager, long gameTime) {
        ButcherRuntimeContext runtime = townstead$runtime((ServerLevel) villager.level(), villager);
        boolean hasOutput = ButcherSupplyManager.hasStockableOutput(villager.getInventory(), runtime.profile());
        if (!hasOutput) return false;
        if (lastStockTick == Long.MIN_VALUE) return true;
        long elapsed = gameTime - lastStockTick;
        if (elapsed < 0) return true;
        return elapsed >= townstead$stockIntervalTicks(villager, runtime.profile());
    }

    private SmokerBlockEntity townstead$getSmoker(ServerLevel level) {
        if (smokerAnchor == null) return null;
        if (!(level.getBlockEntity(smokerAnchor) instanceof SmokerBlockEntity smoker)) return null;
        return smoker;
    }

    private boolean townstead$hasInventorySpace(SimpleContainer inv, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (inv.canAddItem(stack)) return true;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) return true;
            if (!ItemStack.isSameItemSameComponents(slot, stack)) continue;
            if (slot.getCount() < Math.min(slot.getMaxStackSize(), inv.getMaxStackSize())) return true;
        }
        return false;
    }

    private void townstead$trackPathProgress(ServerLevel level, VillagerEntityMCA villager, long gameTime, double distSq) {
        if (targetPos == null) return;
        long key = targetPos.asLong();
        if (currentTargetKey != key) {
            currentTargetKey = key;
            lastTargetDistSq = distSq;
            targetStuckTicks = 0;
            return;
        }

        if (distSq >= (lastTargetDistSq - 0.05d)) {
            targetStuckTicks++;
        } else {
            targetStuckTicks = 0;
            lastTargetDistSq = distSq;
        }

        if (targetStuckTicks < TARGET_STUCK_TICKS) return;

        int retry = targetRetries.getOrDefault(key, 0) + 1;
        if (retry >= PATHFAIL_MAX_RETRIES) {
            targetRetries.remove(key);
            targetBlacklistUntil.put(key, gameTime + TARGET_BLACKLIST_TICKS);
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.UNREACHABLE);
        } else {
            targetRetries.put(key, retry);
        }

        actionType = ActionType.NONE;
        targetPos = null;
        nextAcquireTick = gameTime + townstead$idleBackoffTicks(villager);
        townstead$resetPathTracking();
    }

    private boolean townstead$isTargetStillValid(ServerLevel level, long gameTime) {
        if (targetPos == null || smokerAnchor == null) return false;
        if (townstead$isBlacklisted(targetPos, gameTime)) return false;
        BlockState state = level.getBlockState(smokerAnchor);
        if (!state.is(Blocks.SMOKER)) return false;
        return switch (actionType) {
            case RETURN, FETCH_INPUT, FETCH_FUEL, UNBLOCK_SMOKER, SMOKE, COLLECT_OUTPUT, STOCK_SMOKER_OUTPUT -> true;
            case STOCK -> true;
            case NONE -> false;
        };
    }

    private boolean townstead$isBlacklisted(BlockPos pos, long gameTime) {
        Long until = targetBlacklistUntil.get(pos.asLong());
        if (until == null) return false;
        if (until <= gameTime) {
            targetBlacklistUntil.remove(pos.asLong());
            return false;
        }
        return true;
    }

    private BlockPos townstead$findNearestSmoker(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BlockPos center = villager.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-ANCHOR_SEARCH_RADIUS, -VERTICAL_RADIUS, -ANCHOR_SEARCH_RADIUS),
                center.offset(ANCHOR_SEARCH_RADIUS, VERTICAL_RADIUS, ANCHOR_SEARCH_RADIUS))) {
            if (!level.getBlockState(pos).is(Blocks.SMOKER)) continue;
            if (townstead$isBlacklisted(pos, gameTime)) continue;
            double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
        return best;
    }

    private BlockPos townstead$findWorkStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = anchor.relative(dir);
            if (!townstead$isStandable(level, candidate)) continue;
            if (townstead$isBlacklisted(candidate, level.getGameTime())) continue;
            double dist = villager.distanceToSqr(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate.immutable();
            }
        }
        return best;
    }

    private boolean townstead$isStandable(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return false;
        if (!level.getBlockState(pos.above()).isAir()) return false;
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private boolean townstead$ensureOffSmoker(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (smokerAnchor == null) return false;
        if (!villager.blockPosition().equals(smokerAnchor)) return false;
        BlockPos standPos = townstead$findWorkStandPos(level, villager, smokerAnchor);
        if (standPos == null) {
            townstead$setBlockedReason(level, villager, HungerData.ButcherBlockedReason.UNREACHABLE);
            nextAcquireTick = gameTime + townstead$idleBackoffTicks(villager);
            townstead$clearMovementIntent(villager);
            return true;
        }
        actionType = ActionType.RETURN;
        targetPos = standPos;
        BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED_NORMAL, CLOSE_ENOUGH);
        return true;
    }

    private void townstead$clearMovementIntent(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private void townstead$resetPathTracking() {
        currentTargetKey = Long.MIN_VALUE;
        lastTargetDistSq = Double.MAX_VALUE;
        targetStuckTicks = 0;
    }

    private void townstead$setBlockedReason(ServerLevel level, VillagerEntityMCA villager, HungerData.ButcherBlockedReason reason) {
        if (blockedReason == reason) return;
        blockedReason = reason;

        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        if (HungerData.getButcherBlockedReason(hunger) != reason) {
            HungerData.setButcherBlockedReason(hunger, reason);
            villager.setData(Townstead.HUNGER_DATA, hunger);
        }
        PacketDistributor.sendToPlayersTrackingEntity(villager, new ButcherStatusSyncPayload(villager.getId(), reason.id()));
        if (reason == HungerData.ButcherBlockedReason.NONE) {
            nextRequestTick = 0;
        } else {
            long soonest = level.getGameTime() + 40;
            if (nextRequestTick < soonest) nextRequestTick = soonest;
        }
    }

    private void townstead$maybeAnnounceRequest(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!TownsteadConfig.ENABLE_FARMER_REQUEST_CHAT.get()) return;
        if (blockedReason == HungerData.ButcherBlockedReason.NONE) return;
        if (gameTime < nextRequestTick) return;
        if (level.getNearestPlayer(villager, REQUEST_RANGE) == null) {
            nextRequestTick = gameTime + 200;
            return;
        }

        String key = switch (blockedReason) {
            case NO_SMOKER -> "dialogue.chat.butcher_request.no_smoker/" + (1 + level.random.nextInt(4));
            case NO_INPUT -> "dialogue.chat.butcher_request.no_input/" + (1 + level.random.nextInt(6));
            case UNSUPPORTED_RECIPE -> null;
            case NO_FUEL -> "dialogue.chat.butcher_request.no_fuel/" + (1 + level.random.nextInt(6));
            case OUTPUT_BLOCKED -> "dialogue.chat.butcher_request.output_blocked/" + (1 + level.random.nextInt(4));
            case UNREACHABLE -> "dialogue.chat.butcher_request.unreachable/" + (1 + level.random.nextInt(6));
            case OUT_OF_SCOPE -> "dialogue.chat.butcher_request.out_of_scope/" + (1 + level.random.nextInt(4));
            case NO_VALID_TARGET -> "dialogue.chat.butcher_request.no_valid_target/" + (1 + level.random.nextInt(4));
            case NONE -> null;
        };
        if (blockedReason == HungerData.ButcherBlockedReason.UNSUPPORTED_RECIPE) {
            String item = unsupportedItemName == null || unsupportedItemName.isBlank() ? "that" : unsupportedItemName;
            villager.sendChatToAllAround("dialogue.chat.butcher_request.unsupported_recipe_item", item);
            villager.getLongTermMemory().remember("townstead.butcher_request.any");
            villager.getLongTermMemory().remember("townstead.butcher_request." + blockedReason.id());
            int interval = townstead$requestIntervalTicks(villager);
            nextRequestTick = gameTime + Math.max(200, interval);
            return;
        }
        if (key == null) return;

        villager.sendChatToAllAround(key);
        villager.getLongTermMemory().remember("townstead.butcher_request.any");
        villager.getLongTermMemory().remember("townstead.butcher_request." + blockedReason.id());
        int interval = townstead$requestIntervalTicks(villager);
        nextRequestTick = gameTime + Math.max(200, interval);
    }

    private int townstead$butcherTier(VillagerEntityMCA villager) {
        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        int tier = ButcherProgressData.getTier(hunger);
        villager.setData(Townstead.HUNGER_DATA, hunger);
        return tier;
    }

    private int townstead$smokeWaitTicks(VillagerEntityMCA villager, int tier, ButcherProfileDefinition profile) {
        int base = switch (Math.max(1, Math.min(tier, 5))) {
            case 1 -> SMOKE_WAIT_TICKS;
            case 2 -> 18;
            case 3 -> 16;
            case 4 -> 14;
            default -> 12;
        };
        double profileScale = profile == null ? 1.0d : profile.throughputModifier();
        return townstead$scaleInt(base, townstead$profile(villager).throughputScale() * profileScale, 8, 40);
    }

    private int townstead$idleBackoffTicks(VillagerEntityMCA villager) {
        return townstead$scaleInt(IDLE_BACKOFF_TICKS, townstead$profile(villager).idleBackoffScale(), 10, 300);
    }

    private int townstead$requestIntervalTicks(VillagerEntityMCA villager) {
        int base = TownsteadConfig.FARMER_REQUEST_INTERVAL_TICKS.get();
        ButcherProfileDefinition profile = townstead$runtime((ServerLevel) villager.level(), villager).profile();
        double profileScale = profile == null ? 1.0d : profile.requestIntervalModifier();
        return townstead$scaleInt(base, townstead$profile(villager).requestIntervalScale() * profileScale, 200, 72000);
    }

    private int townstead$stockIntervalTicks(VillagerEntityMCA villager, ButcherProfileDefinition profile) {
        double profileScale = profile == null ? 1.0d : profile.stockCadenceModifier();
        return townstead$scaleInt(STOCK_INTERVAL_TICKS, townstead$profile(villager).stockCadenceScale() * profileScale, 80, 1200);
    }

    private ButcherPersonalityProfile townstead$profile(VillagerEntityMCA villager) {
        return ButcherPersonalityProfile.forVillager(villager);
    }

    private int townstead$scaleInt(int base, double scale, int min, int max) {
        int scaled = (int) Math.round(base * scale);
        return Math.max(min, Math.min(max, scaled));
    }

    private void townstead$awardButcherXp(ServerLevel level, VillagerEntityMCA villager, long gameTime, int amount, String source) {
        if (amount <= 0) return;
        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        ButcherProgressData.GainResult result = ButcherProgressData.addXp(hunger, amount, gameTime);
        if (result.appliedXp() <= 0) return;
        villager.setData(Townstead.HUNGER_DATA, hunger);

        if (result.tierUp()) {
            String chatKey = "dialogue.chat.butcher_progress.tier_up/" + (1 + level.random.nextInt(6));
            villager.sendChatToAllAround(chatKey);
            villager.getLongTermMemory().remember("townstead.butcher.tier_up");
            villager.getLongTermMemory().remember("townstead.butcher.tier." + result.tierAfter());
            villager.getLongTermMemory().remember("townstead.butcher.discovery.unlock");
            villager.getLongTermMemory().remember("townstead.butcher.discovery.tier." + result.tierAfter());
        }
    }

    private Activity townstead$getCurrentScheduleActivity(VillagerEntityMCA self) {
        long dayTime = self.level().getDayTime() % 24000L;
        return self.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    private ButcherRuntimeContext townstead$runtime(ServerLevel level, VillagerEntityMCA villager) {
        int villagerTier = townstead$butcherTier(villager);
        if (smokerAnchor == null) {
            ButcherProfileDefinition fallback = ButcherProfileRegistry.resolveForTier(ButcherProfileRegistry.DEFAULT_PROFILE_ID, villagerTier);
            return new ButcherRuntimeContext(villagerTier, fallback);
        }

        ButcherPolicyData.ResolvedButcherPolicy policy = ButcherPolicyData.get(level).resolveForAnchor(smokerAnchor);
        int effectiveTier = Math.max(1, Math.min(5, Math.min(villagerTier, policy.tier())));
        ButcherProfileDefinition profile = ButcherProfileRegistry.resolveForTier(policy.profileId(), effectiveTier);
        return new ButcherRuntimeContext(effectiveTier, profile);
    }

    private record ButcherRuntimeContext(int effectiveTier, ButcherProfileDefinition profile) {}

    private enum SlotClearResult {
        NO_BLOCKER,
        CLEARED,
        FAILED
    }
}
