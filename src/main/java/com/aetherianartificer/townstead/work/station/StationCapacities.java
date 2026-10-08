package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The registry behind {@link StationCapacity}. Providers are asked in registration order and the
 * first one to claim a station answers for it.
 *
 * <p>A station nobody claims is worth one job. That is deliberately generous: a station only
 * exists because a workstation def declared it, and refusing to work a correctly declared block
 * merely because no compat recognised it is the failure this seam was built to remove.</p>
 */
public final class StationCapacities {

    private static final List<StationCapacity> PROVIDERS = new CopyOnWriteArrayList<>();

    private StationCapacities() {}

    public static void register(StationCapacity provider) {
        if (provider != null) PROVIDERS.add(provider);
    }

    /** Vanilla stations the engine can answer for itself. Registered at startup. */
    public static void bootstrap() {
        register(new CampfireCapacity());
    }

    /** The cell that stands for this station, collapsing multi-block stations to one anchor. */
    public static BlockPos anchor(ServerLevel level, BlockPos pos) {
        if (pos == null) return null;
        WorkstationDef def = Workstations.byState(level.getBlockState(pos));
        StationAdapters.Adapter adapter = StationAdapters.forDef(def);
        if (def != null && adapter != null) {
            BlockPos anchored = adapter.anchor(level, pos, def);
            if (anchored != null) return anchored;
        }
        for (StationCapacity provider : PROVIDERS) {
            BlockPos anchored = provider.anchor(level, pos);
            if (anchored != null) return anchored;
        }
        return pos;
    }

    /**
     * Jobs this station can take right now. Resolves the anchor first, refuses an open-topped
     * station with something set on it, then asks the providers.
     */
    public static int capacity(ServerLevel level, BlockPos pos, @Nullable StationType type) {
        if (level == null || pos == null || type == null) return 0;
        BlockPos anchor = anchor(level, pos);
        BlockState state = level.getBlockState(anchor);
        if (Stations.coverBlocksWork(level, anchor, state)) return 0;
        WorkstationDef def = Workstations.byState(state);
        StationAdapters.Adapter adapter = StationAdapters.forDef(def);
        if (def != null && adapter != null) {
            int capacity = adapter.capacity(level, anchor, def);
            if (capacity >= 0) return capacity;
        }
        for (StationCapacity provider : PROVIDERS) {
            int capacity = provider.capacity(level, anchor, type);
            if (capacity >= 0) return capacity;
        }
        return 1;
    }

    /** A campfire holds four items and is vanilla, so the engine counts it without help. */
    private static final class CampfireCapacity implements StationCapacity {

        @Override
        public int capacity(ServerLevel level, BlockPos pos, StationType type) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.CAMPFIRES)) return -1;
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof CampfireBlockEntity campfire)) return 0;
            int free = 0;
            for (ItemStack slot : campfire.getItems()) {
                if (slot.isEmpty()) free++;
            }
            return free;
        }
    }
}
