package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.ButcherProgressData;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Butcher hangs cooked sausages (regular or blood) on a {@code butchery:hook}
 * below which there's either air (fresh placement) or an existing
 * {@code cooked_sausages} / {@code cooked_blood_sausages} block at state 0
 * or 1 (stacking up toward the state-2 cap). Matches
 * {@code PlacecookedsausagesonhookProcedure} and the blood-sausage sibling:
 * block goes at {@code hook.below()}, facing derived from the hook's own
 * facing, sound {@code block.chain.hit} at vol 1.
 *
 * <p>One hang per task session. Raw sausages intentionally aren't hung by
 * the villager — those get cooked through the smoker first
 * ({@code minecraft:smoking} recipe) and only the cooked form ends up on
 * hooks.
 */
public class SausageHookTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 400;
    private static final double ARRIVAL_DISTANCE_SQ = 2.89;
    private static final float WALK_SPEED = 0.55f;
    private static final int PATH_TIMEOUT_TICKS = 200;
    private static final int ACT_COOLDOWN_TICKS = 24;

    //? if >=1.21 {
    private static final ResourceLocation HOOK_ID = ResourceLocation.parse("butchery:hook");
    private static final ResourceLocation COOKED_SAUSAGE_ID = ResourceLocation.parse("butchery:cooked_sausage");
    private static final ResourceLocation COOKED_BLOOD_SAUSAGE_ID = ResourceLocation.parse("butchery:cooked_blood_sausage");
    private static final ResourceLocation COOKED_SAUSAGES_BLOCK_ID = ResourceLocation.parse("butchery:cooked_sausages");
    private static final ResourceLocation COOKED_BLOOD_SAUSAGES_BLOCK_ID = ResourceLocation.parse("butchery:cooked_blood_sausages");
    private static final ResourceLocation SOUND_CHAIN_HIT = ResourceLocation.parse("block.chain.hit");
    //?} else {
    /*private static final ResourceLocation HOOK_ID = new ResourceLocation("butchery", "hook");
    private static final ResourceLocation COOKED_SAUSAGE_ID = new ResourceLocation("butchery", "cooked_sausage");
    private static final ResourceLocation COOKED_BLOOD_SAUSAGE_ID = new ResourceLocation("butchery", "cooked_blood_sausage");
    private static final ResourceLocation COOKED_SAUSAGES_BLOCK_ID = new ResourceLocation("butchery", "cooked_sausages");
    private static final ResourceLocation COOKED_BLOOD_SAUSAGES_BLOCK_ID = new ResourceLocation("butchery", "cooked_blood_sausages");
    private static final ResourceLocation SOUND_CHAIN_HIT = new ResourceLocation("block.chain.hit");
    *///?}

    private enum Phase { PATH, ACT }

    /** Which sausage flavor this task is hanging on the current trip. */
    private enum SausageKind {
        REGULAR(COOKED_SAUSAGE_ID, COOKED_SAUSAGES_BLOCK_ID),
        BLOOD(COOKED_BLOOD_SAUSAGE_ID, COOKED_BLOOD_SAUSAGES_BLOCK_ID);

        final ResourceLocation itemId;
        final ResourceLocation blockId;

        SausageKind(ResourceLocation itemId, ResourceLocation blockId) {
            this.itemId = itemId;
            this.blockId = blockId;
        }
    }

    @Nullable private BlockPos hookPos;
    @Nullable private BlockPos standPos;
    @Nullable private SausageKind pendingKind;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long lastPathTick;
    private long nextActTick;
    private boolean stalled;

    public SausageHookTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return false;
        return findHangableHook(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        HangTarget target = findHangableHook(level, villager);
        if (target == null) return;
        hookPos = target.hookPos;
        pendingKind = target.kind;
        standPos = findStandPos(level, villager, hookPos);
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextActTick = gameTime + ACT_COOLDOWN_TICKS;
        stalled = false;
        setWalkTarget(villager, standPos != null ? standPos : hookPos);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (hookPos == null || pendingKind == null) return false;
        if (!isHook(level, hookPos)) return false;
        if (gameTime - startedTick > MAX_DURATION) { stalled = true; return false; }
        if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) {
            stalled = true;
            return false;
        }
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (hookPos == null || pendingKind == null) return;
        villager.getLookControl().setLookAt(
                hookPos.getX() + 0.5, hookPos.getY() - 0.5, hookPos.getZ() + 0.5);

        if (phase == Phase.PATH) {
            BlockPos anchor = standPos != null ? standPos : hookPos;
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
        executeHang(level, villager, gameTime);
        hookPos = null; // one hang per session
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        stalled = false;
        hookPos = null;
        standPos = null;
        pendingKind = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    // ── Action ──

    private void executeHang(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BlockPos below = hookPos.below();
        BlockState belowState = level.getBlockState(below);
        int sausageSlot = findSausageSlot(villager, pendingKind);
        if (sausageSlot < 0) return; // inventory changed mid-walk; abort gracefully

        boolean changed = false;
        if (belowState.isAir()) {
            BlockState hookState = level.getBlockState(hookPos);
            Direction facing = facingFromHook(hookState);
            Block sausageBlock = BuiltInRegistries.BLOCK.get(pendingKind.blockId);
            if (sausageBlock != null) {
                BlockState placed = sausageBlock.defaultBlockState();
                Property<?> facingProp = placed.getBlock().getStateDefinition().getProperty("facing");
                if (facingProp instanceof DirectionProperty dp && dp.getPossibleValues().contains(facing)) {
                    placed = placed.setValue(dp, facing);
                }
                level.setBlock(below, placed, 3);
                changed = true;
            }
        } else {
            ResourceLocation belowId = BuiltInRegistries.BLOCK.getKey(belowState.getBlock());
            if (belowId.equals(pendingKind.blockId)) {
                Property<?> prop = belowState.getBlock().getStateDefinition().getProperty("blockstate");
                if (prop instanceof IntegerProperty ip) {
                    int current = belowState.getValue(ip);
                    if (current < 2 && ip.getPossibleValues().contains(current + 1)) {
                        level.setBlock(below, belowState.setValue(ip, current + 1), 3);
                        changed = true;
                    }
                }
            }
        }

        if (!changed) return;

        villager.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, hookPos,
                BuiltInRegistries.SOUND_EVENT.get(SOUND_CHAIN_HIT),
                SoundSource.NEUTRAL, 1f, 1f);
        villager.getInventory().getItem(sausageSlot).shrink(1);
        awardXp(villager, 1, gameTime);
    }

    private static Direction facingFromHook(BlockState hookState) {
        Property<?> prop = hookState.getBlock().getStateDefinition().getProperty("facing");
        if (prop instanceof DirectionProperty dp) return hookState.getValue(dp);
        return Direction.NORTH;
    }

    // ── Scanning ──

    private record HangTarget(BlockPos hookPos, SausageKind kind) {}

    @Nullable
    private static HangTarget findHangableHook(ServerLevel level, VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        boolean hasRegular = countMatching(inv, COOKED_SAUSAGE_ID) > 0;
        boolean hasBlood = countMatching(inv, COOKED_BLOOD_SAUSAGE_ID) > 0;
        if (!hasRegular && !hasBlood) return null;

        BlockPos origin = villager.blockPosition();
        HangTarget best = null;
        double bestDsq = Double.MAX_VALUE;

        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.carcassCapableShops(level, villager)) {
            Building building = ref.building();
            List<BlockPos> hooks = building.getBlocks().get(HOOK_ID);
            if (hooks == null) continue;
            for (BlockPos hook : hooks) {
                SausageKind kind = pickKindForHook(level, hook, hasRegular, hasBlood);
                if (kind == null) continue;
                double dsq = hook.distSqr(origin);
                if (dsq < bestDsq) {
                    bestDsq = dsq;
                    best = new HangTarget(hook.immutable(), kind);
                }
            }
        }
        return best;
    }

    /**
     * For the given hook and inventory flags, return the kind the villager
     * should hang here, or null if this hook isn't usable right now.
     * Prefers filling an existing partially-hung block over starting a new
     * one, so each rack fills up before the next gets touched.
     */
    @Nullable
    private static SausageKind pickKindForHook(ServerLevel level, BlockPos hook,
                                               boolean hasRegular, boolean hasBlood) {
        BlockPos below = hook.below();
        BlockState belowState = level.getBlockState(below);
        ResourceLocation belowId = belowState.getBlock() == Blocks.AIR
                ? null
                : BuiltInRegistries.BLOCK.getKey(belowState.getBlock());

        if (belowId != null && belowId.equals(SausageKind.REGULAR.blockId)) {
            if (hasRegular && partialState(belowState)) return SausageKind.REGULAR;
            return null;
        }
        if (belowId != null && belowId.equals(SausageKind.BLOOD.blockId)) {
            if (hasBlood && partialState(belowState)) return SausageKind.BLOOD;
            return null;
        }
        if (belowState.isAir()) {
            // Fresh hook. Prefer regular sausage if both available; blood
            // is rarer and we'd rather use it only when we have nothing
            // else to hang.
            if (hasRegular) return SausageKind.REGULAR;
            if (hasBlood) return SausageKind.BLOOD;
        }
        return null;
    }

    private static boolean partialState(BlockState state) {
        Property<?> prop = state.getBlock().getStateDefinition().getProperty("blockstate");
        if (!(prop instanceof IntegerProperty ip)) return false;
        return state.getValue(ip) < 2;
    }

    private static int countMatching(SimpleContainer inv, ResourceLocation itemId) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int findSausageSlot(VillagerEntityMCA villager, SausageKind kind) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(kind.itemId)) return i;
        }
        return -1;
    }

    private static boolean isHook(ServerLevel level, BlockPos pos) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return HOOK_ID.equals(id);
    }

    // ── Stand / walk ──

    @Nullable
    private static BlockPos findStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos hook) {
        // The hook is ceiling-mounted; the sausages hang at hook.below().
        // Stand one block to the side at floor-level near the sausage.
        BlockPos sausage = hook.below();
        BlockPos[] candidates = {
                sausage.north(), sausage.south(), sausage.east(), sausage.west()
        };
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        BlockPos villagerPos = villager.blockPosition();
        for (BlockPos c : candidates) {
            BlockPos floor = findFloor(level, c);
            if (floor == null) continue;
            double dsq = floor.distSqr(villagerPos);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = floor;
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findFloor(ServerLevel level, BlockPos start) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos().set(start);
        for (int drop = 0; drop <= 4; drop++) {
            BlockState below = level.getBlockState(cursor.below());
            if (!below.isAir() && !below.canBeReplaced()) {
                BlockState at = level.getBlockState(cursor);
                BlockState head = level.getBlockState(cursor.above());
                if ((at.isAir() || at.canBeReplaced())
                        && (head.isAir() || head.canBeReplaced())) {
                    return cursor.immutable();
                }
                return null;
            }
            cursor.move(Direction.DOWN);
        }
        return null;
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
