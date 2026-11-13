package com.aetherianartificer.townstead.root.attachment;

import org.joml.Vector3f;

/**
 * Keeps one physics chain segment, from a fixed {@code head} to a movable {@code tip}, out of
 * solid things. The whole segment is tested rather than just its tip, so it can't pass through
 * something thinner than itself. A contact turns the segment about its head and keeps its length.
 * A contact near the head would take a huge swing to clear, so only the tip answers those: a root
 * that starts inside a block doesn't throw the rest of the chain around.
 */
final class ChainCollision {

    private static final int PROJECTIONS = 4;
    private static final float NEAR_HEAD = 0.15f;
    private static final float MIN_LEVER = 0.3f;

    private ChainCollision() {}

    /**
     * Pushes the segment out of every box. {@code boxes} holds six floats per box (min x, y, z, then
     * max x, y, z), and {@code ox}, {@code oy}, {@code oz} move them into the segment's frame.
     */
    static boolean outOfBoxes(Vector3f head, Vector3f tip, float length, float radius, float[] boxes, int count,
                              float ox, float oy, float oz) {
        boolean hit = false;
        for (int i = 0; i < count; i++) {
            int b = i * 6;
            hit |= outOfBox(head, tip, length, radius, boxes[b] + ox, boxes[b + 1] + oy, boxes[b + 2] + oz,
                    boxes[b + 3] + ox, boxes[b + 4] + oy, boxes[b + 5] + oz);
        }
        return hit;
    }

    static boolean outOfBox(Vector3f head, Vector3f tip, float length, float radius, float minX, float minY,
                            float minZ, float maxX, float maxY, float maxZ) {
        if (Math.max(head.x, tip.x) + radius <= minX || Math.min(head.x, tip.x) - radius >= maxX
                || Math.max(head.y, tip.y) + radius <= minY || Math.min(head.y, tip.y) - radius >= maxY
                || Math.max(head.z, tip.z) + radius <= minZ || Math.min(head.z, tip.z) - radius >= maxZ) {
            return false;
        }
        float dx = tip.x - head.x, dy = tip.y - head.y, dz = tip.z - head.z;
        boolean headInside = head.x > minX && head.x < maxX && head.y > minY && head.y < maxY
                && head.z > minZ && head.z < maxZ;
        if (!headInside) {
            // Crossing the box: bring the tip back behind the face the segment came in by.
            float enter = 0f, leave = 1f;
            int face = -1;
            float[] mins = {minX, minY, minZ}, maxes = {maxX, maxY, maxZ};
            float[] origin = {head.x, head.y, head.z}, delta = {dx, dy, dz};
            for (int axis = 0; axis < 3; axis++) {
                if (Math.abs(delta[axis]) < 1e-8f) {
                    if (origin[axis] <= mins[axis] || origin[axis] >= maxes[axis]) {
                        enter = 2f;
                        break;
                    }
                    continue;
                }
                float near = (mins[axis] - origin[axis]) / delta[axis];
                float far = (maxes[axis] - origin[axis]) / delta[axis];
                boolean flipped = near > far;
                if (flipped) {
                    float swap = near;
                    near = far;
                    far = swap;
                }
                if (near > enter) {
                    enter = near;
                    face = axis * 2 + (flipped ? 1 : 0);
                }
                leave = Math.min(leave, far);
            }
            if (face >= 0 && enter <= leave) {
                layAgainst(head, tip, length, face / 2, (face & 1) == 0 ? mins[face / 2] - radius
                        : maxes[face / 2] + radius);
                return true;
            }
        }
        // Clear of the box, or starting inside it: the nearest pair decides, stepping between the
        // segment and the box until they stop moving. A head inside can only answer with its tip.
        float lengthSq = dx * dx + dy * dy + dz * dz;
        float t = 1f;
        for (int i = 0; i < PROJECTIONS && lengthSq > 1e-10f && !headInside; i++) {
            float qx = clamp(head.x + dx * t, minX, maxX);
            float qy = clamp(head.y + dy * t, minY, maxY);
            float qz = clamp(head.z + dz * t, minZ, maxZ);
            t = clamp(((qx - head.x) * dx + (qy - head.y) * dy + (qz - head.z) * dz) / lengthSq, 0f, 1f);
        }
        if (t < NEAR_HEAD) t = 1f;
        float cx = head.x + dx * t, cy = head.y + dy * t, cz = head.z + dz * t;
        float nx = cx - clamp(cx, minX, maxX), ny = cy - clamp(cy, minY, maxY), nz = cz - clamp(cz, minZ, maxZ);
        float distanceSq = nx * nx + ny * ny + nz * nz;
        if (distanceSq >= radius * radius) return false;
        float push;
        if (distanceSq > 1e-8f) {
            float distance = (float) Math.sqrt(distanceSq);
            nx /= distance;
            ny /= distance;
            nz /= distance;
            push = radius - distance;
        } else {
            float[] exit = {cx - minX, maxX - cx, cy - minY, maxY - cy, cz - minZ, maxZ - cz};
            int face = 0;
            for (int f = 1; f < 6; f++) {
                if (exit[f] < exit[face]) face = f;
            }
            nx = face == 0 ? -1f : face == 1 ? 1f : 0f;
            ny = face == 2 ? -1f : face == 3 ? 1f : 0f;
            nz = face == 4 ? -1f : face == 5 ? 1f : 0f;
            push = exit[face] + radius;
        }
        turn(head, tip, length, nx, ny, nz, push / Math.max(t, MIN_LEVER));
        return true;
    }

    /**
     * Pushes the segment out of a capsule's core by {@code reach}. A pose the author placed against
     * the capsule stays allowed: the segment only has to keep as far out as its goal is.
     */
    static boolean outOfCapsule(Vector3f head, Vector3f tip, Vector3f goal, float length, float reach,
                                Vector3f a, Vector3f b) {
        float[] pair = new float[2];
        float allowed = Math.min(reach, (float) Math.sqrt(nearest(head, goal, a, b, pair)));
        float distanceSq = nearest(head, tip, a, b, pair);
        if (distanceSq >= allowed * allowed) return false;
        float t = pair[0];
        if (t < NEAR_HEAD) {
            t = 1f;
            distanceSq = nearest(tip, tip, a, b, pair);
            if (distanceSq >= allowed * allowed) return false;
        }
        float s = pair[1];
        float cx = head.x + (tip.x - head.x) * t, cy = head.y + (tip.y - head.y) * t, cz = head.z + (tip.z - head.z) * t;
        float qx = a.x + (b.x - a.x) * s, qy = a.y + (b.y - a.y) * s, qz = a.z + (b.z - a.z) * s;
        float nx = cx - qx, ny = cy - qy, nz = cz - qz;
        float distance = (float) Math.sqrt(distanceSq);
        if (distance > 1e-4f) {
            nx /= distance;
            ny /= distance;
            nz /= distance;
        } else {
            nx = goal.x - qx;
            ny = goal.y - qy;
            nz = goal.z - qz;
            float n = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (n < 1e-5f) return false;
            nx /= n;
            ny /= n;
            nz /= n;
        }
        turn(head, tip, length, nx, ny, nz, (allowed - distance) / Math.max(t, MIN_LEVER));
        return true;
    }

    /**
     * The squared distance between segments p1-q1 and p2-q2. {@code out} receives the nearest pair's
     * position along each, 0 to 1.
     */
    static float nearest(Vector3f p1, Vector3f q1, Vector3f p2, Vector3f q2, float[] out) {
        float d1x = q1.x - p1.x, d1y = q1.y - p1.y, d1z = q1.z - p1.z;
        float d2x = q2.x - p2.x, d2y = q2.y - p2.y, d2z = q2.z - p2.z;
        float rx = p1.x - p2.x, ry = p1.y - p2.y, rz = p1.z - p2.z;
        float a = d1x * d1x + d1y * d1y + d1z * d1z;
        float e = d2x * d2x + d2y * d2y + d2z * d2z;
        float f = d2x * rx + d2y * ry + d2z * rz;
        float s, t;
        if (a <= 1e-10f && e <= 1e-10f) {
            s = 0f;
            t = 0f;
        } else if (a <= 1e-10f) {
            s = 0f;
            t = clamp(f / e, 0f, 1f);
        } else {
            float c = d1x * rx + d1y * ry + d1z * rz;
            if (e <= 1e-10f) {
                t = 0f;
                s = clamp(-c / a, 0f, 1f);
            } else {
                float d = d1x * d2x + d1y * d2y + d1z * d2z;
                float denom = a * e - d * d;
                s = denom > 1e-10f ? clamp((d * f - c * e) / denom, 0f, 1f) : 0f;
                t = (d * s + f) / e;
                if (t < 0f) {
                    t = 0f;
                    s = clamp(-c / a, 0f, 1f);
                } else if (t > 1f) {
                    t = 1f;
                    s = clamp((d - c) / a, 0f, 1f);
                }
            }
        }
        out[0] = s;
        out[1] = t;
        float x = rx + d1x * s - d2x * t, y = ry + d1y * s - d2y * t, z = rz + d1z * s - d2z * t;
        return x * x + y * y + z * z;
    }

    /**
     * Puts the tip on a face's plane at the segment's length, keeping the way it already leaned along
     * the face. A segment that hit dead on has no lean, so it falls: down, or sideways off a floor.
     */
    private static void layAgainst(Vector3f head, Vector3f tip, float length, int axis, float plane) {
        float[] start = {head.x, head.y, head.z};
        float[] lean = {tip.x - head.x, tip.y - head.y, tip.z - head.z};
        float across = plane - start[axis];
        lean[axis] = 0f;
        float leanLength = (float) Math.sqrt(lean[0] * lean[0] + lean[1] * lean[1] + lean[2] * lean[2]);
        if (leanLength < 1e-4f) {
            lean[0] = lean[1] = lean[2] = 0f;
            lean[axis == 1 ? 0 : 1] = axis == 1 ? 1f : -1f;
            leanLength = 1f;
        }
        float along = (float) Math.sqrt(Math.max(0f, length * length - across * across)) / leanLength;
        float[] out = {start[0] + lean[0] * along, start[1] + lean[1] * along, start[2] + lean[2] * along};
        out[axis] = plane;
        tip.set(out[0], out[1], out[2]);
    }

    private static void turn(Vector3f head, Vector3f tip, float length, float nx, float ny, float nz, float amount) {
        tip.add(nx * amount, ny * amount, nz * amount);
        float x = tip.x - head.x, y = tip.y - head.y, z = tip.z - head.z;
        float current = (float) Math.sqrt(x * x + y * y + z * z);
        if (current < 1e-5f) return;
        float scale = length / current;
        tip.set(head.x + x * scale, head.y + y * scale, head.z + z * scale);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
