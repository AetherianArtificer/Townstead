package com.aetherianartificer.townstead.client.root;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Local-player fish swimming: forward moves the way you look (up and down included),
 * strafing slides sideways, jump and sneak rise and sink, sprint goes faster. Let go and
 * you coast to a stop where you are instead of sinking.
 */
public final class ClientSwim {

    private static final double SPEED = 0.3;
    private static final double SPRINT = 1.4;
    private static final double RESPONSE = 0.25;
    private static final double COAST = 0.85;

    private ClientSwim() {}

    public static void afterTravel(Player entity) {
        if (entity != Minecraft.getInstance().player) return;
        LocalPlayer player = (LocalPlayer) entity;
        float forward = player.input.forwardImpulse;
        float left = player.input.leftImpulse;
        double vertical = (player.input.jumping ? 1 : 0) - (player.input.shiftKeyDown ? 1 : 0);
        Vec3 look = player.getLookAngle();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 wish = look.scale(forward).add(side.scale(left)).add(0, vertical, 0);
        Vec3 v = player.getDeltaMovement();
        if (wish.lengthSqr() < 1.0E-4) {
            v = v.scale(COAST);
        } else {
            if (wish.lengthSqr() > 1.0) wish = wish.normalize();
            Vec3 desired = wish.scale(player.isSprinting() ? SPEED * SPRINT : SPEED);
            v = v.add(desired.subtract(v).scale(RESPONSE));
        }
        player.setDeltaMovement(v);
        player.resetFallDistance();
    }
}
