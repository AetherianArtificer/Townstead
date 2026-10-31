package com.aetherianartificer.townstead.compat.otectus;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureAmnestyTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final AABB AREA = new AABB(0, 0, 0, 100, 100, 100);
    private static final Vec3 INSIDE = new Vec3(50, 64, 50);

    @AfterEach
    void reset() {
        CaptureAmnesty.clear();
    }

    @Test
    void coversTheVillageWhileTheTotemReports() {
        CaptureAmnesty.report(OVERWORLD, 1L, AREA, 1000);

        assertTrue(CaptureAmnesty.covers(OVERWORLD, INSIDE, 1000));
        assertTrue(CaptureAmnesty.covers(OVERWORLD, INSIDE, 1010));
    }

    @Test
    void keepsCoveringForHalfAMinuteAfterTheLastReport() {
        CaptureAmnesty.report(OVERWORLD, 1L, AREA, 1000);

        assertTrue(CaptureAmnesty.covers(OVERWORLD, INSIDE, 1000 + CaptureAmnesty.TAIL));
        assertFalse(CaptureAmnesty.covers(OVERWORLD, INSIDE, 1000 + CaptureAmnesty.TAIL + 21));
    }

    @Test
    void aRefreshExtendsTheWindow() {
        CaptureAmnesty.report(OVERWORLD, 1L, AREA, 1000);
        CaptureAmnesty.report(OVERWORLD, 1L, AREA, 1500);

        assertTrue(CaptureAmnesty.covers(OVERWORLD, INSIDE, 2000));
    }

    @Test
    void dropsAWindowLeftByAWorldWithALaterClock() {
        CaptureAmnesty.report(OVERWORLD, 1L, AREA, 1_000_000);

        assertFalse(CaptureAmnesty.covers(OVERWORLD, INSIDE, 5000));
        assertFalse(CaptureAmnesty.covers(OVERWORLD, INSIDE, 1_000_000));
    }

    @Test
    void doesNotCoverOtherPlaces() {
        CaptureAmnesty.report(OVERWORLD, 1L, AREA, 1000);

        assertFalse(CaptureAmnesty.covers(OVERWORLD, new Vec3(500, 64, 500), 1000));
        assertFalse(CaptureAmnesty.covers("minecraft:the_nether", INSIDE, 1000));
    }

    @Test
    void anotherTotemsWindowDoesNotKeepAnExpiredOneAlive() {
        CaptureAmnesty.report(OVERWORLD, 1L, AREA, 1000);
        CaptureAmnesty.report(OVERWORLD, 2L, new AABB(1000, 0, 1000, 1100, 100, 1100), 5000);

        assertFalse(CaptureAmnesty.covers(OVERWORLD, INSIDE, 5000));
        assertTrue(CaptureAmnesty.covers(OVERWORLD, new Vec3(1050, 64, 1050), 5000));
    }
}
