package com.aetherianartificer.townstead.root.appearance;

import net.minecraft.util.RandomSource;

import java.util.List;

/** A weighted, multi-stop RGB or HSV fantasy hair gradient. */
public record HairGradient(List<Integer> stops, int weight, Space space) {

    public enum Space {
        RGB, HSV;

        public static Space parse(String value) {
            return "rgb".equalsIgnoreCase(value) ? RGB : HSV;
        }
    }

    public HairGradient {
        stops = stops == null ? List.of() : stops.stream().map(rgb -> rgb & 0xFFFFFF).toList();
        weight = Math.max(0, weight);
        space = space == null ? Space.HSV : space;
    }

    public int sample(RandomSource random) {
        return colorAt(random.nextFloat());
    }

    /** Uniformly traverses every segment, including both endpoints. */
    public int colorAt(float position) {
        if (stops.isEmpty()) return 0xFF000001;
        if (stops.size() == 1) return argb(stops.get(0));
        float scaled = clamp01(position) * (stops.size() - 1);
        int segment = Math.min((int) scaled, stops.size() - 2);
        return argb(interpolate(stops.get(segment), stops.get(segment + 1), scaled - segment, space));
    }

    /** Find the nearest point on this rendered gradient (sampled finely enough for 24-bit editor input). */
    public int nearest(int argb) {
        int best = colorAt(0f);
        long distance = distance(best, argb);
        int samples = Math.max(64, (stops.size() - 1) * 64);
        for (int i = 1; i <= samples; i++) {
            int candidate = colorAt(i / (float) samples);
            long next = distance(candidate, argb);
            if (next < distance) {
                best = candidate;
                distance = next;
            }
        }
        return best;
    }

    static int interpolate(int from, int to, float amount, Space space) {
        float t = clamp01(amount);
        if (space == Space.RGB) {
            int r = Math.round(channel(from, 16) + (channel(to, 16) - channel(from, 16)) * t);
            int g = Math.round(channel(from, 8) + (channel(to, 8) - channel(from, 8)) * t);
            int b = Math.round(channel(from, 0) + (channel(to, 0) - channel(from, 0)) * t);
            return r << 16 | g << 8 | b;
        }
        float[] a = toHsv(from);
        float[] b = toHsv(to);
        // Hue is undefined for gray/white/black. Borrow the chromatic endpoint's hue so a
        // blue-to-white gradient fades through pale blue rather than circling toward red.
        if (a[1] < 0.0001f) a[0] = b[0];
        if (b[1] < 0.0001f) b[0] = a[0];
        float delta = b[0] - a[0];
        if (delta > 0.5f) delta -= 1f;
        if (delta < -0.5f) delta += 1f;
        float hue = (a[0] + delta * t + 1f) % 1f;
        return fromHsv(hue, lerp(a[1], b[1], t), lerp(a[2], b[2], t));
    }

    private static float[] toHsv(int rgb) {
        float r = channel(rgb, 16) / 255f;
        float g = channel(rgb, 8) / 255f;
        float b = channel(rgb, 0) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = 0f;
        if (delta != 0f) {
            if (max == r) hue = ((g - b) / delta) % 6f;
            else if (max == g) hue = (b - r) / delta + 2f;
            else hue = (r - g) / delta + 4f;
            hue /= 6f;
            if (hue < 0f) hue += 1f;
        }
        return new float[]{hue, max == 0f ? 0f : delta / max, max};
    }

    private static int fromHsv(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        int sector = (int) Math.floor(h);
        float f = h - sector;
        float p = value * (1f - saturation);
        float q = value * (1f - saturation * f);
        float t = value * (1f - saturation * (1f - f));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return Math.round(r * 255f) << 16 | Math.round(g * 255f) << 8 | Math.round(b * 255f);
    }

    private static int argb(int rgb) {
        rgb &= 0xFFFFFF;
        return rgb == 0 ? 0xFF000001 : 0xFF000000 | rgb;
    }

    private static int channel(int rgb, int shift) { return rgb >> shift & 255; }
    private static float clamp01(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static long distance(int a, int b) {
        int dr = channel(a, 16) - channel(b, 16);
        int dg = channel(a, 8) - channel(b, 8);
        int db = channel(a, 0) - channel(b, 0);
        return (long) dr * dr + (long) dg * dg + (long) db * db;
    }
}
