package com.aetherianartificer.townstead.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Smoothly rotates the player's camera to face a target entity during dialogue.
 * Uses frame-rate independent interpolation for buttery smooth movement.
 */
public class DialogueCameraController {
    private final float originalYaw;
    private final float originalPitch;
    private final Entity target;
    private float currentYaw;
    private float currentPitch;
    private boolean restoring;
    private int restoreTicks;
    private long lastTickTime;
    private static final float APPROACH_SPEED = 3.0f; // per second
    private static final float RESTORE_SPEED = 4.0f;
    private static final int MAX_RESTORE_TICKS = 15;

    public DialogueCameraController(Entity target) {
        LocalPlayer player = Minecraft.getInstance().player;
        this.target = target;
        this.originalYaw = player != null ? player.getYRot() : 0;
        this.originalPitch = player != null ? player.getXRot() : 0;
        this.currentYaw = originalYaw;
        this.currentPitch = originalPitch;
        this.lastTickTime = System.nanoTime();
    }

    /**
     * Call every frame (from render), not every tick.
     * Uses real elapsed time for frame-rate independent smoothing.
     */
    public void update() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        long now = System.nanoTime();
        float dt = (now - lastTickTime) / 1_000_000_000f; // seconds
        dt = Math.min(dt, 0.1f); // cap to avoid jumps on lag spikes
        lastTickTime = now;

        if (restoring) {
            updateRestore(player, dt);
            return;
        }

        if (target == null || target.isRemoved()) return;

        float targetYaw = computeTargetYaw(player);
        float targetPitch = computeTargetPitch(player);

        float lerpFactor = 1.0f - (float) Math.exp(-APPROACH_SPEED * dt);
        currentYaw = lerpAngle(lerpFactor, currentYaw, targetYaw);
        currentPitch = Mth.lerp(lerpFactor, currentPitch, targetPitch);

        applyToPlayer(player);
    }

    /**
     * Still called from tick() for restore completion detection.
     */
    public void tick() {
        if (restoring) restoreTicks++;
    }

    public void beginRestore() {
        restoring = true;
        restoreTicks = 0;
        lastTickTime = System.nanoTime();
    }

    public boolean isRestoreComplete() {
        return restoring && restoreTicks >= MAX_RESTORE_TICKS;
    }

    private void updateRestore(LocalPlayer player, float dt) {
        float lerpFactor = 1.0f - (float) Math.exp(-RESTORE_SPEED * dt);
        currentYaw = lerpAngle(lerpFactor, currentYaw, originalYaw);
        currentPitch = Mth.lerp(lerpFactor, currentPitch, originalPitch);

        if (restoreTicks >= MAX_RESTORE_TICKS) {
            currentYaw = originalYaw;
            currentPitch = originalPitch;
        }

        applyToPlayer(player);
    }

    private void applyToPlayer(LocalPlayer player) {
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
        player.setYRot(currentYaw);
        player.setXRot(currentPitch);
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
