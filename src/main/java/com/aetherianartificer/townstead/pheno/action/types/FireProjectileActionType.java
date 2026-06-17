package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Launches a projectile from the actor along its look vector (a racial spit, quill or
 * bolt). {@code entity} is the projectile entity id ({@code minecraft:arrow} default);
 * {@code speed} and {@code inaccuracy} mirror {@code Projectile.shoot}. Server-side.
 *
 * <p>JSON: {@code { "type":"pheno:fire_projectile", "entity":"minecraft:arrow",
 * "speed":1.5, "inaccuracy":1.0 }}</p>
 */
public final class FireProjectileActionType implements ActionType {

    public static final String KEY = "pheno:fire_projectile";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation entityId = json.has("entity")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "entity", ""))
                //? if >=1.21 {
                : ResourceLocation.withDefaultNamespace("arrow");
                //?} else {
                /*: new ResourceLocation("arrow");
                *///?}
        if (entityId == null) return null;
        float speed = GsonHelper.getAsFloat(json, "speed", 1.5f);
        float inaccuracy = GsonHelper.getAsFloat(json, "inaccuracy", 1.0f);
        return ctx -> {
            LivingEntity shooter = ctx.entity();
            if (!(shooter.level() instanceof ServerLevel level)) return;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId);
            if (type == null) return;
            Entity projectile = type.create(level);
            if (projectile == null) return;
            projectile.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
            Vec3 look = shooter.getLookAngle();
            if (projectile instanceof Projectile p) {
                p.setOwner(shooter);
                p.shoot(look.x, look.y, look.z, speed, inaccuracy);
            } else {
                projectile.setDeltaMovement(look.scale(speed));
            }
            level.addFreshEntity(projectile);
        };
    }
}
