package com.aetherianartificer.townstead.temperature;

import java.util.*;

/** A simultaneous finite-volume energy balance. Each internal link is assembled exactly once. */
public final class ThermalNetwork {
    private ThermalNetwork() {}
    public record Node(double capacity, double temperature, double power, double outsideConductance, double outside) {
        public Node {
            if (!Double.isFinite(capacity) || capacity <= 0 || !Double.isFinite(temperature)
                    || !Double.isFinite(power) || !Double.isFinite(outsideConductance)
                    || outsideConductance < 0 || !Double.isFinite(outside))
                throw new IllegalArgumentException("Invalid thermal node");
        }
    }
    public record Link(int a, int b, double conductance) {
        public Link {
            if (a < 0 || b < 0 || a == b || !Double.isFinite(conductance) || conductance < 0)
                throw new IllegalArgumentException("Invalid thermal link");
        }
    }
    public record Result(double[] temperatures, double suppliedJoules, double escapedJoules, double storedJoules) {
        public double errorJoules() { return suppliedJoules - escapedJoules - storedJoules; }
    }

    /** Backward Euler with a diagonally preconditioned conjugate-gradient solve of the SPD matrix. */
    public static Result advance(List<Node> nodes, List<Link> links, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || seconds > 10000)
            throw new IllegalArgumentException("Invalid thermal duration");
        int n = nodes.size();
        double[] t = nodes.stream().mapToDouble(Node::temperature).toArray();
        for (Link link : links) if (link.a >= n || link.b >= n) throw new IllegalArgumentException("Missing thermal node");
        if (seconds == 0 || n == 0) return new Result(t, 0, 0, 0);
        int steps = Math.max(1, (int) Math.ceil(seconds / 5));
        double dt = seconds / steps, escaped = 0, supplied = 0;
        double[] diagonal = new double[n], rhs = new double[n];
        for (int step = 0; step < steps; step++) {
            for (int i = 0; i < n; i++) {
                Node node = nodes.get(i);
                diagonal[i] = node.capacity / dt + node.outsideConductance;
                rhs[i] = node.capacity / dt * t[i] + node.power + node.outsideConductance * node.outside;
            }
            for (Link link : links) { diagonal[link.a] += link.conductance; diagonal[link.b] += link.conductance; }
            solve(t, rhs, diagonal, links);
            for (int i = 0; i < n; i++) {
                Node node = nodes.get(i);
                supplied += dt * node.power;
                escaped += dt * node.outsideConductance * (t[i] - node.outside);
            }
        }
        double stored = 0;
        for (int i = 0; i < n; i++) stored += nodes.get(i).capacity * (t[i] - nodes.get(i).temperature);
        return new Result(t, supplied, escaped, stored);
    }

    private static void multiply(double[] x, double[] out, double[] diagonal, List<Link> links) {
        for (int i = 0; i < x.length; i++) out[i] = diagonal[i] * x[i];
        for (Link link : links) {
            out[link.a] -= link.conductance * x[link.b];
            out[link.b] -= link.conductance * x[link.a];
        }
    }
    private static double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }
    private static void solve(double[] x, double[] rhs, double[] diagonal, List<Link> links) {
        int n = x.length;
        double[] r = new double[n], z = new double[n], p = new double[n], ap = new double[n];
        multiply(x, ap, diagonal, links);
        for (int i = 0; i < n; i++) { r[i] = rhs[i] - ap[i]; z[i] = p[i] = r[i] / diagonal[i]; }
        double tolerance = Math.max(1e-18, dot(rhs, rhs) * 1e-24);
        double rz = dot(r, z);
        for (int iteration = 0; iteration < 512; iteration++) {
            if (dot(r, r) <= tolerance) return;
            multiply(p, ap, diagonal, links);
            double alpha = rz / dot(p, ap);
            for (int i = 0; i < n; i++) { x[i] += alpha * p[i]; r[i] -= alpha * ap[i]; z[i] = r[i] / diagonal[i]; }
            double next = dot(r, z), beta = next / rz;
            for (int i = 0; i < n; i++) p[i] = z[i] + beta * p[i];
            rz = next;
        }
        // Do not silently publish an unconverged or non-finite energy balance.
        throw new IllegalStateException("Thermal network did not converge");
    }
}
