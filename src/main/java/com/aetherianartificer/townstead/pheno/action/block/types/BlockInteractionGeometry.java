package com.aetherianartificer.townstead.pheno.action.block.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Exact, bounded geometry for a real player-like block interaction. */
public record BlockInteractionGeometry(Direction face, double hitX, double hitY, double hitZ,
                                       boolean inside, @Nullable Vec3 actorOffset,
                                       @Nullable Direction actorFacing) {
    public static final BlockInteractionGeometry DEFAULT =
            new BlockInteractionGeometry(Direction.UP, .5d, .5d, .5d, false, null, null);

    public BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(new Vec3(pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ),
                face, pos, inside);
    }

    public Vec3 actorPosition(BlockPos pos, @Nullable Vec3 fallback) {
        return actorOffset == null
                ? (fallback == null ? new Vec3(pos.getX() + .5d, pos.getY() + .5d, pos.getZ() + .5d) : fallback)
                : new Vec3(pos.getX() + .5d, pos.getY() + .5d, pos.getZ() + .5d).add(actorOffset);
    }

    public static @Nullable BlockInteractionGeometry parse(JsonObject json) {
        if (json == null) return null;
        Direction face = Direction.UP;
        if (json.has("face")) {
            if (!json.get("face").isJsonPrimitive()
                    || !json.getAsJsonPrimitive("face").isString()) return null;
            face = Direction.byName(json.get("face").getAsString());
            if (face == null) return null;
        }
        double[] hit = vector(json.get("hit"), 0.0d, 1.0d);
        if (json.has("hit") && hit == null) return null;
        if (hit == null) hit = new double[]{.5d, .5d, .5d};
        boolean inside = false;
        if (json.has("inside")) {
            if (!json.get("inside").isJsonPrimitive()
                    || !json.getAsJsonPrimitive("inside").isBoolean()) return null;
            inside = json.get("inside").getAsBoolean();
        }
        Vec3 actorOffset = null;
        if (json.has("actor_offset")) {
            double[] parsed = vector(json.get("actor_offset"), -4.0d, 4.0d);
            if (parsed == null) return null;
            actorOffset = new Vec3(parsed[0], parsed[1], parsed[2]);
        }
        Direction actorFacing = null;
        if (json.has("actor_facing")) {
            if (!json.get("actor_facing").isJsonPrimitive()
                    || !json.getAsJsonPrimitive("actor_facing").isString()) return null;
            actorFacing = Direction.byName(json.get("actor_facing").getAsString());
            if (actorFacing == null) return null;
        }
        return new BlockInteractionGeometry(face, hit[0], hit[1], hit[2], inside,
                actorOffset, actorFacing);
    }

    private static @Nullable double[] vector(@Nullable JsonElement element, double min, double max) {
        if (element == null || !element.isJsonArray()) return null;
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) return null;
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            JsonElement value = array.get(i);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
            double number = value.getAsDouble();
            if (!Double.isFinite(number) || number < min || number > max) return null;
            out[i] = number;
        }
        return out;
    }
}
