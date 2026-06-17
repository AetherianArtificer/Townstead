package com.aetherianartificer.townstead.client.origin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Local-player flight controls for {@code phasing}. With {@code noPhysics} on there is
 * no floor to stand on, so each physics tick we replace gravity with hover and map
 * jump/sneak to vertical motion, capping horizontal speed (no collision friction).
 * Only the owning client drives its own player; other players move from server
 * position updates.
 */
public final class ClientPhasing {

    private static final double LIFT = 0.22;
    private static final double HORIZONTAL_CAP = 0.5;

    private ClientPhasing() {}

    public static void fly(LivingEntity entity) {
        if (entity != Minecraft.getInstance().player) return;
        LocalPlayer player = (LocalPlayer) entity;
        double lift = player.isSprinting() ? LIFT * 1.8 : LIFT;
        double vy = 0;
        if (player.input.jumping) vy += lift;
        if (player.input.shiftKeyDown) vy -= lift;
        Vec3 m = player.getDeltaMovement();
        double x = Math.max(-HORIZONTAL_CAP, Math.min(HORIZONTAL_CAP, m.x));
        double z = Math.max(-HORIZONTAL_CAP, Math.min(HORIZONTAL_CAP, m.z));
        player.setDeltaMovement(x, vy, z);
        player.resetFallDistance();
    }
}
