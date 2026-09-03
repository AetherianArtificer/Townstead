package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.compat.beachparty.BeachpartyChairAdapter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BeachpartySeatingDataTest {
    @Test
    void nativeChairDefinitionsKeepExactBaseAnchorsAndTallHalfClaims() throws Exception {
        HangoutSpot beach = spot("beachparty_beach_chair");
        HangoutSpot stool = spot("beachparty_palm_bar_stool");
        HangoutSpot palm = spot("beachparty_palm_chair");
        HangoutSpot hooded = spot("beachparty_hooded_beach_chair");

        for (HangoutSpot spot : java.util.List.of(beach, stool, palm, hooded)) {
            assertEquals(BeachpartyChairAdapter.ID, spot.adapter());
            assertPos(spot.canonicalOffset(), 0, 0, 0);
        }
        assertEquals(0, beach.linkedOffsets().size());
        assertEquals(0, stool.linkedOffsets().size());
        assertPos(palm.linkedOffsets().iterator().next(), 0, 1, 0);
        assertPos(hooded.linkedOffsets().iterator().next(), 0, 1, 0);
        assertNotNull(palm.availableWhen(), "only the lower half may become a canonical anchor");
        assertNotNull(hooded.availableWhen(), "upper-half scanning would duplicate the native chair");
    }

    @Test
    void nativeOffsetsMatchBeachpartySourceContract() {
        assertEquals(0.30D, BeachpartyChairAdapter.seatHeight(id("beachparty:beach_chair")));
        assertEquals(0.45D, BeachpartyChairAdapter.seatHeight(id("beachparty:hooded_beach_chair")));
        assertEquals(0.55D, BeachpartyChairAdapter.seatHeight(id("beachparty:palm_chair")));
        assertEquals(0.60D, BeachpartyChairAdapter.seatHeight(id("beachparty:palm_bar_stool")));
        assertNull(BeachpartyChairAdapter.seatHeight(id("beachparty:beach_sun_lounger")));
    }

    @Test
    void loungeSurfacesUseProneAdapterAndAuthoredRestRecovery() throws Exception {
        HangoutSpot towel = spot("beachparty_towel");
        HangoutSpot lounger = spot("beachparty_sun_lounger");

        for (HangoutSpot spot : java.util.List.of(towel, lounger)) {
            assertEquals(id("townstead:recline"), spot.adapter());
            assertEquals(id("townstead:relax"), spot.posture());
            assertEquals(1, spot.linkedOffsets().size());
            assertPos(spot.linkedOffsets().iterator().next(), 0, 0, -1);
            assertEquals(true, spot.facingRelativeLinkedOffsets());
            assertEquals(true, spot.facingRelativeEmbodimentOffset());
            assertNotNull(spot.availableWhen(), "only the head may become a canonical anchor");
            assertNotNull(spot.rest());
        }
        assertEquals(0.0625D, towel.embodimentOffset().y);
        assertEquals(0.49375D, lounger.embodimentOffset().y);
        assertEquals(0.3F, towel.rest().fatigueRecovery());
        assertEquals(0.6F, lounger.rest().fatigueRecovery());
        assertNull(BeachpartyChairAdapter.seatHeight(id("beachparty:beach_sun_lounger")),
                "the lounger must never enter Beachparty's chair lifecycle");
        assertNull(getClass().getResourceAsStream(
                "/data/townstead/hangout_spot/beachparty_seating.json"));
    }

    @Test
    void bedFootClaimsRotateBehindTheCanonicalHead() {
        BlockPos head = new BlockPos(10, 64, 10);
        BlockPos footOffset = new BlockPos(0, 0, -1);
        assertPos(HangoutSpot.facingRelative(head, footOffset, Direction.NORTH), 10, 64, 11);
        assertPos(HangoutSpot.facingRelative(head, footOffset, Direction.EAST), 9, 64, 10);
        assertPos(HangoutSpot.facingRelative(head, footOffset, Direction.SOUTH), 10, 64, 9);
        assertPos(HangoutSpot.facingRelative(head, footOffset, Direction.WEST), 11, 64, 10);
    }

    @Test
    void restRecoveryRejectsNonPositiveOrNonFiniteValues() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new HangoutSpot.RestBonus(0F));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new HangoutSpot.RestBonus(Float.NaN));
    }

    private static HangoutSpot spot(String name) throws Exception {
        JsonObject json = resource("/data/townstead/hangout_spot/" + name + ".json");
        return HangoutData.parseSpot(id("townstead:" + name), json);
    }

    private static JsonObject resource(String path) throws Exception {
        try (var stream = BeachpartySeatingDataTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static void assertPos(net.minecraft.core.BlockPos pos, int x, int y, int z) {
        assertEquals(x, pos.getX());
        assertEquals(y, pos.getY());
        assertEquals(z, pos.getZ());
    }

    private static ResourceLocation id(String raw) { return ResourceLocation.tryParse(raw); }
}
