package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.ButcherProgressData;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Butcher villagers at Tier 2+ shops drive drained carcass blocks through the
 * state machine (head → skin → cut 1 → cut 2 → cut 3), dropping the
 * corresponding Butchery loot tables directly into inventory and awarding
 * butcher XP per stage.
 *
 * <p>Scope for step 4 MVP: processes only carcasses already drained (the
 * bleeding transition is step 4.5). Overflow drops to the ground at the
 * villager's feet; proper station routing (skin rack, pestle, etc.) lands in
 * a later pass.
 *
 * <p>See {@code docs/design/butchery_integration.md} section 6 for the full
 * stage list.
 */
public class CarcassWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 1200;
    private static final int SCAN_RADIUS_HORIZ = 12;
    private static final int SCAN_RADIUS_VERT = 4;
    private static final double ARRIVAL_DISTANCE_SQ = 2.25; // stand within ~1.5 blocks
    private static final float WALK_SPEED = 0.55f;
    private static final int STAGE_COOLDOWN_TICKS = 24;
    private static final int PATH_TIMEOUT_TICKS = 140;

    private enum Phase { PATH, PROCESS }

    @Nullable private BlockPos targetCarcass;
    @Nullable private BlockPos standPos;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long nextStageTick;
    private long lastPathTick;

    public CarcassWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return false;
        Optional<ButcheryShopScanner.ShopRef> shop = ButcheryShopScanner.shopFor(level, villager);
        if (shop.isEmpty() || shop.get().tier() < ButcheryShopScanner.MIN_CARCASS_TIER) return false;
        return findCarcass(level, villager, shop.get()) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Optional<ButcheryShopScanner.ShopRef> shop = ButcheryShopScanner.shopFor(level, villager);
        if (shop.isEmpty()) return;
        targetCarcass = findCarcass(level, villager, shop.get());
        if (targetCarcass == null) return;
        standPos = findStandPos(level, villager, targetCarcass);
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextStageTick = gameTime + STAGE_COOLDOWN_TICKS;
        setWalkTarget(villager, standPos != null ? standPos : targetCarcass);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetCarcass == null) return false;
        BlockState state = level.getBlockState(targetCarcass);
        if (!CarcassStateMachine.isProcessable(level, state, targetCarcass)) return false;
        if (gameTime - startedTick > MAX_DURATION) return false;
        if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) return false;
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetCarcass == null) return;
        villager.getLookControl().setLookAt(
                targetCarcass.getX() + 0.5, targetCarcass.getY() + 0.5, targetCarcass.getZ() + 0.5);

        if (phase == Phase.PATH) {
            BlockPos anchor = standPos != null ? standPos : targetCarcass;
            double dsq = villager.distanceToSqr(
                    anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            if (dsq <= ARRIVAL_DISTANCE_SQ) {
                phase = Phase.PROCESS;
                nextStageTick = gameTime + STAGE_COOLDOWN_TICKS;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                // Re-stamp walk target periodically in case other tasks clear it.
                setWalkTarget(villager, anchor);
            }
            return;
        }

        // PROCESS phase
        if (gameTime < nextStageTick) return;
        BlockState current = level.getBlockState(targetCarcass);

        // Pre-stage: if the carcass is still fresh, bleed it first. Drained
        // state-machine stages resume on the next cooldown tick.
        if (CarcassStateMachine.isFreshCarcass(current)) {
            if (!CarcassStateMachine.hasBasinNearby(level, targetCarcass)) {
                // Basin was removed mid-cycle; abort.
                targetCarcass = null;
                return;
            }
            if (CarcassStateMachine.bleed(level, targetCarcass)) {
                awardXp(villager, CarcassStateMachine.BLEED_XP, gameTime);
                villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                announceFlavor(villager, level, "dialogue.chat.butcher_flavor.bled_carcass/"
                        + (1 + level.random.nextInt(3)));
            }
            nextStageTick = gameTime + STAGE_COOLDOWN_TICKS;
            return;
        }

        CarcassStateMachine.Stage stage = CarcassStateMachine.Stage
                .forCurrentState(CarcassStateMachine.currentState(current));
        if (stage == null) {
            // Block mid-transition or species finished: reset and bail.
            targetCarcass = null;
            return;
        }
        List<ItemStack> drops = CarcassStateMachine.advance(level, targetCarcass);
        deposit(level, villager, drops);
        awardXp(villager, stage.xpGrant, gameTime);
        villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        nextStageTick = gameTime + STAGE_COOLDOWN_TICKS;

        // Terminal stage removed the block; finish.
        if (stage.toState < 0) {
            announceFlavor(villager, level, "dialogue.chat.butcher_flavor.finished_quartering/"
                    + (1 + level.random.nextInt(3)));
            targetCarcass = null;
        }
    }

    private static void announceFlavor(VillagerEntityMCA villager, ServerLevel level, String key) {
        if (level.getNearestPlayer(villager, 24.0) == null) return;
        villager.sendChatToAllAround(key);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetCarcass = null;
        standPos = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    // --- helpers ---

    @Nullable
    private static BlockPos findCarcass(ServerLevel level, VillagerEntityMCA villager, ButcheryShopScanner.ShopRef shop) {
        BlockPos origin = villager.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (int dy = -SCAN_RADIUS_VERT; dy <= SCAN_RADIUS_VERT; dy++) {
            for (int dx = -SCAN_RADIUS_HORIZ; dx <= SCAN_RADIUS_HORIZ; dx++) {
                for (int dz = -SCAN_RADIUS_HORIZ; dz <= SCAN_RADIUS_HORIZ; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!shop.building().containsPos(cursor)) continue;
                    BlockState state = level.getBlockState(cursor);
                    if (!CarcassStateMachine.isProcessable(level, state, cursor)) continue;
                    double dsq = cursor.distSqr(origin);
                    if (dsq < bestDsq) {
                        bestDsq = dsq;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos carcass) {
        BlockPos[] candidates = {
                carcass.north(), carcass.south(), carcass.east(), carcass.west()
        };
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (BlockPos c : candidates) {
            if (!level.getBlockState(c).isAir()) continue;
            BlockState below = level.getBlockState(c.below());
            if (below.isAir()) continue;
            double dsq = c.distSqr(villager.blockPosition());
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = c;
            }
        }
        return best;
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos target) {
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(target), WALK_SPEED, 1));
    }

    private static void deposit(ServerLevel level, VillagerEntityMCA villager, List<ItemStack> drops) {
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            ItemStack leftover = villager.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                ItemEntity ie = new ItemEntity(level,
                        villager.getX(), villager.getY() + 0.25, villager.getZ(), leftover);
                ie.setPickUpDelay(10);
                level.addFreshEntity(ie);
            }
        }
    }

    private static void awardXp(VillagerEntityMCA villager, int amount, long gameTime) {
        if (amount <= 0) return;
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        ButcherProgressData.addXp(data, amount, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }
}
