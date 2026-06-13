package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.selector.spatial.RayCast;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a line of particles from the caster's eyes along a ray to its impact point (the beam half of
 * Apoli/Apugli {@code raycast}, reusable on its own for lasers and lightning). Takes the same ray
 * config as {@code pheno:ray} ({@code distance}/{@code direction}/{@code space}/{@code toward}) plus
 * a {@code particle} id and {@code spacing}.
 *
 * <p>JSON: {@code { "type":"pheno:beam", "particle":"minecraft:end_rod", "toward":"target" }}</p>
 */
public final class BeamActionType implements ActionType {

    public static final String KEY = "pheno:beam";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        RayCast ray = RayCast.parse(json);
        ParticleOptions particle = resolveParticle(GsonHelper.getAsString(json, "particle", ""));
        if (particle == null) return null;
        double spacing = Math.max(0.05, GsonHelper.getAsDouble(json, "spacing", 0.5));
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return;
            LivingEntity self = ctx.entity();
            Vec3 origin = self.getEyePosition();
            Vec3 end = ray.impactPoint(SelectorContext.of(ctx));
            if (end == null) return;
            double total = origin.distanceTo(end);
            for (double d = spacing; d < total; d += spacing) {
                double t = d / total;
                level.sendParticles(particle,
                        Mth.lerp(t, origin.x, end.x), Mth.lerp(t, origin.y, end.y), Mth.lerp(t, origin.z, end.z),
                        1, 0, 0, 0, 0);
            }
        };
    }

    /** Resolve a simple particle id; data-carrying particles (block/item/dust) are out of scope. */
    private static ParticleOptions resolveParticle(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return null;
        return BuiltInRegistries.PARTICLE_TYPE.get(key) instanceof ParticleOptions options ? options : null;
    }
}
