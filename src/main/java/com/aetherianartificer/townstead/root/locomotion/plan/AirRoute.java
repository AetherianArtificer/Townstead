package com.aetherianartificer.townstead.root.locomotion.plan;

import com.aetherianartificer.townstead.root.locomotion.FlightStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A planned flight: feet positions to pass through, takeoff first and landing spot last.
 *
 * @param lift   height the route must gain beyond free gliding (what it costs in stamina)
 * @param perch  the landing spot sits well above the destination (a roof); the bearer
 *               must fly down again rather than walk off
 */
public record AirRoute(FlightStyle style, List<Vec3> waypoints, BlockPos landing, double length,
                       double lift, double liftBudget, boolean perch) {

    public Vec3 end() {
        return waypoints.get(waypoints.size() - 1);
    }
}
