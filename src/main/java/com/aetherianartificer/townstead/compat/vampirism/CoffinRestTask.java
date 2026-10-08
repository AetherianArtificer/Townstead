package com.aetherianartificer.townstead.compat.vampirism;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

/**
 * A vampire rests in a coffin rather than a bed: when it is time to rest, it walks to its coffin
 * (remembered once found, a free horizontal one within a chunk of it otherwise) and lies down in
 * it. Without a coffin it keeps its own bed. Runs after MCA's rest behaviors so its walk wins.
 */
public class CoffinRestTask extends Behavior<VillagerEntityMCA> {
    private static final TagKey<Block> COFFINS = TagKey.create(Registries.BLOCK, ResourceLocation.tryParse("vampirism:coffin"));
    private static final String COFFIN = "townstead:coffin";
    private static final int SEARCH_INTERVAL = 200;
    private static final int SEARCH_HEIGHT = 12;
    private static final double REACH_SQR = 2.25;
    private static final float WALK_SPEED = 0.6f;

    private @Nullable BlockPos coffin;
    private long nextSearch;

    public CoffinRestTask() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), 600);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (villager.isSleeping() || !villager.getBrain().isActive(Activity.REST) || !VampireVillagers.isVampire(villager)) return false;
        coffin = remembered(level, villager);
        if (coffin == null && level.getGameTime() >= nextSearch) {
            nextSearch = level.getGameTime() + SEARCH_INTERVAL;
            coffin = find(level, villager.blockPosition());
            if (coffin != null) villager.getPersistentData().putLong(COFFIN, coffin.asLong());
        }
        return coffin != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (coffin != null) BehaviorUtils.setWalkAndLookTargetMemories(villager, coffin, WALK_SPEED, 1);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return coffin != null && !villager.isSleeping() && villager.getBrain().isActive(Activity.REST) && free(level.getBlockState(coffin));
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (coffin == null) return;
        if (villager.distanceToSqr(coffin.getX() + 0.5, coffin.getY(), coffin.getZ() + 0.5) > REACH_SQR) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, coffin, WALK_SPEED, 1);
            return;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.startSleeping(coffin);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        coffin = null;
    }

    private static @Nullable BlockPos remembered(ServerLevel level, VillagerEntityMCA villager) {
        var data = villager.getPersistentData();
        if (!data.contains(COFFIN)) return null;
        BlockPos pos = BlockPos.of(data.getLong(COFFIN));
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.is(COFFINS) && head(state)) return free(state) ? pos : null;
        data.remove(COFFIN);
        return null;
    }

    /** The nearest free coffin head in the 3x3 chunks around {@code from}; sections without one are skipped. */
    private static @Nullable BlockPos find(ServerLevel level, BlockPos from) {
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
                    if (!section.maybeHas(state -> state.is(COFFINS))) continue;
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.is(COFFINS) || !head(state) || !free(state)) continue;
                        BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                        double sq = pos.distSqr(from);
                        if (sq < bestSq) { bestSq = sq; best = pos; }
                    }
                }
            }
        }
        return best;
    }

    /** A horizontal coffin nobody is lying in. */
    private static boolean free(BlockState state) {
        return state.is(COFFINS) && state.hasProperty(BedBlock.OCCUPIED) && !state.getValue(BedBlock.OCCUPIED)
                && !"true".equals(value(state, "vertical"));
    }

    private static boolean head(BlockState state) {
        return "head".equals(value(state, "part"));
    }

    private static @Nullable String value(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) return serialized(property, state);
        }
        return null;
    }

    private static <T extends Comparable<T>> String serialized(Property<T> property, BlockState state) {
        return property.getName(state.getValue(property));
    }
}
