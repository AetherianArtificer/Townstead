package com.aetherianartificer.townstead.root.locomotion;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.ability.Ability;
import com.aetherianartificer.townstead.root.ability.MovementAbilities;
import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The {@code parachute} fall style: once a drop passes {@link #OPEN_DROP} blocks the bearer
 * descends at {@link #DESCENT} blocks a tick until it lands, with no fall damage. Each side
 * tracks every bearer it ticks, so the client knows who is parachuting (for clips and outfits)
 * from positions alone; only the side that moves the body slows it: the server for villagers,
 * the owning client for its player.
 */
public final class ParachutePhysics {

    public static final double OPEN_DROP = 2.0;
    public static final double DESCENT = 0.1;

    private static final class Drop {
        double peak;
        boolean open;

        Drop(double y) { peak = y; }
    }

    // One map per side: each is only touched from its own thread.
    private static final Map<LivingEntity, Drop> SERVER = new WeakHashMap<>();
    private static final Map<LivingEntity, Drop> CLIENT = new WeakHashMap<>();

    private ParachutePhysics() {}

    /** After the physics tick, for every living entity on both sides. */
    public static void afterTravel(LivingEntity entity) {
        boolean client = entity.level().isClientSide;
        Map<LivingEntity, Drop> drops = client ? CLIENT : SERVER;
        if (landed(entity)) {
            if (!drops.isEmpty()) drops.remove(entity);
            return;
        }
        Drop drop = drops.get(entity);
        if (drop == null) {
            if (!isParachutist(entity)) return;
            drop = new Drop(entity.getY());
            drops.put(entity, drop);
        }
        if (!drop.open) {
            drop.peak = Math.max(drop.peak, entity.getY());
            if (drop.peak - entity.getY() < OPEN_DROP) return;
            if (!isParachutist(entity)) {
                drops.remove(entity);
                return;
            }
            drop.open = true;
        }
        if (!movesBody(entity)) return;
        Vec3 m = entity.getDeltaMovement();
        if (m.y < -DESCENT) entity.setDeltaMovement(m.x, -DESCENT, m.z);
        entity.resetFallDistance();
    }

    /** Whether the entity is coming down under its parachute, on this side. */
    public static boolean isOpen(LivingEntity entity) {
        Drop drop = (entity.level().isClientSide ? CLIENT : SERVER).get(entity);
        return drop != null && drop.open;
    }

    /** A {@code slow_fall} gene in the parachute style. Side-aware, like the other movement styles. */
    public static boolean isParachutist(LivingEntity entity) {
        if (!ExpressedGenes.canCarry(entity)) return false;
        if (!MovementAbilities.isActive(entity, Ability.SLOW_FALL)) return false;
        if (entity.level().isClientSide) {
            return com.aetherianartificer.townstead.client.root.ClientAbilities.fallStyle(entity) == FallStyle.PARACHUTE;
        }
        for (Power power : Powers.active(entity)) {
            if (power.component() instanceof AbilityGeneType.Instance ability
                    && ability.ability() == Ability.SLOW_FALL && ability.fallStyle() == FallStyle.PARACHUTE) {
                return true;
            }
        }
        return false;
    }

    private static boolean landed(LivingEntity entity) {
        if (entity.onGround() || entity.isInWater() || entity.isInLava() || entity.isFallFlying()
                || entity.onClimbable() || entity.isPassenger() || entity.isNoGravity()) {
            return true;
        }
        return entity instanceof Player player && (player.getAbilities().flying || player.isSpectator());
    }

    private static boolean movesBody(LivingEntity entity) {
        if (!entity.level().isClientSide) return !(entity instanceof Player);
        return com.aetherianartificer.townstead.client.root.ClientAbilities.isLocalPlayer(entity);
    }
}
