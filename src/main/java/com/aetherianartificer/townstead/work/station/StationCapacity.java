package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * How many jobs a particular kind of station can take, answered by whoever knows that block.
 *
 * <p>The engine can tell that a block is a station and where to stand to work it, because both
 * come from the workstation def. It cannot tell how many items fit on a campfire, whether a
 * skillet is hot, or how a stove lays out its inventory — those live behind each block's own
 * internals. So the engine asks and the block's owner answers.</p>
 *
 * <p>Returning a negative capacity means "not mine, ask the next one", which is how a provider
 * declines a station it does not recognise without claiming it holds nothing.</p>
 */
public interface StationCapacity {

    /** Jobs this station can take right now, 0 when unusable, or negative to defer. */
    int capacity(ServerLevel level, BlockPos pos, StationType type);

    /**
     * The cell that stands for this station when several blocks form one, or null to defer.
     * A multi-block stove is one station however many cells it spans, and every claim, session
     * and stand lookup has to agree on which cell that is.
     */
    default @Nullable BlockPos anchor(ServerLevel level, BlockPos pos) {
        return null;
    }
}
