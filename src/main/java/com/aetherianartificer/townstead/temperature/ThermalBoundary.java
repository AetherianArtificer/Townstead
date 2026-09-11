package com.aetherianartificer.townstead.temperature;

import java.util.function.IntFunction;

/** Bounded series resistance through solid layers to another thermal reservoir. */
public final class ThermalBoundary {
    public static final int MAX_DEPTH = 8;
    public record Layer(boolean loaded, boolean solid, boolean sameVolume, double conductance, double reservoir) {}
    public record Exchange(double conductance, double reservoir) {}
    private ThermalBoundary() {}

    public static Exchange trace(IntFunction<Layer> read, double opening, double ambient) {
        double resistance = 0;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Layer layer = read.apply(depth);
            if (!layer.loaded()) return new Exchange(0, ambient);
            if (!layer.solid()) {
                if (layer.sameVolume()) return new Exchange(0, ambient);
                return new Exchange(resistance > 0 ? 1 / resistance : opening, layer.reservoir());
            }
            if (!Double.isFinite(layer.conductance()) || layer.conductance() <= 0) return new Exchange(0, ambient);
            resistance += 1 / layer.conductance();
        }
        return new Exchange(1 / resistance, ambient);
    }
}
