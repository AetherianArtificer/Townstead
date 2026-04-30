package com.aetherianartificer.townstead.shepherd;

import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Walks a shepherd to a Wool Shed and deposits wool into a container
 * inside it. Runs when the shepherd has wool but cannot continue
 * shearing (no inventory room) or has no shearable sheep available — in
 * either case {@link ShepherdWorkTask} bails out and this task takes
 * over.
 *
 * <p>Phases collapse to a single PATH-then-DEPOSIT; once within range
 * of the chosen storage block, the wool is inserted into containers
 * inside the shed via {@link NearbyItemSources#insertIntoBuildingStorage}
 * and the task ends so the brain can reschedule.
 */
public class ShepherdDepositTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 600;
    /** Squared horizontal distance from storage block at which the
     *  shepherd is "close enough" to deposit. ~2 blocks. */
    private static final double ARRIVAL_RANGE_SQ = 4.0;
    private static final float WALK_SPEED = 0.55f;
    private static final int PATH_TIMEOUT_TICKS = 200;

    @Nullable private Building targetShed;
    @Nullable private BlockPos targetStorage;
    private long startedTick;
    private long lastPathTick;

    public ShepherdDepositTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (villager.getVillagerData().getProfession() != VillagerProfession.SHEPHERD) return false;
        if (!ShepherdInventory.hasWool(villager)) return false;
        return findStorage(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        ShedStorage pick = findStorage(level, villager);
        if (pick == null) return;
        targetShed = pick.shed();
        targetStorage = pick.storagePos();
        startedTick = gameTime;
        lastPathTick = gameTime;
        setWalkTarget(villager, targetStorage);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (gameTime - startedTick > MAX_DURATION) return false;
        if (targetStorage == null || targetShed == null) return false;
        if (!ShepherdInventory.hasWool(villager)) return false;
        if (gameTime - lastPathTick > PATH_TIMEOUT_TICKS) return false;
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetStorage == null || targetShed == null) return;
        villager.getLookControl().setLookAt(
                targetStorage.getX() + 0.5,
                targetStorage.getY() + 0.5,
                targetStorage.getZ() + 0.5);

        double dx = villager.getX() - (targetStorage.getX() + 0.5);
        double dz = villager.getZ() - (targetStorage.getZ() + 0.5);
        double horizDsq = dx * dx + dz * dz;
        if (horizDsq <= ARRIVAL_RANGE_SQ) {
            depositWool(level, villager);
            // Task ends naturally next canStillUse check (no wool left, or shed full).
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        } else {
            lastPathTick = gameTime;
            setWalkTarget(villager, targetStorage);
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetShed = null;
        targetStorage = null;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private void depositWool(ServerLevel level, VillagerEntityMCA villager) {
        if (targetShed == null) return;
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(ItemTags.WOOL)) continue;
            NearbyItemSources.insertIntoBuildingStorage(level, villager, stack, targetShed);
            if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        inv.setChanged();
    }

    private record ShedStorage(Building shed, BlockPos storagePos) {}

    @Nullable
    private static ShedStorage findStorage(ServerLevel level, VillagerEntityMCA villager) {
        ShedStorage best = null;
        double bestDsq = Double.MAX_VALUE;
        for (Building shed : ShepherdPenScanner.woolSheds(level, villager)) {
            BlockPos storage = findStorageBlockIn(level, shed);
            if (storage == null) continue;
            double dsq = villager.distanceToSqr(
                    storage.getX() + 0.5, storage.getY() + 0.5, storage.getZ() + 0.5);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = new ShedStorage(shed, storage);
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findStorageBlockIn(ServerLevel level, Building shed) {
        BlockPos p0 = shed.getPos0();
        BlockPos p1 = shed.getPos1();
        if (p0 == null || p1 == null) return null;
        int minX = Math.min(p0.getX(), p1.getX());
        int minY = Math.min(p0.getY(), p1.getY());
        int minZ = Math.min(p0.getZ(), p1.getZ());
        int maxX = Math.max(p0.getX(), p1.getX());
        int maxY = Math.max(p0.getY(), p1.getY());
        int maxZ = Math.max(p0.getZ(), p1.getZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (!shed.containsPos(cursor)) continue;
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be instanceof Container) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos pos) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(pos), WALK_SPEED, 1));
    }
}
