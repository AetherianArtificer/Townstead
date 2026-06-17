package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;

/**
 * Teleports the actor to a random safe spot within {@code distance} blocks (Apoli's
 * {@code random_teleport}); chorus-fruit-style.
 *
 * <p>JSON: {@code { "type":"pheno:random_teleport", "distance":8 }}</p>
 */
public final class RandomTeleportActionType implements ActionType {

    public static final String KEY = "pheno:random_teleport";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        double distance = Math.max(1, GsonHelper.getAsDouble(json, "distance", 8));
        return ctx -> {
            RandomSource random = ctx.entity().getRandom();
            double x = ctx.entity().getX() + (random.nextDouble() - 0.5) * 2 * distance;
            double y = ctx.entity().getY() + (random.nextDouble() - 0.5) * 2 * distance;
            double z = ctx.entity().getZ() + (random.nextDouble() - 0.5) * 2 * distance;
            ctx.entity().randomTeleport(x, y, z, true);
        };
    }
}
