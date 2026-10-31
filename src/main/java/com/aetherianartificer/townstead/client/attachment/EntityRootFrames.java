package com.aetherianartificer.townstead.client.attachment;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The pose matrix each in-progress living entity render started from, innermost
 * last. Inverting it turns any later matrix in that render into the entity's own
 * frame: origin at its feet, world axes (on the world pass) or the preview's axes
 * (in a GUI). Render thread only; matrices are pooled.
 */
public final class EntityRootFrames {

    private static final int MAX_DEPTH = 8;
    private static final LivingEntity[] ENTITIES = new LivingEntity[MAX_DEPTH];
    private static final Matrix4f[] ROOTS = new Matrix4f[MAX_DEPTH];
    private static int depth;

    static {
        for (int i = 0; i < MAX_DEPTH; i++) ROOTS[i] = new Matrix4f();
    }

    private EntityRootFrames() {}

    public static void push(LivingEntity entity, Matrix4f root) {
        // A render that threw never popped; don't let the stack drift forever.
        if (depth > MAX_DEPTH * 4) depth = 0;
        if (depth < MAX_DEPTH) {
            ENTITIES[depth] = entity;
            ROOTS[depth].set(root);
        }
        depth++;
    }

    public static void pop(LivingEntity entity) {
        if (depth == 0) return;
        depth--;
        if (depth < MAX_DEPTH) ENTITIES[depth] = null;
    }

    /** The root matrix of the innermost render of {@code entity}, or null outside one. */
    public static @Nullable Matrix4f rootFor(LivingEntity entity) {
        for (int i = Math.min(depth, MAX_DEPTH) - 1; i >= 0; i--) {
            if (ENTITIES[i] == entity) return ROOTS[i];
        }
        return null;
    }
}
