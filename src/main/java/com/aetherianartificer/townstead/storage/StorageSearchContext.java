package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.TownsteadConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
//?} else if forge {
/*import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
*///?}

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

public final class StorageSearchContext {
    private final ServerLevel level;
    private final Map<Long, BlockState> stateCache;
    private final Map<Long, Boolean> protectedStorageCache;
    private final Map<HandlerKey, IItemHandler> handlerCache;
    private final Map<Long, BlockEntity> blockEntityCache;
    private final Set<Long> missingBlockEntities;

    public StorageSearchContext(ServerLevel level) {
        this(level, 256, 64);
    }

    public StorageSearchContext(ServerLevel level, int expectedObservedBlocks, int expectedHandlers) {
        this.level = level;
        int observedCapacity = Math.max(16, expectedObservedBlocks);
        this.stateCache = new HashMap<>(observedCapacity);
        this.protectedStorageCache = new HashMap<>(observedCapacity);
        this.handlerCache = new HashMap<>(Math.max(16, expectedHandlers));
        this.blockEntityCache = new HashMap<>(observedCapacity / 4);
        this.missingBlockEntities = new HashSet<>(observedCapacity / 4);
    }

    public ObservedBlock observe(BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        BlockState state = getState(immutablePos);
        BlockEntity blockEntity = getBlockEntity(immutablePos);
        boolean protectedStorage = isProtectedStorage(immutablePos, state);
        return new ObservedBlock(immutablePos, state, blockEntity, protectedStorage);
    }

    public BlockState getState(BlockPos pos) {
        long key = pos.asLong();
        BlockState state = stateCache.get(key);
        if (state != null) {
            return state;
        }
        state = level.getBlockState(pos);
        stateCache.put(key, state);
        Profiler.recordObservedBlock();
        return state;
    }

    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        long key = pos.asLong();
        BlockEntity blockEntity = blockEntityCache.get(key);
        if (blockEntity != null) {
            return blockEntity;
        }
        if (missingBlockEntities.contains(key)) {
            return null;
        }
        blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntityCache.put(key, blockEntity);
        } else {
            missingBlockEntities.add(key);
        }
        return blockEntity;
    }

    public boolean isProtectedStorage(BlockPos pos, BlockState state) {
        long key = pos.asLong();
        Boolean cached = protectedStorageCache.get(key);
        if (cached != null) {
            return cached;
        }
        boolean protectedStorage = TownsteadConfig.isProtectedStorage(state);
        protectedStorageCache.put(key, protectedStorage);
        return protectedStorage;
    }

    public @Nullable IItemHandler getItemHandler(BlockPos pos, @Nullable Direction side) {
        HandlerKey key = new HandlerKey(pos.asLong(), side);
        if (handlerCache.containsKey(key)) {
            return handlerCache.get(key);
        }
        ObservedBlock observed = observe(pos);
        IItemHandler handler = lookupItemHandler(observed.pos(), observed.blockEntity(), side);
        handlerCache.put(key, handler);
        Profiler.recordHandlerLookup();
        return handler;
    }

    public void forEachUniqueItemHandler(BlockPos pos, UniqueHandlerConsumer consumer) {
        Set<Integer> seen = new HashSet<>();
        IItemHandler unsided = getItemHandler(pos, null);
        if (unsided != null && seen.add(System.identityHashCode(unsided))) {
            consumer.accept(null, unsided);
        }
        for (Direction side : Direction.values()) {
            IItemHandler sided = getItemHandler(pos, side);
            if (sided == null) continue;
            if (!seen.add(System.identityHashCode(sided))) continue;
            consumer.accept(side, sided);
        }
    }

    public void forEachUniqueItemHandlerExcept(BlockPos pos, @Nullable Direction excludedSide, UniqueHandlerConsumer consumer) {
        Set<Integer> seen = new HashSet<>();
        for (Direction side : Direction.values()) {
            if (side == excludedSide) continue;
            IItemHandler sided = getItemHandler(pos, side);
            if (sided == null) continue;
            if (!seen.add(System.identityHashCode(sided))) continue;
            consumer.accept(side, sided);
        }
    }

    private @Nullable IItemHandler lookupItemHandler(BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
        //? if neoforge {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        //?} else if forge {
        /*if (blockEntity == null) return null;
        if (side != null) return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        *///?}
    }

    public record ObservedBlock(BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, boolean protectedStorage) {}

    @FunctionalInterface
    public interface UniqueHandlerConsumer {
        void accept(@Nullable Direction side, IItemHandler handler);
    }

    private record HandlerKey(long posKey, @Nullable Direction side) {}

    public static final class Profiler {
        private static final LongAdder OBSERVED_BLOCKS = new LongAdder();
        private static final LongAdder HANDLER_LOOKUPS = new LongAdder();

        private Profiler() {}

        static void recordObservedBlock() {
            OBSERVED_BLOCKS.increment();
        }

        static void recordHandlerLookup() {
            HANDLER_LOOKUPS.increment();
        }

        public static Snapshot snapshot() {
            return new Snapshot(OBSERVED_BLOCKS.sum(), HANDLER_LOOKUPS.sum());
        }

        public static Snapshot snapshotAndReset() {
            return new Snapshot(OBSERVED_BLOCKS.sumThenReset(), HANDLER_LOOKUPS.sumThenReset());
        }
    }

    public record Snapshot(long observedBlocks, long handlerLookups) {}
}
