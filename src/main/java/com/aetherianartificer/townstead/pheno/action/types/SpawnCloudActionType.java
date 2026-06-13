package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditions;
import com.aetherianartificer.townstead.pheno.field.Cloud;
import com.aetherianartificer.townstead.pheno.field.CloudManager;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Spawns a lingering field (Apoli {@code spawn_effect_cloud}, Apugli
 * {@code spawn_custom_effect_cloud}): a volume that runs {@code do} on each entity inside it every
 * reapplication cycle, for {@code duration}, growing/shrinking via {@code grow_per_tick}/
 * {@code shrink_on_use}. One {@code do} subsumes Apoli's status effects ({@code apply_effect}) and
 * Apugli's bi-entity actions. The cloud sits at the acting entity, so {@code on: "target"} spawns it
 * on the target while the bearer stays its owner. {@code where} is a bi-entity gate (owner, inside).
 *
 * <p>JSON: {@code { "type":"pheno:cloud", "radius":3, "duration":600, "particle":"minecraft:poison",
 * "do":{ "type":"pheno:apply_effect", "effect":"minecraft:poison", "duration":100 } }}</p>
 */
public final class SpawnCloudActionType implements ActionType {

    public static final String KEY = "pheno:cloud";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Action doAction = json.has("do") ? Actions.parse(json.get("do")) : null;
        if (doAction == null) return null;
        BiEntityCondition where = json.has("where") ? BiEntityConditions.parse(json.get("where")) : null;
        float radius = GsonHelper.getAsFloat(json, "radius", 3.0f);
        float growPerTick = GsonHelper.getAsFloat(json, "grow_per_tick", 0.0f);
        float shrinkOnUse = GsonHelper.getAsFloat(json, "shrink_on_use", 0.0f);
        int waitTime = GsonHelper.getAsInt(json, "wait_time", 10);
        int duration = GsonHelper.getAsInt(json, "duration", 600);
        int reapplyDelay = GsonHelper.getAsInt(json, "reapply_delay", 20);
        double height = GsonHelper.getAsDouble(json, "height", 0.0);
        ParticleOptions particle = resolveParticle(GsonHelper.getAsString(json, "particle", ""));
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return;
            CloudManager.spawn(new Cloud(level, ctx.entity().position(), ctx.origin(), doAction, where,
                    particle, radius, growPerTick, shrinkOnUse, waitTime, duration, reapplyDelay, height));
        };
    }

    @Nullable
    private static ParticleOptions resolveParticle(String id) {
        if (id.isEmpty()) return null;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return null;
        return BuiltInRegistries.PARTICLE_TYPE.get(key) instanceof ParticleOptions options ? options : null;
    }
}
