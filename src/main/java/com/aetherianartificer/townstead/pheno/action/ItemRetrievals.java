package com.aetherianartificer.townstead.pheno.action;

import com.aetherianartificer.townstead.mixin.accessor.ItemEntityOwnerAccessor;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-owned retrieval: never copies stacks, clears ownership, or bypasses native pickup. */
public final class ItemRetrievals {
    private ItemRetrievals() {}
    private record Pull(ServerLevel level, ItemEntity item, long start, double range,
                        double speed, int extendTicks, DustParticleOptions particle) {}
    private static final Map<LivingEntity, Pull> ACTIVE = new WeakHashMap<>();
    public static void clear() { ACTIVE.clear(); }

    public static boolean start(LivingEntity actor, double range, double speed, double aim,
                                int extendTicks, int color) {
        if (!(actor.level() instanceof ServerLevel level) || !actor.isAlive()
                || actor.isSpectator() || actor.isSleeping() || ACTIVE.containsKey(actor)) return false;
        ItemEntity item = target(actor, range, aim);
        if (item == null || ACTIVE.values().stream().anyMatch(p -> p.item == item)) return false;
        DustParticleOptions particle = new DustParticleOptions(new Vector3f(
                ((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f), .65f);
        ACTIVE.put(actor, new Pull(level, item, level.getGameTime(), range, speed, extendTicks, particle));
        return true;
    }

    public static boolean eligible(LivingEntity actor, ItemEntity item) {
        if (!item.isAlive() || item.getItem().isEmpty() || item.hasPickUpDelay()
                || item.level() != actor.level()) return false;
        var owner = ((ItemEntityOwnerAccessor)item).townstead$pickupOwner();
        if (owner != null && !owner.equals(actor.getUUID())) return false;
        if (actor instanceof Player) return true;
        return actor instanceof Mob mob && mob.canPickUpLoot() && mob.wantsToPickUp(item.getItem())
                && actor.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    public static ItemEntity target(LivingEntity actor, double range, double aim) {
        if (!(actor instanceof Player)) {
            // Only retrieve the item the villager's own brain already wants. This respects
            // profession/food collection and avoids stealing arbitrary drops while idle.
            var brain = actor.getBrain();
            if (!brain.hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)) return null;
            ItemEntity item = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).orElse(null);
            return item != null && eligible(actor, item) && visible(actor, item, range) ? item : null;
        }
        Vec3 start = actor.getEyePosition();
        Vec3 end = start.add(actor.getLookAngle().scale(range));
        ItemEntity chosen = null;
        double nearest = range * range;
        for (ItemEntity item : actor.level().getEntitiesOfClass(ItemEntity.class,
                actor.getBoundingBox().expandTowards(actor.getLookAngle().scale(range)).inflate(aim + .5))) {
            if (!eligible(actor, item) || !visible(actor, item, range)) continue;
            var hit = item.getBoundingBox().inflate(aim).clip(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < nearest) { nearest = distance; chosen = item; }
        }
        return chosen;
    }

    private static Vec3 mouth(LivingEntity actor) {
        return actor.getEyePosition().add(0, -Math.min(.12, actor.getEyeHeight() * .2), 0);
    }
    private static boolean clear(LivingEntity actor, Vec3 from, Vec3 to) {
        return actor.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, actor)).getType() == HitResult.Type.MISS;
    }
    private static boolean visible(LivingEntity actor, ItemEntity item, double range) {
        Vec3 end = item.getBoundingBox().getCenter();
        return actor.getEyePosition().distanceToSqr(end) <= range * range
                && clear(actor, actor.getEyePosition(), end) && clear(actor, mouth(actor), end);
    }

    public static void tick(LivingEntity actor) {
        Pull p = ACTIVE.get(actor);
        if (p == null) return;
        long age = p.level.getGameTime() - p.start;
        if (!actor.isAlive() || actor.isSpectator() || actor.isSleeping() || actor.level() != p.level
                || age > 80 || !eligible(actor, p.item) || !visible(actor, p.item, p.range)) {
            ACTIVE.remove(actor);
            return;
        }
        Vec3 origin = mouth(actor);
        Vec3 tip = p.item.getBoundingBox().getCenter();
        if (age < p.extendTicks) {
            tip = origin.lerp(tip, (age + 1d) / p.extendTicks);
        } else {
            // Deliver into the ordinary pickup volume, using collision-aware movement.
            Vec3 delta = actor.position().add(0, .2, 0).subtract(p.item.position());
            Vec3 step = delta.length() <= p.speed ? delta : delta.normalize().scale(p.speed);
            if (!clear(actor, tip, tip.add(step))) { ACTIVE.remove(actor); return; }
            p.item.setDeltaMovement(Vec3.ZERO);
            p.item.move(MoverType.SELF, step);
            p.item.hasImpulse = true;
            tip = p.item.getBoundingBox().getCenter();
            if (delta.length() <= p.speed) {
                ACTIVE.remove(actor);
                if (actor instanceof Player player) p.item.playerTouch(player);
                // Mobs use their normal loot tick when the item reaches their feet.
            }
        }
        // A pink particle ribbon grows to the item and retracts with it. No new texture,
        // renderer dependency or client-authored hit result is required.
        int steps = Math.max(1, Math.min(80, (int)Math.ceil(origin.distanceTo(tip) / .12)));
        for (int i = 0; i <= steps; i++) {
            Vec3 at = origin.lerp(tip, i / (double)steps);
            p.level.sendParticles(p.particle, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }
}
