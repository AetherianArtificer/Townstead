package com.aetherianartificer.townstead.client.species;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Eases the spider-gravity reorientation in and out so attaching to / leaving a wall is a smooth swing
 * rather than a snap. Holds a per-entity factor (0 = upright, 1 = fully on the surface) plus the last wall
 * normal, so the ease-out can keep rotating back toward upright after the entity has already let go (when
 * {@link ClimbRender#wallNormal} would return null). Ticked once per client tick from {@code onClientTick}
 * (the single writer); the model ({@link ClimbRender}) and camera ({@link ClimbView}) only read it.
 */
public final class ClimbAnim {

    private ClimbAnim() {}

    // Per-tick ramp step: ~7 ticks (~0.35 s) from upright to fully on the wall.
    private static final float STEP = 0.15f;

    // Per-tick low-pass on the surface normal, so crossing block edges on blocky terrain swings the surface
    // rather than snapping it between faces.
    private static final float NORMAL_EASE = 0.25f;

    private static final Map<Integer, State> STATES = new HashMap<>();

    private static final class State {
        float factor;
        Vector3f normal;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) continue;
            Vector3f n = ClimbRender.wallNormal(le);
            State s = STATES.get(e.getId());
            if (n != null) {
                if (s == null) {
                    s = new State();
                    STATES.put(e.getId(), s);
                }
                if (s.normal == null) {
                    s.normal = n;
                } else {
                    s.normal.lerp(n, NORMAL_EASE);
                    if (s.normal.lengthSquared() < 1.0e-4f) s.normal.set(n);
                    else s.normal.normalize();
                }
                s.factor = Math.min(1f, s.factor + STEP);
            } else if (s != null) {
                s.factor = Math.max(0f, s.factor - STEP);
                if (s.factor <= 0f) STATES.remove(e.getId());
            }
        }
        STATES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        // Track the local player's attach/detach here (runs every tick, regardless of camera perspective)
        // so the wall-frame look resets correctly even if the player attaches while in third person.
        if (mc.player != null) ClimbLook.updateClungState(factor(mc.player.getId()));
        ClimbMove.tickGrace();
    }

    /** Eased reorientation amount for an entity, 0 (upright) to 1 (fully on the surface). */
    public static float factor(int entityId) {
        State s = STATES.get(entityId);
        return s == null ? 0f : s.factor;
    }

    /** The last wall normal seen while climbing, retained through the ease-out, or null. */
    public static Vector3f normal(int entityId) {
        State s = STATES.get(entityId);
        return s == null ? null : s.normal;
    }
}
