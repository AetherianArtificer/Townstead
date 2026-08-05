package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.TeleportSupport;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Teleports the block action's cause to its selected block position. This makes teleportation a
 * normal consumer of {@code pheno:at}, block selectors, {@code pheno:ray}, and block-action
 * transforms such as {@code pheno:offset}.
 */
public final class TeleportBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:teleport";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        boolean safe = GsonHelper.getAsBoolean(json, "safe", false);
        boolean resetVelocity = GsonHelper.getAsBoolean(json, "reset_velocity", false);
        return ctx -> {
            if (ctx.cause() == null) {
                ctx.fail();
                return;
            }
            Vec3 target = new Vec3(ctx.pos().getX() + 0.5, ctx.pos().getY(), ctx.pos().getZ() + 0.5);
            boolean moved = TeleportSupport.teleport(ctx.cause(), target, safe);
            if (!moved) {
                ctx.fail();
                return;
            }
            if (resetVelocity) ctx.cause().setDeltaMovement(Vec3.ZERO);
        };
    }
}
