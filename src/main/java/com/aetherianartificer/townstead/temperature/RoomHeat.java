package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.compat.temperature.*;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.*;

/** Loaded, connected air regions with cached geometry and a shared, persistent heat budget. */
public final class RoomHeat {
    private static final Map<ServerLevel, World> WORLDS = new WeakHashMap<>();
    private static final int MAX_CELLS = 4096, MAX_ROOMS = 64;
    private static final Direction[] DIRECTIONS = Direction.values();
    /** Slots discovery leaves free, so a queried room never evicts a room discovery would refill next step. */
    private static final int DISCOVERY_HEADROOM = 8;
    /** A failed scan waits this long before retrying; a new barrier clears waits early. */
    private static final long MISS_BACKOFF = 600;
    /** Room scans and wall reads a tick may spend together, and the least wall reads get when any are due. */
    private static final long THERMAL_NANOS_PER_TICK = 1_000_000, SHELL_NANOS_MIN = 100_000;
    /** Edits in a burst (a door's open and close, a blast) become one reread of the room's walls. */
    private static final long SHELL_COALESCE_TICKS = 10;
    /** Picks up exterior changes no watched block reports, like a roof raised over a ray's end. */
    private static final long SHELL_CHECK_TICKS = 1200;
    /** Rereads whole walls, for writes that skip block updates. */
    private static final long SHELL_REREAD_TICKS = 12000;
    private static final int SHELL_FACES_PER_CHECK = 64;
    /** Scan work a tick may spend once a space turns out larger than {@link #SCAN_CELLS_AT_ONCE}. */
    private static final long SCAN_NANOS_PER_TICK = 500_000;
    private static final int SCAN_CELLS_AT_ONCE = 256, SCAN_CELLS_PER_CHECK = 64;
    private RoomHeat() {}
    private static final class Region {
        ThermalRegionScan.Region shape;
        long anchor, lastTick, lastUsed;
        double temperature;
        double power, draft, conductance, reservoir, capacity;
        double coolingConductance, coolingPotential;
        /** Mean rock beyond the buried faces, weighted by how well each conducts. NaN when none is buried or until first sampled. */
        double ground = Double.NaN;
        /** Rock beyond each buried lump of {@link #rockShell}, by lump index, and beyond buried faces with no wall. */
        double[] rock;
        double directRock = Double.NaN;
        ThermalShell rockShell;
        /** A new space with no saved heat: its air and walls start at the rock around it once that is read. */
        boolean fresh;
        /** The room's air and the sky over it, kept between steps: a temperature mod's reading can cost milliseconds. NaN until first sampled. */
        double air = Double.NaN, sky = Double.NaN;
        long airSampledAt;
        long groundSampledAt;
        double wallCapacity, wallTemperature, airToWalls, externalLoss;
        int unresolved;
        List<String> sourceDetails = new ArrayList<>();
        List<String> controls = new ArrayList<>();
        boolean dirty, solved;
        /** Found only by neighbor discovery, never read. Its own neighbors are not discovered, so discovery cannot chain into caves. */
        boolean discovered;
        Map<Long, Integer> sourceCells;
        int sourceGeneration;
        Map<BlockPos, Float> localSources = new HashMap<>();
        /** What the scan read around the room, kept to tell which block changes alter its shape. */
        Long2ObjectOpenHashMap<ThermalRegionScan.Kind> boundaryKinds = new Long2ObjectOpenHashMap<>();
        /** One position per chunk the room or its faces touch. */
        final List<BlockPos> chunkSamples = new ArrayList<>();
        int minX, maxX, minZ, maxZ;
        ThermalShell shell;
        long shellCheckedAt;
        /** A wall read still under way; it takes over from {@link #shell} when complete. */
        ThermalShell pendingShell;
        TemperatureSettings shellSettings;
        boolean shellDirty;
        /** Wall temperature inherited from the rooms this one replaced. */
        double wallFallback = Double.NaN;
        /** Air of the same space beyond a partial room's edge, open to the outside air. */
        Set<Long> frontier = Set.of();
        int minY, maxY, footprint;
        /** Ceiling minus floor air, from where its sources sit: warm sources low warm the top. */
        double stratification;
        /** One water block in each heated pool in the room, null until looked for; their water, the water they give the air in kg/s, and what that adds to its vapour in hPa. */
        long[] poolSeeds;
        boolean poolsPending;
        int poolVolume;
        double poolVapour, addedVapour;
        Region(ThermalRegionScan.Region shape, long now) {
            this.shape = shape;
            this.lastTick = this.lastUsed = now;
            anchor = Long.MAX_VALUE;
            minX = minY = minZ = Integer.MAX_VALUE; maxX = maxY = maxZ = Integer.MIN_VALUE;
            var seenChunks = new LongOpenHashSet();
            var cells = shape.cells().iterator();
            while (cells.hasNext()) {
                long cell = cells.nextLong();
                anchor = Math.min(anchor, cell);
                minY = Math.min(minY, BlockPos.getY(cell)); maxY = Math.max(maxY, BlockPos.getY(cell));
                track(cell, seenChunks);
            }
            for (var face : shape.faces()) track(face.outside(), seenChunks);
            this.footprint = Math.max(1, shape.cells().size() / (maxY - minY + 1));
        }
        private void track(long cell, LongOpenHashSet seenChunks) {
            int x = BlockPos.getX(cell), z = BlockPos.getZ(cell);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            if (seenChunks.add(net.minecraft.world.level.ChunkPos.asLong(x >> 4, z >> 4))) chunkSamples.add(new BlockPos(x, minY, z));
        }
        boolean holds(long cell, int x, int y, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY && shape.cells().contains(cell);
        }
        boolean overlaps(Region other, int reach) {
            return other.minX <= maxX + reach && other.maxX >= minX - reach && other.minZ <= maxZ + reach && other.maxZ >= minZ - reach
                    && other.minY <= maxY + reach && other.maxY >= minY - reach;
        }
        /** Whether a block here could be in the room, on its boundary or on one of its rays. */
        boolean near(int x, int y, int z) {
            int reach = ThermalBoundary.MAX_DEPTH + 1;
            return x >= minX - reach && x <= maxX + reach && z >= minZ - reach && z <= maxZ + reach
                    && y >= minY - reach - 1 && y <= maxY + reach + 1;
        }
    }
    private static final class RoomScan {
        final ThermalRegionScan.Job job;
        /** Cells the scan read that may affect a room; it reads every cell a source could be in. */
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet sources;
        boolean query;
        RoomScan(ThermalRegionScan.Job job, it.unimi.dsi.fastutil.longs.LongOpenHashSet sources, boolean query) {
            this.job = job; this.sources = sources; this.query = query;
        }
    }
    private static final class World {
        Set<Region> regions = new LinkedHashSet<>();
        Map<Long, Long> misses = new HashMap<>();
        final Long2DoubleOpenHashMap residual = new Long2DoubleOpenHashMap();
        RoomScan scan;
        /** Reads that arrived while another scan ran, scanned in turn. */
        final LongLinkedOpenHashSet scanQueue = new LongLinkedOpenHashSet();
        long scanTick = Long.MIN_VALUE;
        int scans;
        long lastStep = Long.MIN_VALUE;
        double energyError, supplied, escaped;
        boolean valid;
        String status = "Awaiting thermal update";
        private Region lastOwner;
        /** The room that holds this cell. Rooms are few and reads repeat, so this checks the last answer, then each room's bounds. */
        Region owner(long cell) {
            int x = BlockPos.getX(cell), y = BlockPos.getY(cell), z = BlockPos.getZ(cell);
            if (lastOwner != null && lastOwner.holds(cell, x, y, z)) return lastOwner;
            for (Region r : regions) {
                if (r.holds(cell, x, y, z)) { lastOwner = r; return r; }
            }
            return null;
        }
        void remove(Region r) {
            regions.remove(r);
            if (lastOwner == r) lastOwner = null;
            for (Region other : regions)
                if (other.pendingShell != null || other.shell != null && other.shell.neighbors.contains(r.anchor)) other.shellDirty = true;
        }
        void add(Region r) {
            regions.add(r);
            int reach = ThermalBoundary.MAX_DEPTH + 1;
            for (Region other : regions) {
                if (other == r) continue;
                // A read under way may already have passed this room's cells.
                if (other.pendingShell != null) other.shellDirty = true;
                if (other.shell == null || !other.overlaps(r, reach)) continue;
                var ends = other.shell.openEnds.iterator();
                while (ends.hasNext()) {
                    long end = ends.nextLong();
                    if (r.holds(end, BlockPos.getX(end), BlockPos.getY(end), BlockPos.getZ(end))) { other.shellDirty = true; break; }
                }
            }
        }
    }

    /** A pool of heated water: its lowest block, temperature, size, heat and water it gives the air, and its heaters. */
    public record Pool(long anchor, double celsius, int volume, double airWatts, double vapourKgPerSecond, long[] heaters) {}

    /** Where heated pools come from: Bit of a Mess, when it is installed. */
    public interface PoolSource {
        java.util.Optional<Pool> at(ServerLevel level, BlockPos pos);
    }

    private static volatile PoolSource pools;
    /** Water blocks a room asks after in one step while it looks for its pools. */
    private static final int POOL_ASKS = 16;

    /** Gives rooms their heated pools. */
    public static void setPools(PoolSource source) {
        pools = source;
    }

    /** A room's air here if one is already known, without scanning for one. For readers a room step may itself call. */
    public static OptionalDouble cachedAirAt(ServerLevel level, BlockPos pos) {
        World world = WORLDS.get(level);
        Region region = world == null ? null : world.owner(pos.asLong());
        return region == null || region.dirty ? OptionalDouble.empty() : OptionalDouble.of(air(region, pos, 0.5));
    }

    /** Water vapour heated pools add to the room here, in hPa; 0 outside a known room. */
    public static double addedVapour(ServerLevel level, BlockPos pos) {
        World world = WORLDS.get(level);
        Region region = world == null ? null : world.owner(pos.asLong());
        return region == null ? 0 : region.addedVapour;
    }

    public static OptionalDouble at(ServerLevel level, BlockPos position) {
        return sample(level, position, false, true);
    }

    public static OptionalDouble airAt(ServerLevel level, BlockPos position) {
        return sample(level, position, true, true);
    }

    /** Control requires a fresh successful solve, rather than stale saved air after a failed update. */
    public static OptionalDouble controlAirAt(ServerLevel level, BlockPos position) {
        OptionalDouble air = airAt(level,position);
        World world = WORLDS.get(level);
        Region region = world == null ? null : world.owner(position.asLong());
        return air.isPresent() && world.valid && region != null && region.solved && !region.dirty
                && level.getGameTime()-region.lastTick <= 60 ? air : OptionalDouble.empty();
    }

    /** {@code query} is a read from outside RoomHeat; discovery and dirty rescans are not. */
    private static OptionalDouble sample(ServerLevel level, BlockPos position, boolean airOnly, boolean query) {
        if (!TemperatureSettings.get().roomHeatEnabled() || !com.aetherianartificer.townstead.TownsteadConfig.temperatureActive()
                || RoomHeatBackend.bypassed() || !level.getServer().isSameThread()) return OptionalDouble.empty();
        World world = WORLDS.computeIfAbsent(level, key -> new World());
        BlockPos pos = position;
        // Sample the exposed side of a mounted sensor, never an arbitrary room across its wall.
        if (kind(level, pos) == ThermalRegionScan.Kind.BARRIER) {
            BlockState sensor = level.getBlockState(position);
            if (sensor.getBlock() instanceof com.aetherianartificer.townstead.block.RoomThermometerBlock
                    || sensor.getBlock() instanceof com.aetherianartificer.townstead.block.RoomThermostatBlock) {
                pos = position.relative(sensor.getValue(com.aetherianartificer.townstead.block.RoomThermometerBlock.FACING));
                if (kind(level, pos) != ThermalRegionScan.Kind.INTERIOR) return OptionalDouble.empty();
            } else {
                pos = null;
                // A sensor of another mod (Cold Sweat's thermolith, say) reads the side it faces first.
                Direction front = facing(sensor);
                if (front != null && kind(level, position.relative(front)) == ThermalRegionScan.Kind.INTERIOR) pos = position.relative(front);
                if (pos == null) for (Direction d : Direction.values()) {
                    BlockPos candidate = position.relative(d);
                    if (kind(level, candidate) == ThermalRegionScan.Kind.INTERIOR) { pos = candidate; break; }
                }
                if (pos == null) return OptionalDouble.empty();
            }
        }
        long key = pos.asLong(), now = level.getGameTime();
        Region old = world.owner(key);
        if (old != null && !old.dirty) {
            old.lastUsed = now;
            // A read room's neighbors join the network, so its next wall read looks for them.
            if (query && old.discovered) { old.discovered = false; old.shellDirty = true; }
            return OptionalDouble.of(airOnly ? air(old, pos, 0.5) : experienced(level, old, position));
        }
        if (world.misses.getOrDefault(key, 0L) > now) return OptionalDouble.empty();
        if (world.scan != null) {
            // Only one space is scanned at a time; a read of the one under way marks it as read.
            if (world.scan.job.seed == key && query) world.scan.query = true;
            else if (query) queueScan(world, key);
            return OptionalDouble.empty();
        }
        if (world.scanTick != now) { world.scanTick = now; world.scans = 0; }
        if (world.scans++ >= 1) {
            if (query) queueScan(world, key);
            return OptionalDouble.empty();
        }
        var mutable = new BlockPos.MutableBlockPos();
        var sources = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        ThermalRegionScan.Access access = new ThermalRegionScan.Access() {
            public ThermalRegionScan.Kind kind(long p) {
                var found = RoomHeat.kind(level, mutable.set(p));
                if (found != ThermalRegionScan.Kind.UNLOADED && ThermalBlocks.mayAffectRoom(level.getBlockState(mutable))) sources.add(p);
                return found;
            }
            public long[] neighbors(long p) {
                long[] out = new long[DIRECTIONS.length];
                for (int i = 0; i < out.length; i++) out[i] = BlockPos.offset(p, DIRECTIONS[i]);
                return out;
            }
        };
        Region owner = old;
        var scan = new RoomScan(new ThermalRegionScan.Job(key, MAX_CELLS, access, cell -> {
            Region other = world.owner(cell);
            return other != null && other != owner && !other.dirty;
        }), sources, query);
        // Most rooms fit in this slice and answer at once; a large space answers when its scan ends.
        if (!scan.job.advance(SCAN_CELLS_AT_ONCE)) { world.scan = scan; return OptionalDouble.empty(); }
        Region region = finishScan(level, world, scan);
        if (region == null) return OptionalDouble.empty();
        return OptionalDouble.of(airOnly ? air(region, pos, 0.5) : experienced(level, region, position));
    }

    private static void queueScan(World world, long cell) {
        if (world.scanQueue.size() < MAX_ROOMS * 2) world.scanQueue.add(cell);
    }

    /** Continues the scan under way, or starts the next one waiting, within this tick's share. */
    private static void continueScan(ServerLevel level, World world, long tickStart) {
        if (world.scan == null && !world.scanQueue.isEmpty())
            sample(level, BlockPos.of(world.scanQueue.removeFirstLong()), true, true);
        RoomScan scan = world.scan;
        if (scan == null) return;
        long deadline = tickStart + SCAN_NANOS_PER_TICK;
        while (!scan.job.advance(SCAN_CELLS_PER_CHECK)) {
            if (System.nanoTime() > deadline) return;
        }
        world.scan = null;
        finishScan(level, world, scan);
    }

    /** Turns a finished scan into a room, or records the miss. Null when no room came of it. */
    private static Region finishScan(ServerLevel level, World world, RoomScan scan) {
        long key = scan.job.seed, now = level.getGameTime();
        boolean query = scan.query;
        Region old = world.owner(key);
        var found = scan.job.region();
        if (found.isEmpty()) {
            if (old != null) world.remove(old);
            if (world.misses.size() >= 2048) world.misses.clear();
            world.misses.put(key, now + MISS_BACKOFF);
            return null;
        }
        Set<Long> frontier = scan.job.frontier();
        var shape = found.get();
        Region region = new Region(shape, now);
        // Volume-weighted overlap preserves stored heat when regions merge or split.
        double heat = 0; int overlap = 0;
        Set<Region> replaced = new HashSet<>();
        for (Region other : world.regions) {
            if (!other.overlaps(region, 0)) continue;
            var cells = other.shape.cells().iterator();
            while (cells.hasNext()) {
                if (!shape.cells().contains(cells.nextLong())) continue;
                heat += other.temperature; overlap++; replaced.add(other);
            }
        }
        if (!world.residual.isEmpty()) {
            var cells = shape.cells().iterator();
            while (cells.hasNext()) {
                long cell = cells.nextLong();
                if (world.residual.containsKey(cell)) { heat += world.residual.remove(cell); overlap++; }
            }
        }
        double ambient = roomAir(level, region);
        double saved = overlap == 0 ? RoomHeatData.get(level).temperature(region.anchor, Double.NaN) : Double.NaN;
        region.fresh = overlap == 0 && Double.isNaN(saved);
        region.temperature = overlap == 0 ? (region.fresh ? ambient : saved)
                : (heat + ambient * (shape.cells().size() - overlap)) / shape.cells().size();
        // Carry unvisited portions of split rooms forward without retaining stale topology.
        for (Region r : replaced) {
            if (world.residual.size() > MAX_CELLS * 2) world.residual.clear();
            var cells = r.shape.cells().iterator();
            while (cells.hasNext()) {
                long cell = cells.nextLong();
                if (!shape.cells().contains(cell)) world.residual.put(cell, r.temperature);
            }
            world.remove(r);
        }
        // At the room cap, the room read least recently gives way.
        while (world.regions.size() >= MAX_ROOMS) {
            Region stalest = world.regions.stream().min(Comparator.comparingLong(r -> r.lastUsed)).orElse(null);
            if (stalest == null || now - stalest.lastUsed < 20) return null;
            if (world.residual.size() > MAX_CELLS * 2) world.residual.clear();
            var cells = stalest.shape.cells().iterator();
            while (cells.hasNext()) world.residual.put(cells.nextLong(), stalest.temperature);
            world.remove(stalest);
        }
        region.wallFallback = replaced.stream().filter(r -> r.shell != null).mapToDouble(r -> r.shell.meanTemperature(r.temperature)).average().orElse(Double.NaN);
        region.frontier = frontier;
        region.discovered = !query && replaced.stream().allMatch(r -> r.discovered);
        region.boundaryKinds = scan.job.kinds();
        // The scan read every cell a source could be in; a wall's share of its heat follows from its faces on the room.
        region.sourceCells = new TreeMap<>();
        region.sourceGeneration = ThermalBlocks.generation();
        var hits = scan.sources.iterator();
        while (hits.hasNext()) {
            long hit = hits.nextLong();
            int weight = sourceWeight(region, hit);
            if (weight > 0) region.sourceCells.put(hit, weight);
        }
        // Rebuilding geometry must not restart the thermal clock during cooking/block updates.
        if (!replaced.isEmpty()) region.lastTick = replaced.stream().mapToLong(r -> r.lastTick).min().orElse(now);
        world.add(region);
        return region;
    }

    /** How much of a source's heat is the room's: all of it inside, a share per face on a wall. Zero if the cell is neither. */
    private static int sourceWeight(Region region, long cell) {
        if (region.shape.cells().contains(cell)) return 6;
        int faces = 0;
        for (Direction direction : DIRECTIONS) if (region.shape.cells().contains(BlockPos.offset(cell, direction))) faces++;
        return faces;
    }

    /** Air at a height in the room: its mean plus the vertical profile, {@code offset} blocks above the cell's floor. */
    private static double air(Region room, BlockPos pos, double offset) {
        return room.temperature + RoomHeatBalance.layerOffset(room.stratification, pos.getY() + offset - room.minY,
                room.maxY - room.minY + 1);
    }

    private static Direction facing(BlockState state) {
        for (var property : state.getProperties()) {
            if (property instanceof net.minecraft.world.level.block.state.properties.DirectionProperty direction
                    && property.getName().equals("facing")) return state.getValue(direction);
        }
        return null;
    }

    private static ThermalRegionScan.Kind kind(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) return ThermalRegionScan.Kind.UNLOADED;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || !state.getCollisionShape(level, pos).isEmpty()) return ThermalRegionScan.Kind.BARRIER;
        // Light visibility treats glass roofs as outdoors; collision height preserves greenhouses.
        if (pos.getY() >= level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                pos.getX(), pos.getZ())) return ThermalRegionScan.Kind.EXTERIOR;
        return ThermalRegionScan.Kind.INTERIOR;
    }

    /** What a shell remembers of a cell: its wall class, or which kind of space it is. */
    private static int cellCode(ServerLevel level, BlockPos pos) {
        return switch (kind(level, pos)) {
            case BARRIER -> ThermalMaterials.signature(level.getBlockState(pos));
            case INTERIOR -> ThermalShell.INTERIOR;
            case EXTERIOR -> ThermalShell.EXTERIOR;
            case UNLOADED -> ThermalShell.UNLOADED;
        };
    }

    /**
     * Runs on every server block change, so it returns early wherever no room is near.
     * Only the rooms whose air or rays hold this block reread anything.
     */
    public static void changed(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getServer().isSameThread()) return;
        World world = WORLDS.get(level);
        if (world == null) return;
        // Only a new barrier can enclose space that was open; clearing opens nothing new.
        if (!world.misses.isEmpty() && (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || !state.getCollisionShape(level, pos).isEmpty())) world.misses.clear();
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        long key = pos.asLong();
        if (world.scan != null) {
            var seen = world.scan.job.seen(key);
            if (seen != null && seen != kind(level, pos)) world.scan = null;
        }
        ThermalRegionScan.Kind current = null;
        int code = 0;
        for (Region region : world.regions) {
            if (!region.near(x, y, z)) continue;
            if (current == null) {
                current = kind(level, pos);
                code = cellCode(level, pos);
            }
            ThermalRegionScan.Kind previous = region.shape.cells().contains(key)
                    ? ThermalRegionScan.Kind.INTERIOR : region.boundaryKinds.get(key);
            if (previous != null) {
                if (previous != current) region.dirty = true;
                // Lit/open flags and material swaps change heat flow, not the air region.
                if (region.sourceCells != null) {
                    int weight = ThermalBlocks.mayAffectRoom(state) ? sourceWeight(region, key) : 0;
                    if (weight > 0) region.sourceCells.put(key, weight);
                    else region.sourceCells.remove(key);
                }
            }
            if (region.shell != null && region.shell.changedBy(key, code)
                    || region.pendingShell != null && region.pendingShell.changedBy(key, code)) region.shellDirty = true;
        }
    }

    public static void tick(MinecraftServer server) {
        VillagerDoorCleanup.tick(server);
        if (!TemperatureSettings.get().roomHeatEnabled() || !com.aetherianartificer.townstead.TownsteadConfig.temperatureActive()) return;
        for (ServerLevel level : server.getAllLevels()) {
            World world = WORLDS.get(level);
            if (world == null) continue;
            long now = level.getGameTime();
            boolean step = world.lastStep == Long.MIN_VALUE || now - world.lastStep >= 20;
            if (step) for (Region r : new ArrayList<>(world.regions)) {
                for (BlockPos sample : r.chunkSamples) {
                    if (!level.isLoaded(sample)) { world.remove(r); break; }
                }
            }
            if (world.regions.stream().anyMatch(r -> r.dirty))
                for (Region r : new ArrayList<>(world.regions)) if (r.dirty) sample(level, BlockPos.of(r.anchor), false, false);
            long tickStart = System.nanoTime();
            continueScan(level, world, tickStart);
            readShells(level, world, now, tickStart);
            if (!step) continue;
            double seconds = Math.min(5, world.lastStep == Long.MIN_VALUE ? 1 : Math.max(0, now-world.lastStep)/20.0)
                    * Math.min(100, TemperatureSettings.get().thermalTimeScale());
            advanceNetwork(level, world, Math.min(500, seconds));
            // Discover connected interiors whether or not a player or villager queried them.
            // sample() bounds scans per tick. Until resolved, an interior is insulated, not outdoors.
            discoverNeighbors(level, world, now);
            world.lastStep = now;
        }
    }

    /** Rereads walls that went stale, a bounded amount per tick, most urgent first: no step ever reads a block for a wall. */
    private static void readShells(ServerLevel level, World world, long now, long tickStart) {
        var settings = TemperatureSettings.get();
        long deadline = tickStart + THERMAL_NANOS_PER_TICK;
        boolean started = false;
        for (int pass = 0; pass < 2; pass++) {
            for (Region r : world.regions) {
                if (r.dirty) { r.pendingShell = null; continue; }
                boolean check = pass == 1 && r.pendingShell == null && now - r.shellCheckedAt >= SHELL_CHECK_TICKS;
                boolean due = r.pendingShell != null || (pass == 0
                        ? r.shell == null || r.shellSettings != settings || r.shellDirty && now - r.shell.builtAt >= SHELL_COALESCE_TICKS
                        : now - r.shell.builtAt >= SHELL_REREAD_TICKS);
                if (!due && !check) continue;
                // The first read of a tick always runs a little, so a busy scan cannot starve the walls.
                if (!started) { started = true; deadline = Math.max(deadline, System.nanoTime() + SHELL_NANOS_MIN); }
                else if (System.nanoTime() > deadline) return;
                if (!due) {
                    r.shellCheckedAt = now;
                    if (r.shell.spacesChanged(shellCells(level, world))) r.shellDirty = true;
                    continue;
                }
                if (r.pendingShell == null) beginShell(level, world, r, settings);
                while (!r.pendingShell.advance(SHELL_FACES_PER_CHECK)) {
                    if (System.nanoTime() > deadline) return;
                }
                finishShell(level, r, settings, now);
            }
        }
    }

    private static ThermalShell.Cells shellCells(ServerLevel level, World world) {
        var mutable = new BlockPos.MutableBlockPos();
        return new ThermalShell.Cells() {
            @Override public int read(long cell) { return cellCode(level, mutable.set(cell)); }
            @Override public long beyond(long from, long to) {
                return BlockPos.asLong(2*BlockPos.getX(to)-BlockPos.getX(from), 2*BlockPos.getY(to)-BlockPos.getY(from), 2*BlockPos.getZ(to)-BlockPos.getZ(from));
            }
            @Override public long roomAt(long cell) {
                Region owner = world.owner(cell);
                return owner == null ? ThermalShell.NO_ROOM : owner.anchor;
            }
            @Override public int x(long cell) { return BlockPos.getX(cell); }
            @Override public int y(long cell) { return BlockPos.getY(cell); }
            @Override public int z(long cell) { return BlockPos.getZ(cell); }
        };
    }

    private static void beginShell(ServerLevel level, World world, Region r, TemperatureSettings settings) {
        r.pendingShell = ThermalShell.begin(r.anchor, r.shape.faces(), r.frontier, !r.discovered, shellCells(level, world), ThermalMaterials.table(settings));
        r.shellDirty = false;
    }

    private static void finishShell(ServerLevel level, Region r, TemperatureSettings settings, long now) {
        ThermalShell previous = r.shell, shell = r.pendingShell;
        shell.builtAt = r.shellCheckedAt = now;
        // A wall keeps its heat across rereads; a wall new to the room starts where the room's others are.
        var stored = RoomHeatData.get(level);
        double fallback = previous != null ? previous.meanTemperature(Double.NaN) : r.wallFallback;
        if (!Double.isFinite(fallback)) fallback = roomAir(level, r);
        for (int i = 0; i < shell.groups; i++) {
            double known = previous != null ? previous.temperatureOf(shell.key[i]) : Double.NaN;
            shell.temperature[i] = Double.isFinite(known) ? known : stored.groupTemperature(r.anchor, shell.key[i], fallback);
        }
        r.shell = shell;
        r.pendingShell = null;
        r.shellSettings = settings;
    }

    private static BlockPos roomMiddle(Region r) {
        return new BlockPos(BlockPos.getX(r.anchor), (r.minY + r.maxY) / 2, BlockPos.getZ(r.anchor));
    }

    /** Whether natural terrain lies over the room, so its air comes from the ground rather than the sky. */
    private static boolean covered(ServerLevel level, Region r) {
        return GroundClimate.probe(level, roomMiddle(r)).depth() > 0;
    }

    /** The air a room exchanges with and rests toward: under terrain the rock around it, otherwise the open air above it. */
    private static double roomAir(ServerLevel level, Region r) {
        if (!covered(level, r)) return outside(level, r.anchor, r.maxY);
        return Double.isFinite(r.ground) ? r.ground : RoomHeatBackend.outdoor(level, roomMiddle(r));
    }

    private static double outside(ServerLevel level, long anchorCell, int top) {
        BlockPos anchor = BlockPos.of(anchorCell);
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, anchor.getX(), anchor.getZ());
        return RoomHeatBackend.outdoor(level, new BlockPos(anchor.getX(), Math.min(level.getMaxBuildHeight()-1, Math.max(surface, top + 2)), anchor.getZ()));
    }

    private static final long GROUND_RESAMPLE_TICKS = 1200, AIR_RESAMPLE_TICKS = 100;
    /** Readings a step may refresh, so rooms take turns rather than all asking a temperature mod at once. A new room always gets its first. */
    private static final int READINGS_PER_STEP = 4;

    private static void refreshAir(ServerLevel level, Region r, long now) {
        boolean covered = covered(level, r);
        r.air = roomAir(level, r);
        // Only faces that meet open sky feel the sky.
        r.sky = covered && r.shell.reachesSky() ? outside(level, r.anchor, r.maxY) : r.air;
        r.airSampledAt = now;
    }

    /**
     * Rock beyond each buried lump, where its rays end, so lava or a cave biome on one side warms or
     * chills that side. A temperature mod is read once, at the lumps' mean end; Townstead's ground model
     * gives the differences from there.
     */
    private static void refreshRock(ServerLevel level, Region r, long now) {
        ThermalShell s = r.shell;
        double[] flow = new double[s.groups];
        double weight = s.directGround, x = weight * s.directGroundX, y = weight * s.directGroundY, z = weight * s.directGroundZ;
        for (int i = 0; i < s.groups; i++) {
            if (s.endpoint[i] != ThermalShell.TO_GROUND) continue;
            flow[i] = s.toRoom[i] * s.toEnd[i] / Math.max(1e-12, s.toRoom[i] + s.toEnd[i]);
            weight += flow[i]; x += flow[i] * s.endX[i]; y += flow[i] * s.endY[i]; z += flow[i] * s.endZ[i];
        }
        r.rock = new double[s.groups];
        r.rockShell = s;
        r.groundSampledAt = now;
        if (weight <= 0) { r.ground = r.directRock = Double.NaN; r.fresh = false; return; }
        BlockPos mean = BlockPos.containing(x / weight, y / weight, z / weight);
        double base = RoomHeatBackend.outdoor(level, mean) - BuiltinTemperatureBridge.climateCelsius(level, mean);
        double sum = 0;
        for (int i = 0; i < s.groups; i++) {
            if (flow[i] <= 0) continue;
            r.rock[i] = base + BuiltinTemperatureBridge.climateCelsius(level, BlockPos.containing(s.endX[i], s.endY[i], s.endZ[i]));
            sum += flow[i] * r.rock[i];
        }
        r.directRock = s.directGround > 0
                ? base + BuiltinTemperatureBridge.climateCelsius(level, BlockPos.containing(s.directGroundX, s.directGroundY, s.directGroundZ))
                : Double.NaN;
        if (s.directGround > 0) sum += s.directGround * r.directRock;
        r.ground = sum / weight;
        if (r.fresh && covered(level, r)) {
            r.temperature = r.ground;
            for (int i = 0; i < s.groups; i++) s.temperature[i] = flow[i] > 0 ? r.rock[i] : r.ground;
        }
        r.fresh = false;
    }

    /** The shells' rays already found the interiors no room claims; scan those, not every ray again. */
    private static void discoverNeighbors(ServerLevel level, World world, long now) {
        for (Region r : new ArrayList<>(world.regions)) {
            if (r.shell == null) continue;
            var cells = r.shell.unclaimed.iterator();
            while (cells.hasNext()) {
                if (world.regions.size() >= MAX_ROOMS - DISCOVERY_HEADROOM || world.scan != null
                        || world.scanTick == level.getGameTime() && world.scans >= 1) return;
                long cell = cells.nextLong();
                if (world.owner(cell) == null && world.misses.getOrDefault(cell, 0L) <= now) sample(level, BlockPos.of(cell), true, false);
            }
        }
    }

    private static void exposeTo(List<ThermalNetwork.Node> nodes, int node, double g, double reservoir) {
        var old = nodes.get(node);
        double total = old.outsideConductance()+g;
        nodes.set(node, new ThermalNetwork.Node(old.capacity(),old.temperature(),old.power(),total,
                (old.outsideConductance()*old.outside()+g*reservoir)/total));
    }

    /** Both rooms hold the rays of a wall between them; the lower anchor's copy is the one in the network. */
    private static boolean carries(Region room, Region other) {
        return room == other || room.anchor < other.anchor || !other.shell.neighbors.contains(room.anchor);
    }

    private static void advanceNetwork(ServerLevel level, World world, double seconds) {
        var settings = TemperatureSettings.get();
        var stored = RoomHeatData.get(level);
        stored.dropLegacyWalls();
        world.valid = false;
        List<Region> rooms = new ArrayList<>();
        var roomIndex = new Long2IntOpenHashMap();
        roomIndex.defaultReturnValue(-1);
        for (Region r : world.regions) {
            if (r.dirty || r.shell == null) continue;
            roomIndex.put(r.anchor, rooms.size());
            rooms.add(r);
        }
        var states = new HashMap<Long,BlockState>();
        double[] ambients = new double[rooms.size()];
        long now = level.getGameTime();
        int readings = READINGS_PER_STEP;
        List<ThermalNetwork.Node> nodes = new ArrayList<>(rooms.size() * 8);
        for (int room = 0; room < rooms.size(); room++) {
            Region r = rooms.get(room);
            // Rock first: a covered room's air comes from it. A new wall read always gets its rock.
            boolean newShell = r.rockShell != r.shell;
            if (newShell || readings > 0 && now - r.groundSampledAt >= GROUND_RESAMPLE_TICKS) {
                if (!newShell) readings--;
                refreshRock(level, r, now);
            }
            if (Double.isNaN(r.air)) refreshAir(level, r, now);
            else if (readings > 0 && now - r.airSampledAt >= AIR_RESAMPLE_TICKS) { refreshAir(level, r, now); readings--; }
            double ambient = r.air;
            ambients[room] = ambient;
            r.reservoir = ambient; r.unresolved = r.shell.unresolved;
            r.power = sourcePower(level,r,states);
            // Pool water is in the room's cells, but it is the pool's to heat, not air.
            r.capacity = Math.max(1, r.shape.cells().size()-r.poolVolume)*settings.roomHeatCapacity();
            double ventilation = RoomHeatBalance.ventilation(r.shape.cells().size(),settings.airChangesPerHour())+r.draft;
            r.addedVapour = RoomHeatBalance.addedVapour(r.poolVapour, ventilation, r.temperature);
            double boundary = ventilation + r.coolingConductance;
            double reservoir = boundary > 0 ? (ventilation*ambient+r.coolingPotential)/boundary : ambient;
            nodes.add(new ThermalNetwork.Node(r.capacity,r.temperature,r.power,boundary,reservoir));
        }
        List<ThermalNetwork.Link> links = new ArrayList<>(rooms.size() * 8);
        List<Region> groupRooms = new ArrayList<>();
        List<Integer> groupSlots = new ArrayList<>();
        for (int room = 0; room < rooms.size(); room++) {
            Region r = rooms.get(room);
            ThermalShell shell = r.shell;
            double sky = r.sky;
            if (shell.directOutside > 0) exposeTo(nodes, room, shell.directOutside, sky);
            if (shell.directSpace > 0) exposeTo(nodes, room, shell.directSpace, ambients[room]);
            if (shell.directGround > 0) exposeTo(nodes, room, shell.directGround, r.directRock);
            for (var direct : shell.directRooms.long2DoubleEntrySet()) {
                int other = roomIndex.get(direct.getLongKey());
                if (other >= 0 && other != room && carries(r, rooms.get(other)))
                    links.add(new ThermalNetwork.Link(room, other, direct.getDoubleValue()));
            }
            for (int i = 0; i < shell.groups; i++) {
                int other = shell.endpoint[i] == ThermalShell.TO_ROOM ? roomIndex.get(shell.endpointRoom[i]) : -1;
                if (other >= 0 && !carries(r, rooms.get(other))) continue;
                double g = other < 0 && shell.endpoint[i] != ThermalShell.TO_ROOM && shell.endpoint[i] != ThermalShell.TO_NOTHING ? shell.toEnd[i] : 0;
                double reservoir = shell.endpoint[i] == ThermalShell.TO_GROUND ? r.rock[i]
                        : shell.endpoint[i] == ThermalShell.TO_OUTSIDE ? sky : ambients[room];
                int node = nodes.size();
                nodes.add(new ThermalNetwork.Node(shell.capacity[i], shell.temperature[i], 0, g, reservoir));
                links.add(new ThermalNetwork.Link(room, node, shell.toRoom[i]));
                if (other >= 0) links.add(new ThermalNetwork.Link(node, other, shell.toEnd[i]));
                groupRooms.add(r);
                groupSlots.add(i);
            }
        }
        ThermalNetwork.Result result;
        try { result = ThermalNetwork.advance(nodes,links,seconds); }
        catch (IllegalStateException invalid) {
            world.status = "Thermal solve deferred: " + invalid.getMessage();
            return;
        }
        double[] temperatures = result.temperatures();
        int roomCount = rooms.size();
        for (int i = 0; i < groupRooms.size(); i++) {
            Region r = groupRooms.get(i);
            int slot = groupSlots.get(i);
            r.shell.temperature[slot] = temperatures[roomCount + i];
            stored.putGroup(r.anchor, r.shell.key[slot], temperatures[roomCount + i]);
        }
        // One pass over the links, not one per room: rooms are the first nodes.
        double[] airToWalls = new double[roomCount], wallTemperature = new double[roomCount], wallCapacity = new double[roomCount];
        for (var link : links) {
            int a = link.a(), b = link.b();
            double g = link.conductance();
            if (a < roomCount) { airToWalls[a] += g*(temperatures[a]-temperatures[b]); wallTemperature[a] += g*temperatures[b]; wallCapacity[a] += g; }
            if (b < roomCount) { airToWalls[b] += g*(temperatures[b]-temperatures[a]); wallTemperature[b] += g*temperatures[a]; wallCapacity[b] += g; }
        }
        for (int index = 0; index < roomCount; index++) {
            Region r = rooms.get(index);
            r.temperature = temperatures[index];
            r.solved = true;
            r.conductance = nodes.get(index).outsideConductance();
            r.externalLoss = r.conductance*(r.temperature-nodes.get(index).outside());
            r.airToWalls = airToWalls[index];
            r.wallTemperature = wallTemperature[index];
            r.wallCapacity = wallCapacity[index];
            r.wallTemperature = r.wallCapacity > 0 ? r.wallTemperature/r.wallCapacity : r.temperature;
            stored.put(r.anchor,r.temperature);
            r.lastTick = level.getGameTime();
        }
        world.valid = true;
        world.energyError = result.errorJoules(); world.supplied = result.suppliedJoules(); world.escaped = result.escapedJoules();
        world.status = roomCount+" rooms / "+groupRooms.size()+" wall groups / "+links.size()+" links";
    }

    /**
     * One water block in each heated pool in this room. Asks block by block only until the pools found hold all the
     * room's water, so a room with one bath asks once, and at most {@link #POOL_ASKS} a step: the rest wait for the
     * next step, as do pools still being mapped.
     */
    private static void findPools(ServerLevel level, Region r) {
        r.poolsPending = false;
        PoolSource source = pools;
        if (source == null) { r.poolSeeds = new long[0]; return; }
        List<Long> water = new ArrayList<>();
        for (long cell : r.shape.cells())
            if (level.getFluidState(BlockPos.of(cell)).is(net.minecraft.tags.FluidTags.WATER)) water.add(cell);
        List<Long> seeds = new ArrayList<>();
        Set<Long> anchors = new HashSet<>();
        int found = 0, asked = 0;
        for (long cell : water) {
            if (found >= water.size()) break;
            if (asked++ >= POOL_ASKS) { r.poolsPending = true; break; }
            var pool = source.at(level, BlockPos.of(cell));
            if (pool.isEmpty()) continue;
            if (anchors.add(pool.get().anchor())) {
                seeds.add(cell);
                found += pool.get().volume();
            }
        }
        r.poolSeeds = seeds.stream().mapToLong(Long::longValue).toArray();
    }

    private static double sourcePower(ServerLevel level, Region r, Map<Long,BlockState> states) {
        var settings = TemperatureSettings.get();
        r.sourceDetails.clear();
        if (r.sourceCells == null || r.sourceGeneration != ThermalBlocks.generation()) {
            // Block changes in watched cells reset this, so the per-state filter stays exact.
            r.poolSeeds = null;
            r.sourceCells = new TreeMap<>();
            r.sourceGeneration = ThermalBlocks.generation();
            for (long cell : r.shape.cells())
                if (ThermalBlocks.mayAffectRoom(level.getBlockState(BlockPos.of(cell)))) r.sourceCells.put(cell,6);
            for (var face : r.shape.faces())
                if (ThermalBlocks.mayAffectRoom(level.getBlockState(BlockPos.of(face.outside())))) r.sourceCells.merge(face.outside(),1,Integer::sum);
        }
        double power = 0;
        // Heated pools: their surface warms the air and their steam dampens it. Their heaters are theirs, not the room's.
        Set<Long> poolHeaters = new HashSet<>();
        double vapour = 0;
        r.poolVolume = 0;
        PoolSource source = pools;
        if (r.poolSeeds == null || r.poolsPending) findPools(level, r);
        for (long seed : r.poolSeeds) {
            var pool = source == null ? java.util.Optional.<Pool>empty() : source.at(level, BlockPos.of(seed));
            if (pool.isEmpty()) { r.poolsPending = true; continue; }
            power += pool.get().airWatts();
            vapour += pool.get().vapourKgPerSecond();
            r.poolVolume += pool.get().volume();
            for (long heater : pool.get().heaters()) poolHeaters.add(heater);
            r.sourceDetails.add("heated pool at "+BlockPos.of(pool.get().anchor()).toShortString()+": "
                    +String.format(java.util.Locale.ROOT, "%.1f", pool.get().celsius())+" C, "+Math.round(pool.get().airWatts())+" W to the air");
        }
        r.poolVapour = vapour;
        r.draft = 0;
        r.coolingConductance = r.coolingPotential = 0;
        r.localSources.clear(); r.controls.clear();
        double warmDrive = 0, coldDrive = 0;
        int height = r.maxY - r.minY + 1;
        for (var entry : r.sourceCells.entrySet()) {
            if (poolHeaters.contains(entry.getKey())) continue;
            BlockPos pos = BlockPos.of(entry.getKey());
            BlockState state = states.computeIfAbsent(entry.getKey(), p -> level.getBlockState(pos));
            if (state.isAir()) continue;
            if (state.getBlock() instanceof com.aetherianartificer.townstead.block.RoomThermostatBlock) {
                r.controls.add(pos.toShortString()+" "+state.getValue(com.aetherianartificer.townstead.block.RoomThermostatBlock.MODE)
                        +" target "+state.getValue(com.aetherianartificer.townstead.block.RoomThermostatBlock.TARGET)
                        +" C, output "+state.getValue(com.aetherianartificer.townstead.block.RoomThermostatBlock.POWERED));
            }
            float nativeEffect = ThermalBlocks.moddedTemperature(level,pos,state);
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            var profile = ThermalBlocks.appliance(state, settings);
            ThermalAppliance.Output output;
            if (profile != null) output = profile.output(nativeEffect,Float.isFinite(nativeEffect) ? nativeEffect != 0 : ThermalBlocks.isActive(state),
                    (float) r.temperature);
            else {
                float effect = nativeEffect;
                if (!Float.isFinite(effect)) {
                    int sign = ThermalBlocks.taggedSource(state);
                    effect = sign > 0 ? settings.heatSourceOffset() : sign < 0 ? settings.coolingSourceOffset() : 0;
                }
                float radiant = effect < 0 && !Float.isFinite(nativeEffect)
                        ? ThermalBlocks.coolingStrength(settings, (float) r.temperature) : effect;
                output = new ThermalAppliance.Output(RoomHeatBalance.sourcePower(effect,settings.roomSourcePower(),1,1),radiant,true);
            }
            if (output.radiantDegrees() != 0) r.localSources.put(pos,output.radiantDegrees());
            int exposed = 0;
            for (Direction d : Direction.values()) {
                BlockPos adjacent = pos.relative(d);
                if (level.isLoaded(adjacent) && level.getBlockState(adjacent).getCollisionShape(level,adjacent).isEmpty()) exposed++;
            }
            double share = r.shape.cells().contains(pos.asLong()) ? 1 : RoomHeatBalance.sourceShare(entry.getValue(),exposed);
            double watts = output.watts()*share;
            double z = Math.max(0, Math.min(1, (pos.getY() - r.minY + 0.5) / height));
            if (watts > 0) warmDrive += watts * (1 - z); else coldDrive += -watts * z;
            if (watts < 0) {
                var cooling = RoomHeatBalance.cooling(watts, r.reservoir,
                        profile == null ? Math.abs(output.radiantDegrees()) : settings.fireMaxRise());
                r.coolingConductance += cooling.conductance();
                r.coolingPotential += cooling.conductance()*cooling.reservoir();
            } else power += watts;
            if (profile == null || profile.draft()) r.draft += RoomHeatBalance.draft(watts, settings.fireMaxRise());
            if (watts != 0 || profile != null && (profile.watts() != 0 || profile.radiantDegrees() != 0))
                r.sourceDetails.add(id+" at "+pos.toShortString()+": "+Math.round(watts)+" W"+(watts < 0 ? " nominal, bounded cooling" : "")+"; "
                        +(output.estimated() ? "estimated native/tag fallback" : "explicit profile")+"; native "+nativeEffect);
        }
        r.stratification = RoomHeatBalance.stratification(warmDrive, coldDrive, r.footprint, height);
        return power;
    }
    /** Operative warmth near a source is distinct from the region's stored air temperature. */
    private static double experienced(ServerLevel level, Region room, BlockPos position) {
        double heat = 0, cool = 0, strongestHeat = 0, strongestCool = 0;
        for (var source : room.localSources.entrySet()) {
            double effect = RoomHeatBalance.localExposure(source.getValue(), position.distSqr(source.getKey()));
            if (effect == 0 || !visible(level, position, source.getKey())) continue;
            if (effect > 0) { heat += effect; strongestHeat = Math.max(strongestHeat, effect); }
            else { cool -= effect; strongestCool = Math.max(strongestCool, -effect); }
        }
        var settings = TemperatureSettings.get();
        return air(room, position, 1.0) + RoomHeatBalance.combinedExposure(heat, strongestHeat, settings.radiantMax(), settings.radiantStack())
                - RoomHeatBalance.combinedExposure(cool, strongestCool, settings.radiantMax(), settings.radiantColdStack());
    }

    public static boolean visible(ServerLevel level, BlockPos observer, BlockPos source) {
        var start = net.minecraft.world.phys.Vec3.atCenterOf(observer);
        var end = net.minecraft.world.phys.Vec3.atCenterOf(source);
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) * 2));
        for (int i = 0; i <= steps; i++) {
            if (!level.isLoaded(BlockPos.containing(start.lerp(end, i / (double) steps)))) return false;
        }
        var hit = level.clip(exposureRay(start, end));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(source);
    }

    static net.minecraft.world.level.ClipContext exposureRay(net.minecraft.world.phys.Vec3 start,
                                                            net.minecraft.world.phys.Vec3 end) {
        // Environmental sampling has no entity. 1.20.1 explicitly handles a null entity;
        // newer versions expose the collision-context overload and dereference null entities.
        return new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                //? if >=1.21 {
                net.minecraft.world.phys.shapes.CollisionContext.empty());
                //?} else {
                /*(net.minecraft.world.entity.Entity) null);
                *///?}
    }

    /** A field diagnostic: stored temperature, fluxes and expected equilibrium at current inputs. */
    public static Optional<String> describe(ServerLevel level, BlockPos pos) {
        at(level, pos);
        World world = WORLDS.get(level);
        if (world == null) return Optional.empty();
        Region r = world.owner(pos.asLong());
        if (r == null) return Optional.empty();
        return Optional.of(String.format(java.util.Locale.ROOT,
                "Air %.1f C; operative %.1f C; outside %.1f C; ground %.1f C; volume %d; input %.0f W; fire draft %.0f W/K; air-to-network %.0f W; ventilation loss %.0f W; adjacent mean %.1f C; unresolved faces %d. Network: %s; supplied %.1f J; escaped %.1f J; balance error %.6f J. Sources: %s. Controllers: %s",
                r.temperature,experienced(level,r,pos),r.reservoir,r.ground,r.shape.cells().size(),r.power,r.draft,r.airToWalls,
                r.externalLoss,r.wallTemperature,r.unresolved,world.status,world.supplied,world.escaped,world.energyError,
                String.join(" | ",r.sourceDetails),String.join(" | ",r.controls)));
    }
    public static void clear() { WORLDS.clear(); RoomHeatBackend.clear(); }
}
