package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.locomotion.DriftPhysics;
import com.aetherianartificer.townstead.root.locomotion.FlightStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Local-player drift: after each physics tick, float the player over whatever is below.
 * Jump lifts the float higher (up to a few blocks), sneak lets it settle low. Water is
 * floated over, unless the player sneaks: then it sinks in, and once in the water the
 * player swims as anyone would. The player counts as standing, so walking keeps its
 * ground speed and friction, falls do no harm, and the server does not mistake the float
 * for flying. Only the owning client drives its own player; others see it through
 * position updates.
 */
public final class ClientDrift {

    private static final double LIFT_STEP = 0.08;
    private static final double MAX_LIFT = 2.5;
    private static final double SNEAK_HEIGHT = 0.2;

    private static double lift;

    private ClientDrift() {}

    public static void afterTravel(LivingEntity entity) {
        if (entity != Minecraft.getInstance().player) return;
        LocalPlayer player = (LocalPlayer) entity;
        if (player.getAbilities().flying || player.isFallFlying() || player.isPassenger() || player.isSleeping()
                || player.isSpectator() || player.noPhysics || player.isInWater() || !DriftPhysics.isDrifter(player)) {
            lift = 0;
            return;
        }
        if (player.input.jumping) lift = Math.min(MAX_LIFT, lift + LIFT_STEP);
        else lift = Math.max(0, lift - LIFT_STEP);
        boolean sneaking = player.input.shiftKeyDown;
        double floatHeight = sneaking ? SNEAK_HEIGHT : FlightStyle.DRIFT.cruiseHeight() + lift;
        Vec3 m = player.getDeltaMovement();
        double vy = DriftPhysics.vertical(player, m, floatHeight, player.tickCount, !sneaking, Double.NaN);
        player.setDeltaMovement(m.x, vy, m.z);
        player.setOnGround(true);
        player.resetFallDistance();
    }
}
