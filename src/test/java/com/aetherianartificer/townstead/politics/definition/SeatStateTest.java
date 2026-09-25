package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.politics.state.OrganizationInstance;
import com.aetherianartificer.townstead.politics.state.PoliticalActorRef;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalStatus;
import com.aetherianartificer.townstead.politics.state.PolityInstance;
import com.aetherianartificer.townstead.politics.state.SeatInstance;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatStateTest {
    private static final ResourceLocation POLITY = id("test:rivercross");
    private static final SettlementRef SETTLEMENT = new SettlementRef(id("minecraft:overworld"), 4);

    @Test
    void anActorHasOneSeatAndASecondDesignationMovesIt() {
        PoliticalSavedData data = world();
        PoliticalActorRef actor = new PoliticalActorRef(PoliticalActorRef.Kind.POLITY, POLITY);
        SeatInstance hall = seat(actor, new BlockPos(10, 64, 10), 3);
        SeatInstance keep = seat(actor, new BlockPos(40, 70, -8), 9);

        data.putSeat(hall);
        data.putSeat(keep);

        assertEquals(1, data.seats().size());
        assertEquals(keep, data.seat(actor));
        assertFalse(hall.sameHost(keep));
    }

    @Test
    void theSameLecternUnderANewBuildingIdIsTheSameSeat() {
        PoliticalActorRef actor = new PoliticalActorRef(PoliticalActorRef.Kind.POLITY, POLITY);
        BlockPos lectern = new BlockPos(10, 64, 10);

        assertTrue(seat(actor, lectern, 3).sameHost(seat(actor, lectern, 11)));
    }

    @Test
    void aSeatNeedsAnExistingActor() {
        PoliticalSavedData data = new PoliticalSavedData();

        assertThrows(IllegalArgumentException.class, () ->
                data.putSeat(seat(new PoliticalActorRef(PoliticalActorRef.Kind.POLITY, POLITY), new BlockPos(0, 64, 0), 1)));
    }

    @Test
    void removingASeatLeavesTheActor() {
        PoliticalSavedData data = world();
        PoliticalActorRef actor = new PoliticalActorRef(PoliticalActorRef.Kind.POLITY, POLITY);
        data.putSeat(seat(actor, new BlockPos(0, 64, 0), 1));

        data.removeSeat(actor, "charter_removed");

        assertNull(data.seat(actor));
        assertEquals(POLITY, data.polity(POLITY).id());
    }

    private static PoliticalSavedData world() {
        PoliticalSavedData data = new PoliticalSavedData();
        ResourceLocation council = id("test:council");
        data.putOrganization(new OrganizationInstance(council, id("townstead:civic_faction"),
                id("townstead:application"), "Council", "Council", 0xFFFFFF, null, 10L,
                id("townstead:generation"), PoliticalStatus.Organization.ACTIVE, null));
        data.putPolity(new PolityInstance(POLITY, "Rivercross", 0x336699, null, 10L,
                id("townstead:charter"), PoliticalStatus.Polity.ACTIVE, List.of(SETTLEMENT), council));
        return data;
    }

    private static SeatInstance seat(PoliticalActorRef actor, BlockPos lectern, int building) {
        return new SeatInstance(actor, SETTLEMENT, lectern, building, 100L);
    }

    private static ResourceLocation id(String value) {
        return DataPackLang.parseId(value);
    }
}
