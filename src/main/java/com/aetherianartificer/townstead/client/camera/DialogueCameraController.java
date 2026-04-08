package com.aetherianartificer.townstead.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Smoothly rotates the player's camera to face a target entity during dialogue,
 * and restores the original orientation on close.
 */
public class DialogueCameraController {
    private final float originalYaw;
    private final float originalPitch;
    private final Entity target;
    private float currentYaw;
    private float currentPitch;
    private static final float LERP_SPEED = 0.1f;

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
        if (player == null || target == null || target.isRemoved()) return;

        float targetYaw = computeTargetYaw(player);
        float targetPitch = computeTargetPitch(player);

        // Lerp toward target, handling angle wrapping
        currentYaw = lerpAngle(LERP_SPEED, currentYaw, targetYaw);
        currentPitch = Mth.lerp(LERP_SPEED, currentPitch, targetPitch);

        player.setYRot(currentYaw);
        player.setXRot(currentPitch);
        player.yRotO = currentYaw;
        player.xRotO = currentPitch;
    }

    public void restore() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.setYRot(originalYaw);
        player.setXRot(originalPitch);
        player.yRotO = originalYaw;
        player.xRotO = originalPitch;
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

    /**
     * Lerp between two angles, handling the 360-degree wraparound correctly.
     */
    private static float lerpAngle(float delta, float from, float to) {
        float diff = Mth.wrapDegrees(to - from);
        return from + delta * diff;
    }
}
