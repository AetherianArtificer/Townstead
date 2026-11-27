package com.aetherianartificer.townstead.root.attachment;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The server's physics chains, running only for bodies a moving projectile has come near. A body that's
 * awake swings the way a client's would, so what's aimed at is close to where the tail really is. Everyone
 * else sits where their chains settle, which costs nothing. Each client still runs its own copy, and when
 * they differ, the server's is the swing that counts.
 */
public final class BodyPoseSim {

    private static final double WAKE_RANGE = 32.0;
    private static final int WAKE_CHECK_TICKS = 5;
    private static final long AWAKE_TICKS = 40;
    private static final double MOVING_SQR = 0.01;
    private static final float TICK_SECONDS = 0.05f;
    private static final float PATH_SMOOTHING = 0.15f;
    private static final float TELEPORT = 8f;

    private static final Map<LivingEntity, Awake> AWAKE = Collections.synchronizedMap(new WeakHashMap<>());

    private BodyPoseSim() {}

    /** One body's running chains, by attachment, anchor and chain. */
    static final class Awake {
        long until;
        double x, y, z;
        boolean placed;
        float pathX, pathY, pathZ;
        final Map<String, ChainSim[][]> chains = new HashMap<>();

        ChainSim[] of(AttachmentDef def, int anchor, int anchors) {
            ChainSim[][] slots = chains.computeIfAbsent(def.id(), k -> new ChainSim[anchors][]);
            if (anchor >= slots.length) return null;
            if (slots[anchor] == null) {
                slots[anchor] = new ChainSim[def.physics().size()];
                for (int c = 0; c < slots[anchor].length; c++) slots[anchor][c] = new ChainSim();
            }
            return slots[anchor];
        }
    }

    /** Called as a projectile ticks: anyone it could reach soon wakes up, so their chains are moving when it arrives. */
    public static void onProjectileTick(Projectile projectile) {
        // From its first tick: a shot from close by lands before any later check.
        if (projectile.level().isClientSide || projectile.tickCount % WAKE_CHECK_TICKS != 1) return;
        if (projectile.getDeltaMovement().lengthSqr() < MOVING_SQR) return;
        long until = projectile.level().getGameTime() + AWAKE_TICKS;
        AABB around = projectile.getBoundingBox().inflate(WAKE_RANGE);
        for (LivingEntity entity : projectile.level().getEntitiesOfClass(LivingEntity.class, around,
                e -> e instanceof Player || e instanceof VillagerEntityMCA)) {
            AWAKE.computeIfAbsent(entity, k -> new Awake()).until = until;
        }
    }

    /** Runs each server tick for a person. Does nothing unless they're awake. */
    public static void tick(LivingEntity entity) {
        Awake awake = AWAKE.get(entity);
        if (awake == null) return;
        if (entity.level().getGameTime() > awake.until) {
            AWAKE.remove(entity);
            return;
        }
        float dx = awake.placed ? (float) (entity.getX() - awake.x) : 0f;
        float dy = awake.placed ? (float) (entity.getY() - awake.y) : 0f;
        float dz = awake.placed ? (float) (entity.getZ() - awake.z) : 0f;
        awake.x = entity.getX();
        awake.y = entity.getY();
        awake.z = entity.getZ();
        if (!awake.placed || Math.abs(dx) > TELEPORT || Math.abs(dy) > TELEPORT || Math.abs(dz) > TELEPORT) {
            awake.placed = true;
            awake.pathX = awake.pathY = awake.pathZ = 0f;
            awake.chains.clear();
            dx = dy = dz = 0f;
        }
        // Chains feel a smoothed path, as they do on a client: vanilla changes speed in a single tick.
        float ease = 1f - (float) Math.exp(-TICK_SECONDS / PATH_SMOOTHING);
        awake.pathX += (dx / TICK_SECONDS - awake.pathX) * ease;
        awake.pathY += (dy / TICK_SECONDS - awake.pathY) * ease;
        awake.pathZ += (dz / TICK_SECONDS - awake.pathZ) * ease;
        List<AttachmentDef> worn = WornAttachments.of(entity);
        for (AttachmentDef def : worn) {
            if (!def.physics().isEmpty()) BodyPose.step(entity, def, awake, TICK_SECONDS);
        }
    }

    /** The running chains for this attachment at this anchor, or null when this body is at rest. */
    @Nullable
    static ChainSim[] running(LivingEntity entity, AttachmentDef def, int anchor) {
        if (entity.level().isClientSide) return null;
        Awake awake = AWAKE.get(entity);
        ChainSim[][] slots = awake == null ? null : awake.chains.get(def.id());
        return slots == null || anchor >= slots.length ? null : slots[anchor];
    }
}
