package com.aetherianartificer.townstead.root.attachment;

/** Where an attachment's authored volume box sits around its bearer. Plain numbers, so it can be tested. */
public final class VolumePlacement {

    private VolumePlacement() {}

    /** How a grow channel scales one axis, the same way a morph channel does. */
    public static float growFactor(float value, float axis) {
        return Math.max(0.05f, 1f + (value - 1f) * axis);
    }

    /**
     * The box, in blocks from the bearer's feet, as an axis-aligned box in world axes: min x, y, z then
     * max x, y, z. It is grown about {@code pivot}, scaled to the bearer, and turned with its body.
     * {@code +z} in the box is behind the body. A turned box is measured around its corners, so it
     * reads a little larger until the body lines up.
     */
    public static double[] place(float[] box, float[] pivot, float[] grow, float widthScale, float heightScale,
                                 float bodyYawDegrees) {
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double yaw = Math.toRadians(bodyYawDegrees);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        for (int cx = 0; cx < 2; cx++) {
            for (int cz = 0; cz < 2; cz++) {
                float rawX = cx == 0 ? box[0] : box[3];
                float rawZ = cz == 0 ? box[2] : box[5];
                double x = (pivot[0] + (rawX - pivot[0]) * grow[0]) / 16.0 * widthScale;
                double z = (pivot[2] + (rawZ - pivot[2]) * grow[2]) / 16.0 * widthScale;
                // A body facing yaw looks along (-sin, cos), so behind it is (sin, -cos).
                double worldX = x * cos + z * sin;
                double worldZ = x * sin - z * cos;
                minX = Math.min(minX, worldX);
                maxX = Math.max(maxX, worldX);
                minZ = Math.min(minZ, worldZ);
                maxZ = Math.max(maxZ, worldZ);
            }
        }
        double minY = (pivot[1] + (box[1] - pivot[1]) * grow[1]) / 16.0 * heightScale;
        double maxY = (pivot[1] + (box[4] - pivot[1]) * grow[1]) / 16.0 * heightScale;
        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /**
     * Whether a move may go ahead for a box already inside something. It can't push further in, and
     * anything that keeps it out or brings it out is allowed, so a body that grew in a tight spot can
     * always back out.
     */
    public static boolean wedgedMoveAllowed(double overlapNow, double overlapAfter) {
        return overlapAfter <= overlapNow + 1e-6;
    }
}
