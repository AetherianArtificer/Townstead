package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Adds velocity to the actor (a dash or leap). With {@code "relative":true} the
 * vector is rotated to the actor's facing, so {@code z} is "forward".
 *
 * <p>JSON: {@code { "type":"pheno:add_velocity", "x":0, "y":0.4, "z":1.2,
 * "relative":true }}</p>
 */
public final class VelocityActionType implements ActionType {

    public static final String KEY = "pheno:add_velocity";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        double x = GsonHelper.getAsDouble(json, "x", 0d);
        double y = GsonHelper.getAsDouble(json, "y", 0d);
        double z = GsonHelper.getAsDouble(json, "z", 0d);
        boolean relative = GsonHelper.getAsBoolean(json, "relative", false);
        return ctx -> {
            var entity = ctx.entity();
            Vec3 push = new Vec3(x, y, z);
            if (relative) push = push.yRot((float) -Math.toRadians(entity.getYRot()));
            entity.setDeltaMovement(entity.getDeltaMovement().add(push));
            entity.hasImpulse = true;
            if (entity instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(entity));
            }
        };
    }
}
