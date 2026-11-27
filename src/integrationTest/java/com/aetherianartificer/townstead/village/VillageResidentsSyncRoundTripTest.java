package com.aetherianartificer.townstead.village;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VillageResidentsSyncRoundTripTest {

    @Test
    void jobStatusCrossesTheWire() {
        var ending = new VillageResidentClientStore.Resident(new UUID(1, 2), "Cora",
                "minecraft:farmer", 1, new int[]{1, 2, 3}, "standard", "Standard", new int[]{22, 6},
                "day", new VillageResidentClientStore.JobStatus(2, 17200, false, "minecraft:composter"));
        var working = new VillageResidentClientStore.Resident(new UUID(3, 4), "Alice",
                "townstead:cook", 3, new int[0], "standard", "", new int[0], "",
                VillageResidentClientStore.JobStatus.WORKING);

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            VillageResidentsSyncPayload.writeResidents(buffer, List.of(ending, working));
            List<VillageResidentClientStore.Resident> read = VillageResidentsSyncPayload.readResidents(buffer);

            assertEquals(0, buffer.readableBytes());
            assertEquals(ending.job(), read.get(0).job());
            assertArrayEquals(ending.shifts(), read.get(0).shifts());
            assertEquals("day", read.get(0).templateId());
            assertEquals(working.job(), read.get(1).job());
            assertEquals("Alice", read.get(1).name());
        } finally {
            buffer.release();
        }
    }
}
