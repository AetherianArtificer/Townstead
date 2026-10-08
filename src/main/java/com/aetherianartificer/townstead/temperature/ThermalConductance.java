package com.aetherianartificer.townstead.temperature;

/** Effective face conductance in W/K with gameplay material ratios, not measured block properties. */
public final class ThermalConductance {
    public enum Material { INSULATION, WOOD, EARTH, MASONRY, GLASS, METAL, POROUS }
    private ThermalConductance() {}
    public static double rate(Material material, double wall, double insulated, double opening,
                              boolean thin, boolean aperture, boolean open) {
        if (aperture && open) return opening;
        double rate = switch (material) {
            case INSULATION -> insulated;
            case WOOD -> wall * 0.6;
            case EARTH -> wall * 0.8;
            case MASONRY -> wall;
            case GLASS -> wall * 2;
            case METAL -> wall * 3;
            case POROUS -> wall * 4;
        };
        // Closed doors/window frames leak at their seals, even when made from insulating material.
        if (aperture) return Math.max(wall, rate);
        return rate * (thin ? 2 : 1);
    }
}
