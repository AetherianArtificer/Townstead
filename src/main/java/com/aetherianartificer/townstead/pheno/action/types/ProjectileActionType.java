package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.combat.ActionProjectile;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Throws something that runs actions where it lands: damage on a direct hit, then {@code on_hit} on
 * whoever it struck and {@code on_block} on the block it met ({@code on_hit} also covers blocks when
 * {@code on_block} is left out). Those actions run with the impact as their place, so
 * {@code pheno:explode}, {@code pheno:cloud} and {@code pheno:area_of_effect} happen there rather than
 * on the thrower. {@code arc} aims that many degrees above where the thrower looks.
 *
 * <p>JSON: {@code { "type":"pheno:projectile", "item":"minecraft:slime_ball", "speed":1.2, "gravity":0.05,
 * "damage":4, "knockback":0.4, "particle":"minecraft:falling_honey",
 * "on_hit":{ "type":"pheno:cloud", "radius":2, "do":{ "type":"pheno:apply_effect", "effect":"minecraft:slowness" } } }}</p>
 */
public final class ProjectileActionType implements ActionType {

    public static final String KEY = "pheno:projectile";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation itemId = DataPackLang.parseId(GsonHelper.getAsString(json, "item", "minecraft:snowball"));
        float speed = GsonHelper.getAsFloat(json, "speed", 1.5f);
        float inaccuracy = GsonHelper.getAsFloat(json, "inaccuracy", 1f);
        float gravity = GsonHelper.getAsFloat(json, "gravity", 0.03f);
        float damage = GsonHelper.getAsFloat(json, "damage", 0f);
        float knockback = GsonHelper.getAsFloat(json, "knockback", 0f);
        float arc = GsonHelper.getAsFloat(json, "arc", 0f);
        Action onHit = json.has("on_hit") ? Actions.parse(json.get("on_hit")) : null;
        Action onBlock = json.has("on_block") ? Actions.parse(json.get("on_block")) : null;
        ParticleOptions trail = particle(GsonHelper.getAsString(json, "particle", ""));
        return ctx -> {
            LivingEntity shooter = ctx.entity();
            if (!(shooter.level() instanceof ServerLevel level)) return;
            Item item = itemId == null ? Items.SNOWBALL : BuiltInRegistries.ITEM.get(itemId);
            ActionProjectile shot = new ActionProjectile(level, shooter, new ItemStack(item), gravity, damage, knockback,
                    onHit, onBlock, trail);
            Vec3 look = shooter.getLookAngle();
            if (arc != 0f) {
                double up = Math.toRadians(arc);
                double flat = Math.sqrt(look.x * look.x + look.z * look.z);
                double pitch = Math.atan2(look.y, flat) + up;
                double scale = flat < 1e-4 ? 0 : Math.cos(pitch) / flat;
                look = new Vec3(look.x * scale, Math.sin(pitch), look.z * scale);
            }
            shot.shoot(look.x, look.y, look.z, speed, inaccuracy);
            level.addFreshEntity(shot);
        };
    }

    private static ParticleOptions particle(String id) {
        if (id.isEmpty()) return null;
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : com.aetherianartificer.townstead.pheno.particle.DataParticles.options(key);
    }
}
