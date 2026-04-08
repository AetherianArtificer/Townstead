package com.aetherianartificer.townstead.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Smoothly rotates the player's camera to face a target entity during dialogue,
 * and smoothly restores the original orientation on close.
 */
public class DialogueCameraController {
    private final float originalYaw;
    private final float originalPitch;
    private final Entity target;
    private float currentYaw;
    private float currentPitch;
    private boolean restoring;
    private int restoreTicks;
    private static final float LERP_SPEED = 0.1f;
    private static final float RESTORE_SPEED = 0.15f;
    private static final int MAX_RESTORE_TICKS = 15;

    public DialogueCameraController(Entity target) {
        LocalPlayer player = Minecraft.getInstance().player;
        this.target = target;
        this.originalYaw = player != null ? player.getYRot() : 0;
        this.originalPitch = player != null ? player.getXRot() : 0;
        this.currentYaw = originalYaw;
        this.currentPitch = originalPitch;
    }

    public void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (restoring) {
            tickRestore(player);
            return;
        }

        if (target == null || target.isRemoved()) return;

        float targetYaw = computeTargetYaw(player);
        float targetPitch = computeTargetPitch(player);

        currentYaw = lerpAngle(LERP_SPEED, currentYaw, targetYaw);
        currentPitch = Mth.lerp(LERP_SPEED, currentPitch, targetPitch);

        player.setYRot(currentYaw);
        player.setXRot(currentPitch);
        player.yRotO = currentYaw;
        player.xRotO = currentPitch;
    }

    /**
     * Begin smoothly restoring the camera to the original orientation.
     * Call this when closing the dialogue. The controller will continue
     * ticking via {@link #tickRestore} until restoration is complete.
     */
    public void beginRestore() {
        restoring = true;
        restoreTicks = 0;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            currentYaw = player.getYRot();
            currentPitch = player.getXRot();
        }
    }

    /**
     * @return true if the camera has finished restoring
     */
    public boolean isRestoreComplete() {
        return restoring && restoreTicks >= MAX_RESTORE_TICKS;
    }

    private void tickRestore(LocalPlayer player) {
        restoreTicks++;
        currentYaw = lerpAngle(RESTORE_SPEED, currentYaw, originalYaw);
        currentPitch = Mth.lerp(RESTORE_SPEED, currentPitch, originalPitch);

        player.setYRot(currentYaw);
        player.setXRot(currentPitch);
        player.yRotO = currentYaw;
        player.xRotO = currentPitch;

        if (restoreTicks >= MAX_RESTORE_TICKS) {
            // Snap to exact original to avoid floating point drift
            player.setYRot(originalYaw);
            player.setXRot(originalPitch);
            player.yRotO = originalYaw;
            player.xRotO = originalPitch;
        }
    }

    private float computeTargetYaw(LocalPlayer player) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
    }

    private float computeTargetPitch(LocalPlayer player) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double dy = target.getEyeY() - player.getEyeY();
        double dist = Math.sqrt(dx * dx + dz * dz);
        return (float) (-Math.toDegrees(Math.atan2(dy, dist)));
    }

    private static float lerpAngle(float delta, float from, float to) {
        float diff = Mth.wrapDegrees(to - from);
        return from + delta * diff;
    }
}
