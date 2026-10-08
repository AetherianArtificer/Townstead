package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.GsonHelper;

/** Emits a one-shot simple-particle burst at the focused block. */
public final class SpawnParticlesBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:spawn_particles";

    @Override public String key() { return KEY; }

    @Override public BlockAction parse(JsonObject json) {
        var id = DataPackLang.parseId(GsonHelper.getAsString(json, "particle", ""));
        int count = Math.max(1, GsonHelper.getAsInt(json, "count", 8));
        double spread = Math.max(0, GsonHelper.getAsDouble(json, "spread", 0.4));
        double speed = Math.max(0, GsonHelper.getAsDouble(json, "speed", 0));
        double offsetX = GsonHelper.getAsDouble(json, "offset_x", 0);
        double offsetY = GsonHelper.getAsDouble(json, "offset_y", 0);
        double offsetZ = GsonHelper.getAsDouble(json, "offset_z", 0);
        if (id == null) return null;
        return context -> {
            if (!(BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof ParticleOptions particle)) return;
            context.level().sendParticles(particle,
                    context.pos().getX() + 0.5 + offsetX,
                    context.pos().getY() + 0.5 + offsetY,
                    context.pos().getZ() + 0.5 + offsetZ,
                    count, spread, spread, spread, speed);
        };
    }
}
