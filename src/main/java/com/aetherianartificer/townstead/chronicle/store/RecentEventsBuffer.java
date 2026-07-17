package com.aetherianartificer.townstead.chronicle.store;

import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * In-memory ring of the most recent events. Aggregate derivation (memories,
 * digests, gossip candidates) reads from here so the tick path never queries
 * the archive.
 */
public final class RecentEventsBuffer {

    private static final int CAPACITY = 1024;

    private final ArrayDeque<ChronicleEvent> ring = new ArrayDeque<>(CAPACITY);

    public synchronized void add(ChronicleEvent event) {
        if (ring.size() >= CAPACITY) ring.pollFirst();
        ring.addLast(event);
    }

    public synchronized List<ChronicleEvent> snapshot() {
        return new ArrayList<>(ring);
    }

    public synchronized List<ChronicleEvent> matching(Predicate<ChronicleEvent> filter, int limit) {
        List<ChronicleEvent> result = new ArrayList<>();
        var it = ring.descendingIterator();
        while (it.hasNext() && result.size() < limit) {
            ChronicleEvent event = it.next();
            if (filter.test(event)) result.add(event);
        }
        return result;
    }

    public synchronized List<ChronicleEvent> recentBySubject(UUID subject, int limit) {
        return matching(event -> {
            for (var participation : event.participations()) {
                if (subject.equals(participation.ref().uuid())) return true;
            }
            return false;
        }, limit);
    }

    public synchronized ChronicleEvent byId(long eventId) {
        for (ChronicleEvent event : ring) {
            if (event.eventId() == eventId) return event;
        }
        return null;
    }

    public synchronized int size() {
        return ring.size();
    }

    public synchronized void clear() {
        ring.clear();
    }
}
