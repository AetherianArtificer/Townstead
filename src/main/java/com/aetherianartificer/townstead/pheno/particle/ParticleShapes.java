package com.aetherianartificer.townstead.pheno.particle;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where the particles of a shaped burst go and which way each one flies. Shapes are laid out
 * around an origin, facing a direction (the actor's look, flattened for ground shapes).
 *
 * <ul>
 *   <li>{@code ring}: a flat circle of {@code radius}, flying outward.</li>
 *   <li>{@code ring_in}: the same circle, flying inward to the centre.</li>
 *   <li>{@code orbit}: the circle, flying around it.</li>
 *   <li>{@code spokes}: lines from the centre out to {@code radius}, flying outward.</li>
 *   <li>{@code dome}: a half-sphere of {@code radius}, flying outward.</li>
 *   <li>{@code helix}: a spiral of {@code radius} rising {@code height}, two turns.</li>
 *   <li>{@code burst_up}: a fountain from a disc of {@code radius}, flying up to {@code height}.</li>
 *   <li>{@code cone}: along the look for {@code length}, widening to {@code radius}, flying forward.</li>
 *   <li>{@code line}: along the look for {@code length}, flying forward.</li>
 * </ul>
 */
public final class ParticleShapes {

    public enum Shape { POINT, RING, RING_IN, ORBIT, SPOKES, DOME, HELIX, BURST_UP, CONE, LINE }

    /** One particle: where it starts and its direction of travel (unit length or zero). */
    public record Emission(Vec3 position, Vec3 direction) {}

    private ParticleShapes() {}

    public static Shape parse(String raw) {
        if (raw == null) return Shape.POINT;
        try {
            return Shape.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Lay out {@code count} particles of {@code shape} around {@code origin}, facing {@code look}. */
    public static List<Emission> layout(Shape shape, int count, Vec3 origin, Vec3 look, double radius,
                                        double height, double length, RandomSource random) {
        List<Emission> out = new ArrayList<>(count);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 forward = look.lengthSqr() < 1.0e-6 ? flat : look.normalize();
        // Two axes perpendicular to forward, for cones and lines.
        Vec3 side = forward.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(forward).normalize();
        for (int i = 0; i < count; i++) {
            double t = count <= 1 ? 0 : i / (double) count;
            double angle = t * Math.PI * 2;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            switch (shape) {
                case RING -> out.add(new Emission(origin.add(radial.scale(radius)), radial));
                case RING_IN -> out.add(new Emission(origin.add(radial.scale(radius)), radial.scale(-1)));
                case ORBIT -> out.add(new Emission(origin.add(radial.scale(radius)),
                        new Vec3(-radial.z, 0, radial.x)));
                case SPOKES -> {
                    int spokes = Math.max(3, Math.min(12, count / 3));
                    double a = (i % spokes) * Math.PI * 2 / spokes;
                    double along = radius * ((i / spokes) + 1) / Math.max(1.0, Math.ceil(count / (double) spokes));
                    Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                    out.add(new Emission(origin.add(dir.scale(along)), dir));
                }
                case DOME -> {
                    // Even spread over the upper hemisphere (golden-angle spiral).
                    double y = 1 - (i + 0.5) / count;
                    double r = Math.sqrt(Math.max(0, 1 - y * y));
                    double a = i * 2.399963229728653;
                    Vec3 dir = new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
                    out.add(new Emission(origin.add(dir.scale(radius)), dir));
                }
                case HELIX -> {
                    double a = t * Math.PI * 4;
                    Vec3 at = origin.add(Math.cos(a) * radius, t * height, Math.sin(a) * radius);
                    out.add(new Emission(at, new Vec3(0, 1, 0)));
                }
                case BURST_UP -> {
                    double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * radius;
                    Vec3 at = origin.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    out.add(new Emission(at, new Vec3(0, 1, 0)));
                }
                case CONE -> {
                    double along = random.nextDouble() * length;
                    double spreadR = radius * (along / Math.max(0.001, length)) * Math.sqrt(random.nextDouble());
                    double a = random.nextDouble() * Math.PI * 2;
                    Vec3 offset = side.scale(Math.cos(a) * spreadR).add(up.scale(Math.sin(a) * spreadR));
                    out.add(new Emission(origin.add(forward.scale(along)).add(offset), forward));
                }
                case LINE -> out.add(new Emission(origin.add(forward.scale(length * (count <= 1 ? 0 : i / (count - 1.0)))),
                        forward));
                case POINT -> out.add(new Emission(origin, Vec3.ZERO));
            }
        }
        return out;
    }
}
