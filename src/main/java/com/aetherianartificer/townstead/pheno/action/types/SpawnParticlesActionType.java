package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionScheduler;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.particle.DataParticles;
import com.aetherianartificer.townstead.pheno.particle.ParticleShapes;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Emits a one-shot burst of particles at the actor (Apoli's {@code spawn_particles}). The
 * particle is a registered simple particle or a data-pack particle.
 *
 * <p>Without a {@code shape} the burst is a cloud over the actor's body ({@code spread} is a
 * fraction of its size). With one, {@code count} particles are laid out in that shape (see
 * {@link ParticleShapes}) and each flies along it at {@code speed}: {@code ring}, {@code ring_in},
 * {@code orbit}, {@code spokes}, {@code dome}, {@code helix}, {@code burst_up}, {@code cone} or
 * {@code line}, sized by {@code radius}, {@code height} and {@code length}. {@code anchor} places
 * the shape at the actor's {@code feet}, {@code center} (default) or {@code eyes}, and
 * {@code delay} holds the burst back that many ticks so several bursts stage one effect.</p>
 *
 * <p>JSON: {@code { "type":"pheno:spawn_particles", "particle":"minecraft:soul_fire_flame",
 * "shape":"ring", "count":24, "radius":1.5, "speed":0.08, "anchor":"feet" }}</p>
 */
public final class SpawnParticlesActionType implements ActionType {

    public static final String KEY = "pheno:spawn_particles";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "particle", ""));
        if (id == null) return null;
        int count = Math.max(1, Math.min(512, GsonHelper.getAsInt(json, "count", 8)));
        float spread = Math.max(0f, GsonHelper.getAsFloat(json, "spread", 0.4f));
        float speed = Math.max(0f, GsonHelper.getAsFloat(json, "speed", 0f));
        ParticleShapes.Shape shape = json.has("shape") ? ParticleShapes.parse(GsonHelper.getAsString(json, "shape")) : null;
        if (json.has("shape") && shape == null) return null;
        double radius = Math.max(0, GsonHelper.getAsDouble(json, "radius", 1));
        double height = GsonHelper.getAsDouble(json, "height", 2);
        double length = Math.max(0, GsonHelper.getAsDouble(json, "length", 5));
        String anchor = GsonHelper.getAsString(json, "anchor", "center");
        int delay = Math.max(0, GsonHelper.getAsInt(json, "delay", 0));
        Action burst = ctx -> {
            if (!(ctx.entity().level() instanceof ServerLevel level)) return;
            ParticleOptions options = DataParticles.options(id);
            if (options == null) return;
            LivingEntity e = ctx.entity();
            if (shape == null || shape == ParticleShapes.Shape.POINT) {
                float dx = e.getBbWidth() * spread;
                float dy = e.getBbHeight() * spread;
                // Send explicitly with longDistance=true. The generic broadcast overload can cull the
                // activating player at the server tracking boundary; this path is consistent on both
                // supported mappings and guarantees the caster receives its own burst.
                for (ServerPlayer player : level.players()) {
                    level.sendParticles(player, options, true, e.getX(), e.getY(0.5), e.getZ(),
                            count, dx, dy, dx, speed);
                }
                return;
            }
            Vec3 origin = switch (anchor) {
                case "feet" -> e.position().add(0, 0.05, 0);
                case "eyes" -> e.getEyePosition();
                default -> new Vec3(e.getX(), e.getY(0.5), e.getZ());
            };
            var emissions = ParticleShapes.layout(shape, count, origin, e.getLookAngle(), radius, height, length,
                    level.getRandom());
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(origin) > 64 * 64) continue;
                for (ParticleShapes.Emission emission : emissions) {
                    // A count of 0 makes the offset an exact velocity scaled by speed.
                    level.sendParticles(player, options, true,
                            emission.position().x, emission.position().y, emission.position().z, 0,
                            emission.direction().x, emission.direction().y, emission.direction().z, speed);
                }
            }
        };
        return delay == 0 ? burst : ctx -> ActionScheduler.schedule(ctx, delay, burst);
    }
}
