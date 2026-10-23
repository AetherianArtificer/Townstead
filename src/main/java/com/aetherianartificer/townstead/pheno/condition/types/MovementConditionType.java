package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.emote.EmoteActivityTracker;
import com.aetherianartificer.townstead.reaction.ReactionLockTracker;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Entity movement, pose, flight, and collision checks. */
public final class MovementConditionType implements ConditionType {

    public static final String KEY = "pheno:movement";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    @Nullable
    public Condition parse(JsonObject json) {
        String movement = GsonHelper.getAsString(json, "movement", "").toLowerCase(Locale.ROOT);
        return switch (movement) {
            case "grounded", "on_ground" -> ctx -> ctx.entity().onGround();
            case "moving" -> ctx -> ctx.entity().getDeltaMovement().horizontalDistanceSqr() > 1.0e-6;
            case "sneaking" -> ctx -> ctx.entity().isShiftKeyDown();
            case "sprinting" -> ctx -> ctx.entity().isSprinting();
            case "crawling" -> ctx -> ctx.entity().isVisuallyCrawling();
            case "swimming" -> ctx -> ctx.entity().isSwimming();
            case "climbing" -> ctx -> ctx.entity().onClimbable();
            case "sleeping" -> ctx -> ctx.entity().isSleeping();
            case "fall_flying" -> ctx -> ctx.entity().isFallFlying();
            case "parachuting" -> ctx -> com.aetherianartificer.townstead.root.locomotion.ParachutePhysics.isOpen(ctx.entity());
            case "ascending" -> ctx -> ctx.entity().getDeltaMovement().y > 0.08;
            case "descending" -> ctx -> !ctx.entity().onGround() && ctx.entity().getDeltaMovement().y < -0.08;
            case "creative_flying" -> ctx -> ctx.entity() instanceof Player player && player.getAbilities().flying;
            case "colliding_horizontally" -> ctx -> ctx.entity().horizontalCollision;
            case "colliding_vertically" -> ctx -> ctx.entity().verticalCollision;
            case "emoting" -> ctx -> EmoteActivityTracker.isEmoting(ctx.entity());
            case "reacting" -> ctx -> ReactionLockTracker.activeReaction(ctx.entity(), ctx.level().getGameTime()) != null;
            default -> null;
        };
    }
}
