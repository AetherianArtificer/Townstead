package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.OptionalInt;

/**
 * Fire took hold in a building of a village: one MCA registers, or else a piece of the village as the world generated
 * it. Sent at most once a day for each building. {@code buildingCenter} is the middle of that building or piece;
 * {@code buildingId} is MCA's id for it, empty for a generated piece. Added in revision 2.
 */
public record VillageFireEvent(
        ServerLevel level,
        VillageId village,
        BlockPos buildingCenter,
        OptionalInt buildingId,
        long gameTime
) implements TownsteadEvent {
}
