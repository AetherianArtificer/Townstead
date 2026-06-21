package com.aetherianartificer.townstead.client.species;

import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Spider gravity, stage 2 (camera): rolls the first-person view so the climbed surface becomes the floor,
 * the camera counterpart to the stage-1 model tilt in {@link ClimbRender}. Hooks
 * {@code ViewportEvent.ComputeCameraAngles}, which lets a listener rewrite the camera yaw/pitch/roll just
 * before the view is built.
 *
 * <p>While clung the camera is a decoupled "standing on the wall floor" view built by
 * {@link ClimbState#cameraOrientation} (driven by the mouse via {@link ClimbState}), eased in from the
 * player's normal view by the climb factor. Both orientations are expressed as the true camera-to-world (the
 * inverse of MC's view matrix {@code Rz(roll) Rx(pitch) Ry(yaw+180)}, the same on 1.20.1 and 1.21.1), slerped,
 * then decomposed back into yaw/pitch/roll. Applies whenever the view is reoriented (first person always;
 * third person only if {@link ClimbState#REORIENT_THIRD_PERSON}).</p>
 */
public final class ClimbView {

    private ClimbView() {}

    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    //? if neoforge {
    public static void onComputeCameraAngles(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
    //?} else {
    /*public static void onComputeCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
    *///?}
        if (!ClimbState.reorientedView()) return;
        if (!(event.getCamera().getEntity() instanceof LivingEntity entity)) return;
        float f = ClimbState.factor(entity.getId());
        if (f <= 0f) return;
        Vector3f up = ClimbState.normal(entity.getId());
        if (up == null) return;

        float yaw = (float) event.getYaw();
        float pitch = (float) event.getPitch();
        float roll = (float) event.getRoll();

        // The player's normal camera-to-world (inverse of MC's view matrix Rz(roll) Rx(pitch) Ry(yaw+180)).
        Quaternionf normalCam = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-(yaw + 180f)),
                (float) Math.toRadians(-pitch),
                (float) Math.toRadians(-roll));
        // The wall-frame camera (standing on the wall floor), eased in from the normal view.
        Quaternionf wallCam = ClimbState.cameraOrientation(up, entity.getYRot());
        Quaternionf cam = new Quaternionf(normalCam).slerp(wallCam, f);

        Vector3f e = cam.getEulerAnglesYXZ(new Vector3f());
        float outYaw = -e.y * RAD_TO_DEG - 180f;
        float outPitch = -e.x * RAD_TO_DEG;
        float outRoll = -e.z * RAD_TO_DEG;
        event.setYaw(outYaw);
        event.setPitch(outPitch);
        event.setRoll(outRoll);
    }
}
