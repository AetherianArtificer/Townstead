package com.aetherianartificer.townstead.work.site;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The extent is the expensive thing a worksite remembers: deriving it is a flood fill over the
 * world, so the rules about when it may be reused have to be exact. Freshness is a backstop, not
 * the mechanism — an explicit invalidation is what correctness rests on.
 */
class WorksiteExtentTest {

    private static Worksite site() {
        return new Worksite(1L, new WorksiteKey(id("townstead:mca_room"), id("minecraft:overworld"), 7),
                "The Kitchen", 1, 0L, 0L);
    }

    @Test
    void anUncomputedExtentIsNotAnEmptyOne() {
        assertNull(site().extentIfFresh(0L),
                "null means 'ask the world'; an empty set would mean 'this room has no floor'");
    }

    @Test
    void storedExtentIsReusedInsideItsWindow() {
        Worksite site = site();
        Set<Long> cells = Set.of(1L, 2L, 3L);
        site.setExtent(cells, 1000L, Worksites.EXTENT_FRESH_TICKS);

        assertEquals(cells, site.extentIfFresh(1000L));
        assertEquals(cells, site.extentIfFresh(1000L + Worksites.EXTENT_FRESH_TICKS),
                "the last tick of the window still counts as fresh");
    }

    @Test
    void staleExtentIsRefusedRatherThanReturned() {
        Worksite site = site();
        site.setExtent(Set.of(1L), 1000L, Worksites.EXTENT_FRESH_TICKS);

        assertNull(site.extentIfFresh(1001L + Worksites.EXTENT_FRESH_TICKS),
                "past the backstop the answer has to be re-derived, not guessed at");
    }

    @Test
    void invalidationBeatsTheWindow() {
        Worksite site = site();
        site.setExtent(Set.of(1L, 2L), 1000L, Worksites.EXTENT_FRESH_TICKS);
        site.invalidateExtent();

        assertNull(site.extentIfFresh(1000L),
                "a rebuilt wall must not wait out a timer before villagers notice");
    }

    @Test
    void theFreshnessWindowMatchesTheNavSnapshot() {
        assertEquals(80L, Worksites.EXTENT_FRESH_TICKS,
                "this must not make anything staler than the cook path already tolerates");
    }

    private static ResourceLocation id(String raw) {
        //? if >=1.21 {
        return ResourceLocation.parse(raw);
        //?} else {
        /*return new ResourceLocation(raw);
        *///?}
    }
}
