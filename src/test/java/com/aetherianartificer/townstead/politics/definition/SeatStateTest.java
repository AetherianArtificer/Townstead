package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SeatInstance;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.aetherianartificer.townstead.politics.definition.PoliticsFixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatStateTest {
    private static final ResourceLocation FACTION = id("test:rivercross");
    private static final SettlementRef SETTLEMENT = new SettlementRef(id("minecraft:overworld"), 4);

    @Test
    void aFactionHasOneSeatAndASecondDesignationMovesIt() {
        PoliticalSavedData data = world();
        SeatInstance hall = seat(new BlockPos(10, 64, 10), 3);
        SeatInstance keep = seat(new BlockPos(40, 70, -8), 9);

        data.putSeat(hall);
        data.putSeat(keep);

        assertEquals(1, data.seats().size());
        assertEquals(keep, data.seat(FACTION));
        assertFalse(hall.sameHost(keep));
    }

    @Test
    void theSameLecternUnderANewBuildingIdIsTheSameSeat() {
        BlockPos lectern = new BlockPos(10, 64, 10);

        assertTrue(seat(lectern, 3).sameHost(seat(lectern, 11)));
    }

    @Test
    void aSeatNeedsAnExistingFaction() {
        PoliticalSavedData data = new PoliticalSavedData();

        assertThrows(IllegalArgumentException.class, () -> data.putSeat(seat(new BlockPos(0, 64, 0), 1)));
    }

    @Test
    void removingASeatLeavesTheFaction() {
        PoliticalSavedData data = world();
        data.putSeat(seat(new BlockPos(0, 64, 0), 1));

        data.removeSeat(FACTION, "charter_removed");

        assertNull(data.seat(FACTION));
        assertEquals(FACTION, data.faction(FACTION).id());
    }

    private static PoliticalSavedData world() {
        PoliticalSavedData data = new PoliticalSavedData();
        data.putFaction(new Faction(FACTION, id("townstead:player_faction"), "Rivercross", 0x336699, null, 10L,
                id("townstead:charter"), Faction.Status.ACTIVE, List.of(SETTLEMENT), null));
        return data;
    }

    private static SeatInstance seat(BlockPos lectern, int building) {
        return new SeatInstance(FACTION, SETTLEMENT, lectern, building, 100L);
    }
}
