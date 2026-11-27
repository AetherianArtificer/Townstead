package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.temperature.ThermalConductance.Material;

/** A block's thermal class packed into one byte: what conducts and stores heat, and nothing else about the block. */
public final class ThermalWall {
    public static final int FULL = 0, SLAB = 1, STAIR = 2, BARS = 3, DOOR = 4, GATE = 5;
    private static final Material[] MATERIALS = Material.values();
    private ThermalWall() {}

    public static int pack(Material material, int shape, boolean hasOpen, boolean open) {
        return material.ordinal() | shape << 3 | (hasOpen ? 64 : 0) | (open ? 128 : 0);
    }
    public static Material material(int wall) { return MATERIALS[wall & 7]; }
    private static int shape(int wall) { return wall >> 3 & 7; }
    private static boolean hasOpen(int wall) { return (wall & 64) != 0; }
    private static boolean open(int wall) { return (wall & 128) != 0; }

    public static boolean openAperture(int wall) {
        int shape = shape(wall);
        return (shape == DOOR || shape == GATE || material(wall) == Material.GLASS) && hasOpen(wall) && open(wall);
    }
    public static double capacityFraction(int wall) {
        return switch (shape(wall)) {
            case DOOR, BARS -> 0.125;
            case SLAB -> 0.5;
            case STAIR -> 0.75;
            default -> 1;
        };
    }
    public static double conductance(int wall, double wallRate, double insulated, double opening) {
        int shape = shape(wall);
        Material material = material(wall);
        boolean aperture = shape == DOOR || shape == GATE || material == Material.GLASS && hasOpen(wall);
        boolean thin = shape == STAIR || shape == BARS || shape == SLAB;
        return ThermalConductance.rate(material, wallRate, insulated, opening, thin, aperture, aperture && hasOpen(wall) && open(wall));
    }

    /** Every class's conductance and stored heat under one settings instance. */
    public static final class Table {
        final double[] conductance = new double[256], capacity = new double[256];
        final double opening;
        public Table(double wallRate, double insulated, double opening, double airCapacity,
                     java.util.function.ToDoubleFunction<Material> materialCapacity) {
            this.opening = opening;
            for (int wall = 0; wall < 256; wall++) {
                if ((wall & 7) >= MATERIALS.length) continue;
                conductance[wall] = conductance(wall, wallRate, insulated, opening);
                capacity[wall] = openAperture(wall) ? airCapacity
                        : materialCapacity.applyAsDouble(material(wall)) * capacityFraction(wall);
            }
        }
    }
}
