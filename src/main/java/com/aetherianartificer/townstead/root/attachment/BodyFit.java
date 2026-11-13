package com.aetherianartificer.townstead.root.attachment;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Whether a body with a volume fits where a path would take it. Inflating the square hitbox would ask a
 * long abdomen for a three-wide corridor, so a planner checks the volume as it would actually sit at
 * each step: facing the way it walks, and swung round at any corner.
 */
public final class BodyFit {

    // A walker needs a little room to get through; a box exactly filling a gap would stall on its edges.
    private static final double CLEARANCE = 0.06;
    private static final int STUCK_CHECK = 40;
    private static final double STUCK_MOVED_SQR = 0.04;

    private static final Map<VillagerEntityMCA, Vec3> LAST_SEEN = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private BodyFit() {}

    /** Whether this mob may step between two path nodes. Nothing to check for most bodies. */
    public static boolean canStep(Mob mob, Node from, Node to) {
        if (!(mob instanceof VillagerEntityMCA villager) || BodyVolumes.blocking(villager).isEmpty()) return true;
        float yaw = walkYaw(to.x - from.x, to.z - from.z);
        if (!fitsAt(villager, to.x, to.y, to.z, yaw)) return false;
        Node before = from.cameFrom;
        if (before == null) return true;
        float previous = walkYaw(from.x - before.x, from.z - before.z);
        // Turning swings the part round where it stands, so the new facing has to fit there too.
        return Mth.degreesDifferenceAbs(previous, yaw) < 1f || fitsAt(villager, from.x, from.y, from.z, yaw);
    }

    /** The body yaw of someone walking along this step. */
    static float walkYaw(int dx, int dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static boolean fitsAt(VillagerEntityMCA villager, int x, int y, int z, float yaw) {
        for (AABB box : BodyVolumes.blockingAt(villager, yaw)) {
            AABB placed = box.move(x + 0.5, y, z + 0.5).deflate(CLEARANCE);
            if (!villager.level().noCollision(villager, placed)) return false;
        }
        return true;
    }

    /** Whether a villager can get to a place, volume and all. Used so nobody is given a home they can't enter. */
    public static boolean canReach(VillagerEntityMCA villager, BlockPos target) {
        if (BodyVolumes.blocking(villager).isEmpty()) return true;
        Path path = villager.getNavigation().createPath(target, 1);
        return path != null && path.canReach();
    }

    /** A villager whose volume has wedged it gives up the walk rather than pushing forever. */
    public static void tick(VillagerEntityMCA villager) {
        if (villager.tickCount % STUCK_CHECK != 0) return;
        if (BodyVolumes.blocking(villager).isEmpty()) {
            LAST_SEEN.remove(villager);
            return;
        }
        Vec3 now = villager.position();
        Vec3 before = LAST_SEEN.put(villager, now);
        if (before != null && villager.getNavigation().isInProgress() && before.distanceToSqr(now) < STUCK_MOVED_SQR) {
            villager.getNavigation().stop();
        }
    }
}
