package com.aetherianartificer.townstead.client.species;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Spider gravity, movement: while a local player is clung in first person, WASD moves along the wall surface
 * relative to where they look (the wall-frame look from {@link ClimbLook}), so they climb up/down/around by
 * looking. Forward goes toward the look direction flattened onto the wall, strafe along the surface; no input
 * sticks in place. Replaces the vanilla push-into-wall climb while clung. Client-side only (player movement is
 * client-authoritative); third person and villagers keep the vanilla climb.
 */
public final class ClimbMove {

    private ClimbMove() {}

    private static final double SPEED = 0.12; // along-the-wall move speed (blocks/tick)
    private static final double STICK = 0.08; // pull toward the wall each tick so the player stays attached
    private static final int GRACE = 8;       // ticks after release that vanilla climb stays suppressed

    // Counts down after the controller last drove the player; while > 0 the vanilla climb is suppressed so it
    // can't fight the controller (e.g. climb the player back up at the wall base during a descent dismount).
    private static int controlGrace;

    /** Decremented once per client tick (from ClimbAnim) so the suppression window closes after release. */
    public static void tickGrace() {
        if (controlGrace > 0) controlGrace--;
    }

    /**
     * Whether vanilla climb should stay suppressed for this entity. Only during the dismount at the wall base
     * (on the ground): while still up on a wall (airborne) vanilla must hold normally, so the controller
     * handing off (e.g. switching to third person) does not drop the player.
     */
    public static boolean isSuppressing(net.minecraft.world.entity.LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        return e == mc.player && controlGrace > 0 && mc.player.onGround();
    }

    /** Drives look-relative wall movement for a clung local player; true when it took over (cancel vanilla). */
    public static boolean tryTravel(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player) return false;
        if (mc.player.input.jumping) return false; // jump = let go, fall via vanilla
        int id = player.getId();
        if (ClimbAnim.factor(id) <= 0f) return false; // clung at all (robust to ground flicker on steps)
        Vector3f n = ClimbAnim.normal(id);
        if (n == null) return false;
        Vector3f normal = new Vector3f(n).normalize();

        // First person uses the reoriented wall-frame look. Third person has no reoriented camera, so it keeps
        // vanilla climb on walls (push-to-climb); but a ceiling has nothing to push into, so the controller
        // must hold it there too, driven from the player's plain look (which the orbit camera turns).
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        boolean ceiling = normal.y() < -0.5f;
        if (!firstPerson && !ceiling) return false;

        Vector3f moveFwd;
        if (firstPerson) {
            moveFwd = ClimbLook.wallLookForward(normal, player.getYRot());
        } else {
            Vec3 v = player.getViewVector(1.0f);
            moveFwd = new Vector3f((float) v.x, (float) v.y, (float) v.z);
        }
        // Flatten the look onto the surface = "forward" on it.
        moveFwd.sub(new Vector3f(normal).mul(moveFwd.dot(normal)));
        if (moveFwd.lengthSquared() < 1.0e-4f) return false;
        moveFwd.normalize();
        Vector3f strafe = new Vector3f(normal).cross(moveFwd).normalize();

        Vector3f dir = new Vector3f(moveFwd).mul(player.zza).add(strafe.mul(player.xxa));
        double vx = -normal.x() * STICK;
        double vy = -normal.y() * STICK;
        double vz = -normal.z() * STICK;
        if (dir.lengthSquared() > 1.0e-4f) {
            dir.normalize().mul((float) SPEED);
            vx += dir.x();
            vy += dir.y();
            vz += dir.z();
        }
        Vec3 vel = new Vec3(vx, vy, vz);
        player.setDeltaMovement(vel);
        player.move(MoverType.SELF, vel);
        player.setDeltaMovement(Vec3.ZERO); // crisp stop, no momentum carry
        player.resetFallDistance();
        controlGrace = GRACE; // keep vanilla climb suppressed through the dismount
        return true;
    }
}
