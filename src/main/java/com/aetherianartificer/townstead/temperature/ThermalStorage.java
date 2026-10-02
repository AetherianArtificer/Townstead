package com.aetherianartificer.townstead.temperature;

import java.util.List;

/** Air coupled to finite surface reservoirs. Implicit steps conserve exchanged energy. */
public final class ThermalStorage {
    private ThermalStorage() {}
    public record Surface(double capacity, double airConductance, double outerConductance,
                          double reservoir, double temperature) {
        public Surface {
            if (!Double.isFinite(capacity) || capacity <= 0 || !Double.isFinite(airConductance)
                    || airConductance < 0 || !Double.isFinite(outerConductance) || outerConductance < 0
                    || !Double.isFinite(reservoir) || !Double.isFinite(temperature))
                throw new IllegalArgumentException("Invalid thermal surface");
        }
    }
    public record Result(double air, double[] surfaces) {}

    /** Split existing steady conductance around storage without changing steady heat loss. */
    public static Surface surface(double capacity, double steadyConductance, double reservoir, double temperature) {
        double inside = Math.max(5, 2 * steadyConductance);
        double outside = steadyConductance == 0 ? 0 : 1 / (1 / steadyConductance - 1 / inside);
        return new Surface(capacity, inside, outside, reservoir, temperature);
    }

    public static Result advance(double air, double airCapacity, double power, double directConductance,
                                 double reservoir, List<Surface> surfaces, double seconds) {
        if (!Double.isFinite(air) || !Double.isFinite(airCapacity) || airCapacity <= 0
                || !Double.isFinite(power) || !Double.isFinite(directConductance) || directConductance < 0
                || !Double.isFinite(reservoir) || !Double.isFinite(seconds) || seconds < 0)
            throw new IllegalArgumentException("Invalid thermal storage balance");
        double[] temperatures = surfaces.stream().mapToDouble(Surface::temperature).toArray();
        if (seconds == 0) return new Result(air, temperatures);
        // Normal runtime is 20 simulated seconds per update; cap subdivision for delayed ticks.
        int steps = Math.max(1, Math.min(100, (int) Math.ceil(seconds / 5)));
        double dt = seconds / steps;
        double[] denominator = new double[surfaces.size()], rhs = new double[surfaces.size()];
        for (int step = 0; step < steps; step++) {
            double divisor = airCapacity / dt + directConductance;
            double numerator = airCapacity / dt * air + power + directConductance * reservoir;
            for (int i = 0; i < surfaces.size(); i++) {
                Surface s = surfaces.get(i);
                denominator[i] = s.capacity / dt + s.airConductance + s.outerConductance;
                rhs[i] = s.capacity / dt * temperatures[i] + s.outerConductance * s.reservoir;
                divisor += s.airConductance * (s.capacity / dt + s.outerConductance) / denominator[i];
                numerator += s.airConductance * rhs[i] / denominator[i];
            }
            air = numerator / divisor;
            for (int i = 0; i < surfaces.size(); i++)
                temperatures[i] = (rhs[i] + surfaces.get(i).airConductance * air) / denominator[i];
        }
        return new Result(air, temperatures);
    }
}
