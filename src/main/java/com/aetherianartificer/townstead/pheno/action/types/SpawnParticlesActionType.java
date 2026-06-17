package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

/**
 * Emits a one-shot burst of particles at the actor (Apoli's {@code spawn_particles}).
 * Simple particles only.
 *
 * <p>JSON: {@code { "type":"pheno:spawn_particles", "particle":"minecraft:cloud",
 * "count":20, "spread":0.5, "speed":0.05 }}</p>
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
        int count = Math.max(1, GsonHelper.getAsInt(json, "count", 8));
        float spread = Math.max(0f, GsonHelper.getAsFloat(json, "spread", 0.4f));
        float speed = Math.max(0f, GsonHelper.getAsFloat(json, "speed", 0f));
        return ctx -> {
            if (!(ctx.entity().level() instanceof ServerLevel level)) return;
            if (!(BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof SimpleParticleType options)) return;
            float dx = ctx.entity().getBbWidth() * spread;
            float dy = ctx.entity().getBbHeight() * spread;
            level.sendParticles(options, ctx.entity().getX(), ctx.entity().getY(0.5), ctx.entity().getZ(),
                    count, dx, dy, dx, speed);
        };
    }
}
