package com.aetherianartificer.townstead.temperature;

import java.util.ArrayList;
import java.util.List;

/** Cold Sweat food semantics: bounded duplicate stacks, stronger same-sign food supersedes weaker. */
public final class TimedTemperatureEffects {
    private TimedTemperatureEffects() {}
    public record Entry(String key, float bodyDegrees, long expiresAt) {}

    public static List<Entry> add(List<Entry> previous, String key, float bodyDegrees,
                                  int duration, int stackLimit, long now) {
        List<Entry> active = new ArrayList<>(previous.stream().filter(e -> e.expiresAt() > now).toList());
        if (!Float.isFinite(bodyDegrees) || bodyDegrees == 0 || duration <= 0) return List.copyOf(active);
        int limit = Math.max(1, Math.min(64, stackLimit));
        long count = active.stream().filter(e -> e.key().equals(key) && e.bodyDegrees() == bodyDegrees).count();
        if (count >= limit) {
            for (int i = 0; i < active.size(); i++) {
                Entry e = active.get(i);
                if (e.key().equals(key) && e.bodyDegrees() == bodyDegrees) { active.remove(i); break; }
            }
        }
        if (active.size() >= 64) active.remove(0);
        active.add(new Entry(key, bodyDegrees, now + duration));
        return List.copyOf(active);
    }

    public static float offset(List<Entry> entries, long now) {
        float warm = 0, cool = 0;
        for (Entry e : entries) if (e.expiresAt() > now && Float.isFinite(e.bodyDegrees())) {
            warm = Math.max(warm, e.bodyDegrees());
            cool = Math.min(cool, e.bodyDegrees());
        }
        float sum = 0;
        for (Entry e : entries) if (e.expiresAt() > now && (e.bodyDegrees() == warm || e.bodyDegrees() == cool)) sum += e.bodyDegrees();
        return sum;
    }

    /** CS BASE/CORE are body-severity points, not its MC world-temperature scale (25 C/unit). */
    public static float coldSweatBodyDegrees(double points) {
        float degrees = (float) (points * (TemperatureData.DEFAULT_BAND * 3 / 100.0));
        return Float.isFinite(degrees) ? degrees : 0;
    }
}
