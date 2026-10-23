package com.aetherianartificer.townstead.root.locomotion.fly;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteFollowerTest {

    private static final List<Vec3> L_ROUTE = List.of(
            new Vec3(0, 0, 0), new Vec3(10, 0, 0), new Vec3(10, 0, 10));

    @Test
    void measuresTheWholeRoute() {
        RouteFollower follower = new RouteFollower(L_ROUTE);
        assertEquals(20.0, follower.length(), 1.0E-9);
        assertEquals(20.0, follower.remaining(), 1.0E-9);
    }

    @Test
    void projectsTheBodyOntoTheNearestLeg() {
        RouteFollower follower = new RouteFollower(L_ROUTE);
        follower.update(new Vec3(4, 0, 1));
        assertEquals(4.0, follower.progress(), 1.0E-9);
        follower.update(new Vec3(11, 0, 3));
        assertEquals(13.0, follower.progress(), 1.0E-9);
    }

    @Test
    void neverSlidesBackwards() {
        RouteFollower follower = new RouteFollower(L_ROUTE);
        follower.update(new Vec3(8, 0, 0));
        follower.update(new Vec3(2, 0, 0));
        assertEquals(8.0, follower.progress(), 1.0E-9);
    }

    @Test
    void interpolatesPointsAheadAcrossCorners() {
        RouteFollower follower = new RouteFollower(L_ROUTE);
        Vec3 corner = follower.at(10.0);
        assertEquals(10.0, corner.x, 1.0E-9);
        assertEquals(0.0, corner.z, 1.0E-9);
        Vec3 past = follower.at(15.0);
        assertEquals(10.0, past.x, 1.0E-9);
        assertEquals(5.0, past.z, 1.0E-9);
        assertTrue(follower.at(99.0).distanceTo(new Vec3(10, 0, 10)) < 1.0E-9);
    }
}
