package com.aetherianartificer.townstead.temperature;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.*;

/** Bounded topology discovery. Doors are boundaries even while open, avoiding room churn. */
public final class ThermalRegionScan {
    public enum Kind { INTERIOR, EXTERIOR, BARRIER, UNLOADED }
    public interface Access {
        Kind kind(long position);
        long[] neighbors(long position);
    }
    public record Face(long inside, long outside) {}
    public record Region(LongOpenHashSet cells, List<Face> faces) {}
    private ThermalRegionScan() {}
    public static Optional<Region> scan(long seed, int limit, Access access) {
        var job = new Job(seed, limit, access, null, Job.FULL);
        job.advance(Integer.MAX_VALUE);
        return job.region();
    }

    /**
     * A space too large to enclose: the first {@code limit} air cells nearest the seed, never
     * entering cells {@code owned} by other rooms. Faces to unvisited air of the same space are the
     * frontier, open to the air beyond; faces to owned cells join those rooms.
     */
    public record Partial(Region region, Set<Long> frontier) {}
    public static Optional<Partial> scanPartial(long seed, int limit, Access access, java.util.function.LongPredicate owned) {
        var job = new Job(seed, limit, access, owned, Job.PARTIAL);
        job.advance(Integer.MAX_VALUE);
        return job.region().map(region -> new Partial(region, job.frontier()));
    }

    /**
     * One scan that can stop between cells: the whole space if it closes within the limit, else the
     * partial room around the seed. Cells read so far are kept, so a change to one is noticed.
     */
    public static final class Job {
        private static final int FULL = 0, PARTIAL = 1, FACES = 2, DONE = 3;
        public final long seed;
        private final int limit;
        private final Access access;
        private final java.util.function.LongPredicate owned;
        private final Long2ObjectOpenHashMap<Kind> kinds = new Long2ObjectOpenHashMap<>();
        private LongOpenHashSet cells = new LongOpenHashSet();
        private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        private List<Face> faces = new ArrayList<>();
        private final LongOpenHashSet frontier = new LongOpenHashSet();
        private it.unimi.dsi.fastutil.longs.LongIterator faceCells;
        private int stage;
        private Region region;

        /** With {@code owned} null, only a whole enclosed space succeeds. */
        public Job(long seed, int limit, Access access, java.util.function.LongPredicate owned) {
            this(seed, limit, access, owned, FULL);
        }
        private Job(long seed, int limit, Access access, java.util.function.LongPredicate owned, int stage) {
            this.seed = seed; this.limit = limit; this.access = access; this.owned = owned; this.stage = stage;
            if (kindOf(seed) != Kind.INTERIOR || stage == PARTIAL && owned.test(seed)) { this.stage = DONE; return; }
            cells.add(seed); queue.enqueue(seed);
        }

        private Kind kindOf(long cell) {
            Kind kind = kinds.get(cell);
            if (kind == null) { kind = access.kind(cell); kinds.put(cell, kind); }
            return kind;
        }
        /** The kind this job read at a cell, or null if it has not read it. */
        public Kind seen(long cell) { return kinds.get(cell); }
        public Optional<Region> region() { return Optional.ofNullable(region); }
        public Set<Long> frontier() { return frontier; }
        /** Every kind this job read: the room's cells and everything just past them. */
        public Long2ObjectOpenHashMap<Kind> kinds() { return kinds; }

        /** Works through up to {@code count} cells; true once the job is finished. */
        public boolean advance(int count) {
            for (int i = 0; i < count && stage != DONE; i++) {
                if (stage == FULL) stepFull();
                else if (stage == PARTIAL) stepPartial();
                else stepFaces();
            }
            return stage == DONE;
        }

        private void stepFull() {
            if (queue.isEmpty()) { region = new Region(cells, List.copyOf(faces)); stage = DONE; return; }
            long cell = queue.dequeueLong();
            for (long neighbor : access.neighbors(cell)) {
                Kind kind = kindOf(neighbor);
                if (kind == Kind.UNLOADED) { startPartial(); return; }
                if (kind != Kind.INTERIOR) faces.add(new Face(cell, neighbor));
                else if (cells.add(neighbor)) {
                    if (cells.size() > limit) { startPartial(); return; }
                    queue.enqueue(neighbor);
                }
            }
        }
        private void startPartial() {
            if (owned == null || owned.test(seed)) { stage = DONE; return; }
            cells = new LongOpenHashSet();
            faces = new ArrayList<>();
            queue.clear();
            cells.add(seed); queue.enqueue(seed);
            stage = PARTIAL;
        }
        private void stepPartial() {
            if (queue.isEmpty() || cells.size() >= limit) { faceCells = cells.iterator(); stage = FACES; return; }
            long cell = queue.dequeueLong();
            for (long neighbor : access.neighbors(cell)) {
                Kind kind = kindOf(neighbor);
                if (kind == Kind.UNLOADED) { stage = DONE; return; }
                if (kind == Kind.INTERIOR && !owned.test(neighbor) && cells.size() < limit && cells.add(neighbor))
                    queue.enqueue(neighbor);
            }
        }
        private void stepFaces() {
            if (!faceCells.hasNext()) { region = new Region(cells, List.copyOf(faces)); stage = DONE; return; }
            long cell = faceCells.nextLong();
            for (long neighbor : access.neighbors(cell)) {
                if (cells.contains(neighbor)) continue;
                Kind kind = kindOf(neighbor);
                if (kind == Kind.UNLOADED) { stage = DONE; return; }
                faces.add(new Face(cell, neighbor));
                if (kind == Kind.INTERIOR && !owned.test(neighbor)) frontier.add(neighbor);
            }
        }
    }
}
