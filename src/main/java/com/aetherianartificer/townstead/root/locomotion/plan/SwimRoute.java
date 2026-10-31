package com.aetherianartificer.townstead.root.locomotion.plan;

import com.aetherianartificer.townstead.root.locomotion.SwimStyle;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A planned swim: feet positions, start first.
 *
 * @param longestHold ticks of the longest stretch with the head under water
 * @param toShore     the route ends in the water beside a destination on land
 */
public record SwimRoute(SwimStyle style, List<Vec3> waypoints, double length, int longestHold, boolean toShore) {

    public Vec3 end() {
        return waypoints.get(waypoints.size() - 1);
    }
}
