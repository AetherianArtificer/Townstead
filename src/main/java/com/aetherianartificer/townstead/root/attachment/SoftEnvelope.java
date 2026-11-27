package com.aetherianartificer.townstead.root.attachment;

/**
 * Squashes a soft part's lobes into the room around them. Each lobe grows from a root point that stays put
 * (where it joins the body), and each of its six sides gives way to whatever solid is in its way, down to
 * {@code min} of its own size. Where it lost room it can bulge the same share back on sides that are free,
 * so it keeps its volume. Where it's squashed sideways it pushes its bearer the other way.
 *
 * <p>Boxes are six numbers, min x, y, z then max x, y, z, in blocks from the bearer's feet in world axes.
 * Plain numbers, so it can be tested and both sides work it out the same.</p>
 */
public final class SoftEnvelope {

    // How far a squashed side stays off what stopped it.
    private static final double GAP = 0.01;
    // The most a free side bulges to make up for what the others lost, as a share of its size.
    private static final double MAX_BULGE = 1.35;
    private static final double EPS = 1e-6;
    private static final int[] AXIS_ORDER = {1, 0, 2};

    private SoftEnvelope() {}

    /**
     * {@code lobes} as squashed now, and {@code squeezed} at their least, which is what stops the bearer.
     * {@code pushX}, {@code pushZ} add up how far each lobe is squashed from each side: the bearer is pushed
     * away from what it presses on. {@code stuck} says some lobe couldn't fit even at its least.
     */
    public record Result(double[] lobes, double[] squeezed, double pushX, double pushZ, boolean stuck) {}

    /**
     * @param lobes  six numbers a lobe, at its full size
     * @param roots  three numbers a lobe: the point it grows from, moved inside it if it isn't
     * @param min    the least share of each side it squashes to, 0..1
     * @param solids six numbers a box, the solid blocks around it
     */
    public static Result squash(double[] lobes, double[] roots, double min, boolean keepVolume,
                                double[] solids, int solidCount) {
        int count = lobes.length / 6;
        double[] out = new double[count * 6];
        double[] squeezed = new double[count * 6];
        double pushX = 0.0, pushZ = 0.0;
        boolean stuck = false;
        double[][] want = new double[3][2];
        double[][] now = new double[3][2];
        double[] root = new double[3];
        for (int l = 0; l < count; l++) {
            int b = l * 6;
            for (int a = 0; a < 3; a++) {
                double lo = lobes[b + a], hi = lobes[b + 3 + a];
                root[a] = Math.max(lo, Math.min(hi, roots[l * 3 + a]));
                want[a][0] = root[a] - lo;
                want[a][1] = hi - root[a];
                now[a][0] = want[a][0];
                now[a][1] = want[a][1];
            }
            // Twice round, since what one axis gives up changes what the others run into.
            for (int pass = 0; pass < 2; pass++) {
                for (int a : AXIS_ORDER) {
                    for (int side = 0; side < 2; side++) {
                        double room = clearance(root, now, a, side, solids, solidCount) - GAP;
                        now[a][side] = Math.max(want[a][side] * min, Math.min(want[a][side], room));
                    }
                }
            }
            for (int a = 0; a < 3; a++) {
                for (int side = 0; side < 2; side++) {
                    if (clearance(root, now, a, side, solids, solidCount) < now[a][side] - EPS) stuck = true;
                }
            }
            if (keepVolume) bulge(root, want, now, solids, solidCount);
            pushX += squashed(want, now, 0, 0) - squashed(want, now, 0, 1);
            pushZ += squashed(want, now, 2, 0) - squashed(want, now, 2, 1);
            for (int a = 0; a < 3; a++) {
                out[b + a] = root[a] - now[a][0];
                out[b + 3 + a] = root[a] + now[a][1];
                squeezed[b + a] = root[a] - want[a][0] * min;
                squeezed[b + 3 + a] = root[a] + want[a][1] * min;
            }
        }
        return new Result(out, squeezed, pushX, pushZ, stuck);
    }

    /** How far one side lost, never what it bulged. */
    private static double squashed(double[][] want, double[][] now, int axis, int side) {
        return Math.max(0.0, want[axis][side] - now[axis][side]);
    }

    /**
     * Gives back what the squashed sides lost on axes that lost nothing, as far as the room there and
     * {@link #MAX_BULGE} allow.
     */
    private static void bulge(double[] root, double[][] want, double[][] now, double[] solids, int solidCount) {
        double before = 1.0, after = 1.0;
        int free = 0;
        boolean[] open = new boolean[3];
        for (int a = 0; a < 3; a++) {
            double full = want[a][0] + want[a][1];
            double kept = now[a][0] + now[a][1];
            before *= full;
            after *= kept;
            open[a] = full > EPS && kept >= full - EPS;
            if (open[a]) free++;
        }
        if (free == 0 || after <= EPS || after >= before - EPS) return;
        double grow = Math.min(MAX_BULGE, Math.pow(before / after, 1.0 / free));
        for (int a = 0; a < 3; a++) {
            if (!open[a]) continue;
            for (int side = 0; side < 2; side++) {
                double room = clearance(root, now, a, side, solids, solidCount) - GAP;
                now[a][side] = Math.max(want[a][side], Math.min(want[a][side] * grow, room));
            }
        }
    }

    /**
     * How far the lobe can reach from its root along one axis, one way, before a solid that overlaps it
     * across the other two axes. Zero when the root itself is inside one.
     */
    static double clearance(double[] root, double[][] now, int axis, int side, double[] solids, int solidCount) {
        int b1 = (axis + 1) % 3, b2 = (axis + 2) % 3;
        double lo1 = root[b1] - now[b1][0], hi1 = root[b1] + now[b1][1];
        double lo2 = root[b2] - now[b2][0], hi2 = root[b2] + now[b2][1];
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < solidCount; i++) {
            int s = i * 6;
            if (solids[s + b1] >= hi1 - EPS || solids[s + 3 + b1] <= lo1 + EPS) continue;
            if (solids[s + b2] >= hi2 - EPS || solids[s + 3 + b2] <= lo2 + EPS) continue;
            double near = solids[s + axis], far = solids[s + 3 + axis];
            if (side == 1) {
                if (near >= root[axis] - EPS) best = Math.min(best, near - root[axis]);
                else if (far > root[axis]) return 0.0;
            } else {
                if (far <= root[axis] + EPS) best = Math.min(best, root[axis] - far);
                else if (near < root[axis]) return 0.0;
            }
        }
        return best;
    }

    /** A box turned about the feet by {@code degrees} of body yaw, as the box around its corners. */
    public static double[] turned(double[] box, int offset, double degrees) {
        double yaw = Math.toRadians(degrees);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int cx = 0; cx < 2; cx++) {
            for (int cz = 0; cz < 2; cz++) {
                double x = box[offset + (cx == 0 ? 0 : 3)], z = box[offset + (cz == 0 ? 2 : 5)];
                // Turning a body by +yaw turns what it carries the same way about the vertical.
                double wx = x * cos - z * sin;
                double wz = x * sin + z * cos;
                minX = Math.min(minX, wx);
                maxX = Math.max(maxX, wx);
                minZ = Math.min(minZ, wz);
                maxZ = Math.max(maxZ, wz);
            }
        }
        return new double[]{minX, box[offset + 1], minZ, maxX, box[offset + 4], maxZ};
    }
}
