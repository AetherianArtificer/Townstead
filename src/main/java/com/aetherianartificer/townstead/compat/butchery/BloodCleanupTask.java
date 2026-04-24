package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.ButcherProgressData;
import com.aetherianartificer.townstead.tick.WorkToolTicker;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Butcher walks to a blood puddle with a wet sponge or rag and cleans it,
 * matching {@code CleanbloodProcedure} (destroy block, splash sound,
 * decrement wetness by 1). When the cloth is dry, the same task redirects
 * to a water source or filled water cauldron in the shop and re-wets it
 * to full, matching {@code WetspongeProcedure} / {@code WetspongecauldronProcedure}.
 *
 * <p>Puddles form around hanging carcasses during draining when no blood
 * grate is in the column below (see mod's {@code PlacebloodpuddleProcedure}).
 * Our own drain shortcut gates on a grate being present, so the villager's
 * bleed action never creates puddles by itself; this task handles puddles
 * the player produced by slaughtering without a grate, or by blood
 * spreading from a source puddle into neighbouring squares.
 *
 * <p>One action per task session (re-wet or clean, not both). Runs at
 * priority 77, after all production work — blood cleanup is housekeeping,
 * not critical path.
 */
public class BloodCleanupTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 400;
    private static final double ARRIVAL_DISTANCE_SQ = 2.89;
    private static final float WALK_SPEED = 0.5f;
    private static final int ACT_COOLDOWN_TICKS = 12;
    private static final int PATH_TIMEOUT_TICKS = 200;

    //? if >=1.21 {
    private static final ResourceLocation SOUND_SPLASH =
            ResourceLocation.parse("entity.fishing_bobber.splash");
    //?} else {
    /*private static final ResourceLocation SOUND_SPLASH =
            new ResourceLocation("entity.fishing_bobber.splash");
    *///?}

    private enum Phase { PATH, ACT }

    private enum Action { CLEAN, REWET }

    @Nullable private BlockPos targetPos;
    @Nullable private BlockPos standPos;
    @Nullable private Action action;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long lastPathTick;
    private long nextActTick;

    public BloodCleanupTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return false;
        return planTarget(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        PlanResult plan = planTarget(level, villager);
        if (plan == null) return;
        targetPos = plan.pos;
        action = plan.action;
        standPos = findStandPos(level, villager, targetPos);
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextActTick = gameTime + ACT_COOLDOWN_TICKS;
        setWalkTarget(villager, standPos != null ? standPos : targetPos);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetPos == null || action == null) return false;
        if (!isTargetStillValid(level, villager)) return false;
        if (gameTime - startedTick > MAX_DURATION) return false;
        if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) return false;
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetPos == null || action == null) return;
        villager.getLookControl().setLookAt(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        if (phase == Phase.PATH) {
            BlockPos anchor = standPos != null ? standPos : targetPos;
            double dsq = villager.distanceToSqr(
                    anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            if (dsq <= ARRIVAL_DISTANCE_SQ) {
                phase = Phase.ACT;
                nextActTick = gameTime + ACT_COOLDOWN_TICKS;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                setWalkTarget(villager, anchor);
            }
            return;
        }

        if (gameTime < nextActTick) return;
        switch (action) {
            case CLEAN -> executeClean(level, villager);
            case REWET -> executeRewet(level, villager);
        }
        targetPos = null;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetPos = null;
        standPos = null;
        action = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    // ── Actions ──

    private void executeClean(ServerLevel level, VillagerEntityMCA villager) {
        BlockState state = level.getBlockState(targetPos);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!SpongeRagHelper.BLOOD_PUDDLE_ID.equals(id)) return;
        int clothSlot = findWetClothSlot(villager);
        if (clothSlot < 0) return;

        equipCloth(villager, clothSlot);
        villager.swing(InteractionHand.MAIN_HAND, true);
        playSplash(level, targetPos);
        level.destroyBlock(targetPos, false);
        SpongeRagHelper.decrementWetness(villager.getInventory().getItem(clothSlot));
        villager.getInventory().setChanged();
        awardXp(villager, 1, level.getGameTime());
    }

    private void executeRewet(ServerLevel level, VillagerEntityMCA villager) {
        BlockState state = level.getBlockState(targetPos);
        int clothSlot = findDryClothSlot(villager);
        if (clothSlot < 0) return;
        if (!isRewetSource(state)) return;

        equipCloth(villager, clothSlot);
        villager.swing(InteractionHand.MAIN_HAND, true);
        playSplash(level, targetPos);
        SpongeRagHelper.setWetness(villager.getInventory().getItem(clothSlot), SpongeRagHelper.FULL_WETNESS);
        villager.getInventory().setChanged();

        // Matches WetspongecauldronProcedure: taking water from a cauldron
        // decrements its level (eventually emptying it).
        if (state.getBlock() instanceof LayeredCauldronBlock
                && state.hasProperty(BlockStateProperties.LEVEL_CAULDRON)) {
            int cauldronLevel = state.getValue(BlockStateProperties.LEVEL_CAULDRON);
            if (cauldronLevel > 1) {
                level.setBlock(targetPos, state.setValue(BlockStateProperties.LEVEL_CAULDRON, cauldronLevel - 1), 3);
            } else {
                level.setBlock(targetPos, Blocks.CAULDRON.defaultBlockState(), 3);
            }
        }
    }

    private static void playSplash(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos,
                BuiltInRegistries.SOUND_EVENT.get(SOUND_SPLASH),
                SoundSource.NEUTRAL, 0.3f, 1f);
    }

    private static void equipCloth(VillagerEntityMCA villager, int slot) {
        ItemStack cloth = villager.getInventory().getItem(slot);
        if (WorkToolTicker.isCleaver(villager.getMainHandItem())
                || WorkToolTicker.isKnife(villager.getMainHandItem())
                || WorkToolTicker.isHacksaw(villager.getMainHandItem())
                || !SpongeRagHelper.isCloth(villager.getMainHandItem())) {
            villager.setItemInHand(InteractionHand.MAIN_HAND, cloth.copy());
        }
    }

    private boolean isTargetStillValid(ServerLevel level, VillagerEntityMCA villager) {
        BlockState state = level.getBlockState(targetPos);
        return switch (action) {
            case CLEAN -> SpongeRagHelper.BLOOD_PUDDLE_ID
                    .equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                    && findWetClothSlot(villager) >= 0;
            case REWET -> isRewetSource(state) && findDryClothSlot(villager) >= 0;
        };
    }

    private static boolean isRewetSource(BlockState state) {
        if (state.is(Blocks.WATER)) return true;
        if (state.getBlock() instanceof LayeredCauldronBlock
                && state.hasProperty(BlockStateProperties.LEVEL_CAULDRON)) {
            return state.getValue(BlockStateProperties.LEVEL_CAULDRON) >= 1;
        }
        return false;
    }

    // ── Planning / scanning ──

    private record PlanResult(BlockPos pos, Action action) {}

    @Nullable
    private static PlanResult planTarget(ServerLevel level, VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        int wetSlot = findWetClothSlot(villager);
        int drySlot = wetSlot < 0 ? findDryClothSlot(villager) : -1;

        // Prefer cleaning if we have a wet cloth and a puddle exists.
        if (wetSlot >= 0) {
            BlockPos puddle = findBlood(level, villager);
            if (puddle != null) return new PlanResult(puddle, Action.CLEAN);
        }
        // Else if we have a dry cloth and a rewet source is reachable.
        if (drySlot >= 0) {
            BlockPos water = findRewetSource(level, villager);
            if (water != null) return new PlanResult(water, Action.REWET);
        }
        // Villager has a wet cloth but no puddles: we may still want to
        // opportunistically top it up if a water source is handy. Skipped
        // for v1 — cloth stays at whatever wetness it has; the acquisition
        // ticker and carcass work will cycle it naturally.
        return null;
    }

    @Nullable
    private static BlockPos findBlood(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos origin = villager.blockPosition();
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.carcassCapableShops(level, villager)) {
            BlockPos found = scanBuildingFor(level, ref.building(),
                    s -> SpongeRagHelper.BLOOD_PUDDLE_ID.equals(BuiltInRegistries.BLOCK.getKey(s.getBlock())));
            if (found == null) continue;
            double dsq = found.distSqr(origin);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = found;
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findRewetSource(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos origin = villager.blockPosition();
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.carcassCapableShops(level, villager)) {
            BlockPos found = scanBuildingFor(level, ref.building(), BloodCleanupTask::isRewetSource);
            if (found == null) continue;
            double dsq = found.distSqr(origin);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = found;
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos scanBuildingFor(ServerLevel level, Building building,
                                            java.util.function.Predicate<BlockState> matcher) {
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        if (p0 == null || p1 == null) return null;
        int x0 = Math.min(p0.getX(), p1.getX());
        int x1 = Math.max(p0.getX(), p1.getX());
        int y0 = Math.min(p0.getY(), p1.getY());
        int y1 = Math.max(p0.getY(), p1.getY());
        int z0 = Math.min(p0.getZ(), p1.getZ());
        int z1 = Math.max(p0.getZ(), p1.getZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    cursor.set(x, y, z);
                    if (matcher.test(level.getBlockState(cursor))) return cursor.immutable();
                }
            }
        }
        return null;
    }

    private static int findWetClothSlot(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (SpongeRagHelper.isWet(inv.getItem(i))) return i;
        }
        return -1;
    }

    private static int findDryClothSlot(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (SpongeRagHelper.isCloth(stack) && !SpongeRagHelper.isWet(stack)) return i;
        }
        return -1;
    }

    // ── Stand / walk helpers ──

    @Nullable
    private static BlockPos findStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos target) {
        BlockPos[] candidates = {
                target.north(), target.south(), target.east(), target.west()
        };
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        BlockPos villagerPos = villager.blockPosition();
        for (BlockPos c : candidates) {
            if (!isStandable(level, c)) continue;
            double dsq = c.distSqr(villagerPos);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = c;
            }
        }
        return best;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockState at = level.getBlockState(pos);
        if (!at.isAir() && !at.canBeReplaced()) return false;
        BlockState head = level.getBlockState(pos.above());
        if (!head.isAir() && !head.canBeReplaced()) return false;
        BlockState floor = level.getBlockState(pos.below());
        return !floor.isAir();
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos target) {
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(target), WALK_SPEED, 1));
    }

    private static void awardXp(VillagerEntityMCA villager, int amount, long gameTime) {
        if (amount <= 0) return;
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        ButcherProgressData.GainResult result = ButcherProgressData.addXp(data, amount, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
        if (result.tierUp()) {
            ButcherTradeLevelSync.syncToTier(villager, result.tierAfter());
        }
    }
}
