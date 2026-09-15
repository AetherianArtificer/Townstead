package com.aetherianartificer.townstead.temperature;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Bounded caches owned by live world instances; values must not retain their world. */
public final class ThermalCache<W, K, V> {
    private final Map<W, Map<K, Entry<V>>> worlds = new WeakHashMap<>();
    private final long ttl;
    private final int capacity;

    public ThermalCache(long ttl, int capacity) {
        this.ttl = ttl;
        this.capacity = capacity;
    }

    public synchronized V get(W world, K key, long now, Supplier<V> sample) {
        Map<K, Entry<V>> entries = worlds.computeIfAbsent(world, ignored -> new LinkedHashMap<>());
        Entry<V> entry = entries.get(key);
        if (entry != null && now >= entry.sampledAt && now - entry.sampledAt < ttl) return entry.value;
        V value = sample.get();
        // Failed queries are retried, not turned into apparently valid cached readings.
        if (value == null) return null;
        if (entries.size() >= capacity && !entries.containsKey(key)) {
            entries.remove(entries.keySet().iterator().next());
        }
        entries.put(key, new Entry<>(now, value));
        return value;
    }

    public synchronized void clear() { worlds.clear(); }

    private record Entry<V>(long sampledAt, V value) {}
}
