package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.combat.Minions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Spawns entities at the actor's position (Apoli's entity {@code spawn_entity}).
 *
 * <p>{@code count} spawns several, scattered within {@code radius} blocks onto open ground.
 * {@code nbt} (an SNBT string or a JSON object) is merged into each before it enters the world.
 * {@code on_spawn} runs on each new creature, with the actor as the counterpart.</p>
 *
 * <p>{@code owner: true} makes them the actor's summons (see {@code Minions}): {@code allied}
 * (default true) has them defend the actor, attack what the actor attacks and hunt monsters while
 * never turning on the actor or their allies; {@code follow} (default true) keeps them at the
 * actor's side; {@code duration} ticks later they vanish (0 keeps them); and
 * {@code burns_in_sun} (default false) lets undead summons burn as usual. Ownership is saved with
 * the summon.</p>
 *
 * <p>JSON: {@code { "type":"pheno:spawn_entity", "entity":"minecraft:zombie", "count":2,
 * "radius":2.5, "owner":true, "duration":1200,
 * "nbt":{ "Attributes":[{ "Name":"generic.max_health", "Base":30 }] } }}</p>
 */
public final class SpawnEntityActionType implements ActionType {

    public static final String KEY = "pheno:spawn_entity";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "entity", ""));
        if (id == null) return null;
        int count = Math.max(1, Math.min(16, GsonHelper.getAsInt(json, "count", 1)));
        double radius = Math.max(0, Math.min(16, GsonHelper.getAsDouble(json, "radius", 0)));
        CompoundTag nbt = json.has("nbt") ? parseNbt(json.get("nbt")) : null;
        if (json.has("nbt") && nbt == null) return null;
        Action onSpawn = json.has("on_spawn") ? Actions.parse(json.get("on_spawn")) : null;
        if (json.has("on_spawn") && onSpawn == null) return null;
        Minions.Spec minion = GsonHelper.getAsBoolean(json, "owner", false)
                ? new Minions.Spec(GsonHelper.getAsBoolean(json, "allied", true),
                        GsonHelper.getAsBoolean(json, "follow", true),
                        Math.max(0, GsonHelper.getAsInt(json, "duration", 0)),
                        GsonHelper.getAsBoolean(json, "burns_in_sun", false))
                : null;
        return ctx -> {
            if (!(ctx.entity().level() instanceof ServerLevel level)) return;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (type == null) return;
            for (int i = 0; i < count; i++) spawnOne(ctx, level, type, radius, nbt, minion, onSpawn);
        };
    }

    private static void spawnOne(ActionContext ctx, ServerLevel level, EntityType<?> type, double radius,
                                 @Nullable CompoundTag nbt, @Nullable Minions.Spec minion, @Nullable Action onSpawn) {
        Entity spawned = type.create(level);
        if (spawned == null) return;
        if (nbt != null) {
            CompoundTag merged = spawned.saveWithoutId(new CompoundTag());
            merged.merge(nbt);
            spawned.load(merged);
        }
        Vec3 at = placement(ctx.entity(), spawned, radius, level.getRandom());
        spawned.moveTo(at.x, at.y, at.z, ctx.entity().getYRot(), ctx.entity().getXRot());
        if (minion != null) Minions.bind(spawned, ctx.origin(), minion);
        level.addFreshEntity(spawned);
        if (onSpawn != null && spawned instanceof LivingEntity living) {
            onSpawn.run(new ActionContext(living, ctx.entity(), ctx.origin()));
        }
    }

    /** A clear spot within {@code radius} of the actor, falling back to the actor's own position. */
    private static Vec3 placement(LivingEntity actor, Entity spawned, double radius, RandomSource random) {
        if (radius <= 0) return actor.position();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = radius * (0.5 + random.nextDouble() * 0.5);
            Vec3 candidate = actor.position().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            for (int dy = 1; dy >= -2; dy--) {
                Vec3 spot = candidate.add(0, dy, 0);
                var box = spawned.getType().getDimensions().makeBoundingBox(spot);
                if (actor.level().noCollision(spawned, box)
                        && !actor.level().noCollision(spawned, box.move(0, -0.5, 0))) {
                    return spot;
                }
            }
        }
        return actor.position();
    }

    private static @Nullable CompoundTag parseNbt(JsonElement value) {
        try {
            if (value.isJsonPrimitive()) return TagParser.parseTag(value.getAsString());
            Tag converted = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, value);
            return converted instanceof CompoundTag compound ? compound : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
