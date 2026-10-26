package com.aetherianartificer.townstead.rest;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Sleeping in rest spots that are not beds. Vanilla's sleep behavior only lies down in a bed, so a
 * villager whose home is a coffin or a bedroll walks there and lies down through this. A villager
 * bound to some spots (a vampire to coffins) gives up a home it may not use, so MCA finds it one it
 * may, and without a home sleeps in the nearest free one it is allowed. Runs after MCA's rest
 * behaviors so its walk wins.
 */
public class RestSpotSleepTask extends Behavior<VillagerEntityMCA> {
    private static final String REMEMBERED = "townstead:rest_spot";
    private static final int SEARCH_INTERVAL = 200;
    private static final int SEARCH_HEIGHT = 12;
    private static final double REACH_SQR = 2.25;
    private static final float WALK_SPEED = 0.6f;

    private @Nullable BlockPos target;
    private long nextSearch;

    public RestSpotSleepTask() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), 600);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (villager.isSleeping() || !villager.getBrain().isActive(Activity.REST) || RestSpots.all().isEmpty()) return false;
        dropForbiddenHome(level, villager);
        target = home(level, villager);
        if (target == null && !RestSpots.boundTo(villager).isEmpty()) {
            target = remembered(level, villager);
            if (target == null && level.getGameTime() >= nextSearch) {
                nextSearch = level.getGameTime() + SEARCH_INTERVAL;
                target = find(level, villager);
                if (target != null) villager.getPersistentData().putLong(REMEMBERED, target.asLong());
            }
        }
        return target != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target != null) BehaviorUtils.setWalkAndLookTargetMemories(villager, target, WALK_SPEED, 1);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return target != null && !villager.isSleeping() && villager.getBrain().isActive(Activity.REST)
                && free(level.getBlockState(target)) && RestSpots.permits(villager, level.getBlockState(target));
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null) return;
        if (villager.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) > REACH_SQR) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, target, WALK_SPEED, 1);
            return;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.startSleeping(target);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        target = null;
    }

    /** A home this villager may not sleep in (a bed, for a vampire) is let go, so MCA finds another. */
    private static void dropForbiddenHome(ServerLevel level, VillagerEntityMCA villager) {
        Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isEmpty() || !home.get().dimension().equals(level.dimension())) return;
        BlockPos pos = home.get().pos();
        if (!level.isLoaded(pos) || RestSpots.permits(villager, level.getBlockState(pos))) return;
        villager.releasePoi(MemoryModuleType.HOME);
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
    }

    /** The villager's home when it is a rest spot vanilla cannot sleep in, and free. */
    private static @Nullable BlockPos home(ServerLevel level, VillagerEntityMCA villager) {
        Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isEmpty() || !home.get().dimension().equals(level.dimension())) return null;
        BlockPos pos = home.get().pos();
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.BEDS) || RestSpots.used(villager, state) == null || !free(state)) return null;
        return pos;
    }

    private static @Nullable BlockPos remembered(ServerLevel level, VillagerEntityMCA villager) {
        var data = villager.getPersistentData();
        if (!data.contains(REMEMBERED)) return null;
        BlockPos pos = BlockPos.of(data.getLong(REMEMBERED));
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (RestSpots.permits(villager, state) && !RestSpots.at(state).isEmpty()) return free(state) ? pos : null;
        data.remove(REMEMBERED);
        return null;
    }

    /** The nearest free spot this villager may use in the 3x3 chunks around it; empty sections are skipped. */
    private static @Nullable BlockPos find(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos from = villager.blockPosition();
        ChunkPos center = new ChunkPos(from);
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk(center.x + dx, center.z + dz)) continue;
                LevelChunk chunk = level.getChunk(center.x + dx, center.z + dz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                    if (section.hasOnlyAir() || baseY + 16 < from.getY() - SEARCH_HEIGHT || baseY > from.getY() + SEARCH_HEIGHT) continue;
                    if (!section.maybeHas(state -> !RestSpots.at(state).isEmpty())) continue;
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (RestSpots.at(state).isEmpty() || !RestSpots.permits(villager, state) || !free(state)) continue;
                        BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                        double sq = pos.distSqr(from);
                        if (sq < bestSq) { bestSq = sq; best = pos; }
                    }
                }
            }
        }
        return best;
    }

    /** Nobody is lying in it. */
    private static boolean free(BlockState state) {
        return !state.hasProperty(BedBlock.OCCUPIED) || !state.getValue(BedBlock.OCCUPIED);
    }
}
