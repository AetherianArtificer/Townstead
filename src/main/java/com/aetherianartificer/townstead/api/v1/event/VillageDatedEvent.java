package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Townstead gave a village its founding day, the first time it saw one of its villagers. Sent once for each village.
 * Days are on Townstead's life-day axis, the one villager ages use, so {@code today - foundedDay} is the village's age
 * in days. A village the world generated is given a past; a village a player founded is founded today.
 * {@code at} is where the villager stood that the village was dated from. Added in revision 2.
 */
public record VillageDatedEvent(
        ServerLevel level,
        VillageId village,
        long foundedDay,
        long today,
        boolean playerFounded,
        BlockPos at
) implements TownsteadEvent {
}
