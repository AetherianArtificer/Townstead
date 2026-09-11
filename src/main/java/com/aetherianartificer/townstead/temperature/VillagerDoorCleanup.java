package com.aetherianartificer.townstead.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Only doors actually opened by MCA villagers are queued; no scanning for player-opened doors. */
public final class VillagerDoorCleanup {
    private VillagerDoorCleanup() {}
    private static final Map<ServerLevel, LinkedHashMap<BlockPos, Long>> PENDING = new WeakHashMap<>();
    public static void opened(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof FenceGateBlock)) return;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) pos = pos.below();
        var queue = PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        queue.put(pos.immutable(), level.getGameTime());
        while (queue.size() > 1024) queue.remove(queue.keySet().iterator().next());
    }
    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getGameTime() % 10 != 0) continue;
            var queue = PENDING.get(level);
            if (queue == null) continue;
            var iterator = queue.entrySet().iterator();
            List<Map.Entry<BlockPos, Long>> deferred = new ArrayList<>();
            int checked = 0;
            while (iterator.hasNext() && checked++ < 128) {
                var entry = iterator.next();
                BlockPos pos = entry.getKey();
                long age = level.getGameTime() - entry.getValue();
                boolean loaded = level.isLoaded(pos);
                if (!loaded) {
                    if (DoorClosePolicy.decide(age, false, true, false, false) != DoorClosePolicy.Decision.FORGET)
                        deferred.add(Map.entry(pos, entry.getValue()));
                    iterator.remove();
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                boolean open = (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock)
                        && state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
                boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)
                        || level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
                var decision = DoorClosePolicy.decide(age, true, open, powered, open && !powered && passageInUse(level, pos));
                if (decision == DoorClosePolicy.Decision.WAIT) {
                    deferred.add(Map.entry(pos, entry.getValue()));
                    iterator.remove();
                    continue;
                }
                iterator.remove();
                if (decision == DoorClosePolicy.Decision.CLOSE) {
                    if (state.getBlock() instanceof DoorBlock door) door.setOpen(null, level, state, pos, false);
                    else net.conczin.mca.entity.ai.brain.tasks.SmarterOpenDoorsTask.setOpen(null, level, state, pos, false);
                }
            }
            deferred.forEach(entry -> queue.put(entry.getKey(), entry.getValue()));
            if (queue.isEmpty()) PENDING.remove(level);
        }
    }
    private static boolean passageInUse(ServerLevel level, BlockPos pos) {
        AABB passage = new AABB(pos).expandTowards(0, 1, 0).inflate(0.25, 0, 0.25);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, passage.inflate(2), LivingEntity::isAlive)) {
            if (entity.getBoundingBox().intersects(passage)) return true;
            if (entity instanceof Mob mob) {
                var path = mob.getNavigation().getPath();
                if (path == null || path.isDone()) continue;
                for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 2); i++) {
                    BlockPos step = path.getNode(i).asBlockPos();
                    if (step.equals(pos) || step.equals(pos.above())) return true;
                }
            }
        }
        return false;
    }
}
