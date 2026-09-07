package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.compat.temperature.*;
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
    private RoomHeat() {}
    private static final class Region {
        ThermalRegionScan.Region shape;
        long anchor, lastTick, lastUsed;
        double temperature;
        boolean dirty;
        Map<Long, Integer> sourceCells;
        Map<BlockPos, Float> localSources = new HashMap<>();
        Map<Long, ThermalRegionScan.Kind> boundaryKinds = new HashMap<>();
        Set<BlockPos> chunkSamples = new HashSet<>();
        Region(ThermalRegionScan.Region shape, long now, double temperature) {
            this.shape = shape; this.anchor = Collections.min(shape.cells());
            this.lastTick = this.lastUsed = now; this.temperature = temperature;
            Set<Long> positions = new HashSet<>(shape.cells());
            shape.faces().forEach(f -> positions.add(f.outside()));
            for (long value : positions) {
                BlockPos p = BlockPos.of(value);
                chunkSamples.add(new BlockPos((p.getX() >> 4) << 4, p.getY(), (p.getZ() >> 4) << 4));
            }
        }
    }
    private static final class World {
        Map<Long, Region> cells = new HashMap<>();
        Map<Long, Set<Region>> watchers = new HashMap<>();
        Set<Region> regions = new LinkedHashSet<>();
        Map<Long, Long> misses = new HashMap<>();
        Map<Long, Double> residual = new HashMap<>();
        long scanTick = Long.MIN_VALUE;
        int scans;
        void remove(Region r) {
            regions.remove(r);
            r.shape.cells().forEach(p -> cells.remove(p, r));
            Set<Long> watched = new HashSet<>(r.shape.cells());
            r.shape.faces().forEach(f -> watched.add(f.outside()));
            for (long p : watched) {
                Set<Region> values = watchers.get(p);
                if (values != null) { values.remove(r); if (values.isEmpty()) watchers.remove(p); }
            }
        }
        void add(Region r) {
            regions.add(r);
            r.shape.cells().forEach(p -> cells.put(p, r));
            Set<Long> watched = new HashSet<>(r.shape.cells());
            r.shape.faces().forEach(f -> watched.add(f.outside()));
            watched.forEach(p -> watchers.computeIfAbsent(p, key -> new HashSet<>()).add(r));
        }
    }

    public static OptionalDouble at(ServerLevel level, BlockPos position) {
        if (!TemperatureSettings.get().roomHeatEnabled() || RoomHeatBackend.bypassed() || !level.getServer().isSameThread()) return OptionalDouble.empty();
        World world = WORLDS.computeIfAbsent(level, key -> new World());
        BlockPos pos = position;
        // Wall-mounted thermometers query their support cell; inspect neighboring air if needed.
        if (kind(level, pos) == ThermalRegionScan.Kind.BARRIER) {
            pos = null;
            for (Direction d : Direction.values()) {
                BlockPos candidate = position.relative(d);
                if (kind(level, candidate) == ThermalRegionScan.Kind.INTERIOR) { pos = candidate; break; }
            }
            if (pos == null) return OptionalDouble.empty();
        }
        long key = pos.asLong(), now = level.getGameTime();
        Region old = world.cells.get(key);
        if (old != null && !old.dirty) { old.lastUsed = now; return OptionalDouble.of(experienced(old, position)); }
        if (world.misses.getOrDefault(key, 0L) > now) return OptionalDouble.empty();
        if (world.scanTick != now) { world.scanTick = now; world.scans = 0; }
        if (world.scans++ >= 2) return OptionalDouble.empty();
        Optional<ThermalRegionScan.Region> scan = ThermalRegionScan.scan(key, MAX_CELLS, new ThermalRegionScan.Access() {
            public ThermalRegionScan.Kind kind(long p) { return RoomHeat.kind(level, BlockPos.of(p)); }
            public long[] neighbors(long p) {
                BlockPos cell = BlockPos.of(p);
                return Arrays.stream(Direction.values()).mapToLong(d -> cell.relative(d).asLong()).toArray();
            }
        });
        if (scan.isEmpty()) {
            if (old != null) world.remove(old);
            if (world.misses.size() >= 2048) world.misses.clear();
            world.misses.put(key, now + 100);
            return OptionalDouble.empty();
        }
        var shape = scan.get();
        // Volume-weighted overlap preserves stored heat when regions merge or split.
        double heat = 0; int overlap = 0;
        Set<Region> replaced = new HashSet<>();
        for (long cell : shape.cells()) {
            Region previous = world.cells.get(cell);
            if (previous != null) { heat += previous.temperature; overlap++; replaced.add(previous); }
            else if (world.residual.containsKey(cell)) { heat += world.residual.remove(cell); overlap++; }
        }
        long anchor = Collections.min(shape.cells());
        double ambient = outside(level, shape);
        double initial = overlap == 0 ? RoomHeatData.get(level).temperature(anchor, ambient)
                : (heat + ambient * (shape.cells().size() - overlap)) / shape.cells().size();
        // Carry unvisited portions of split rooms forward without retaining stale topology.
        for (Region r : replaced) {
            if (world.residual.size() > MAX_CELLS * 2) world.residual.clear();
            for (long cell : r.shape.cells())
                if (!shape.cells().contains(cell)) world.residual.put(cell, r.temperature);
            world.remove(r);
        }
        if (world.regions.size() >= MAX_ROOMS) {
            Region oldest = world.regions.stream().min(Comparator.comparingLong(r -> r.lastUsed)).orElseThrow();
            world.remove(oldest);
        }
        Region region = new Region(shape, now, initial);
        for (var face : shape.faces()) region.boundaryKinds.put(face.outside(), kind(level, BlockPos.of(face.outside())));
        // Rebuilding geometry must not restart the thermal clock during cooking/block updates.
        if (!replaced.isEmpty()) region.lastTick = replaced.stream().mapToLong(r -> r.lastTick).min().orElse(now);
        world.add(region);
        advance(level, world, region, Map.of(), now);
        return OptionalDouble.of(experienced(region, position));
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

    public static void changed(ServerLevel level, BlockPos pos) {
        World world = WORLDS.get(level);
        if (world == null) return;
        Set<Region> watched = world.watchers.get(pos.asLong());
        if (watched != null) {
            ThermalRegionScan.Kind current = kind(level, pos);
            for (Region region : watched) {
                ThermalRegionScan.Kind previous = region.shape.cells().contains(pos.asLong())
                        ? ThermalRegionScan.Kind.INTERIOR : region.boundaryKinds.get(pos.asLong());
                if (previous != current) region.dirty = true;
                // Lit/open flags and material swaps change heat flow, not the air region.
                region.sourceCells = null;
            }
        }
        world.misses.clear();
    }

    public static void tick(MinecraftServer server) {
        if (!TemperatureSettings.get().roomHeatEnabled()) return;
        for (ServerLevel level : server.getAllLevels()) {
            World world = WORLDS.get(level);
            if (world == null) continue;
            long now = level.getGameTime();
            int updated = 0;
            Map<Region, Double> snapshot = new IdentityHashMap<>();
            world.regions.forEach(r -> snapshot.put(r, r.temperature));
            for (Region r : new ArrayList<>(world.regions)) {
                if (!level.isLoaded(BlockPos.of(r.anchor))) { world.remove(r); continue; }
                if (r.dirty) {
                    // Rediscover using an existing air cell, retaining overlap temperatures.
                    at(level, BlockPos.of(r.anchor));
                    continue;
                }
                if (now - r.lastTick < 20 || updated >= 4) continue;
                updated++;
                advance(level, world, r, snapshot, now);
            }
        }
    }

    private static double outside(ServerLevel level, ThermalRegionScan.Region shape) {
        BlockPos anchor = BlockPos.of(Collections.min(shape.cells()));
        int top = shape.cells().stream().mapToInt(p -> BlockPos.of(p).getY()).max().orElse(anchor.getY());
        return RoomHeatBackend.outdoor(level, new BlockPos(anchor.getX(), Math.min(level.getMaxBuildHeight()-1, top + 2), anchor.getZ()));
    }

    private static void advance(ServerLevel level, World world, Region r, Map<Region, Double> snapshot, long now) {
        // Never read blocks in unloaded chunks or simulate fuel consumption while they are unloaded.
        for (BlockPos chunk : r.chunkSamples) if (!level.isLoaded(chunk)) { world.remove(r); return; }
        double ambient = outside(level, r.shape);
        double conductance = 0, weightedAmbient = 0, power = 0;
        if (r.sourceCells == null) {
            r.sourceCells = new HashMap<>();
            for (long cell : r.shape.cells())
                if (!level.getBlockState(BlockPos.of(cell)).isAir()) r.sourceCells.put(cell, 6);
            for (var face : r.shape.faces())
                if (!level.getBlockState(BlockPos.of(face.outside())).isAir()) r.sourceCells.merge(face.outside(), 1, Integer::sum);
        }
        for (var face : r.shape.faces()) {
            BlockPos wall = BlockPos.of(face.outside());
            BlockState state = level.getBlockState(wall);
            double transfer = ThermalMaterials.conductance(state, TemperatureSettings.get());
            if (kind(level, wall) == ThermalRegionScan.Kind.EXTERIOR)
                transfer = TemperatureSettings.get().roomOpeningConductance();
            BlockPos inner = BlockPos.of(face.inside());
            BlockPos beyond = wall.offset(wall.getX()-inner.getX(), wall.getY()-inner.getY(), wall.getZ()-inner.getZ());
            Region neighbor = world.cells.get(beyond.asLong());
            if (neighbor == r) continue;
            double reservoir = neighbor != null && !neighbor.dirty ? snapshot.getOrDefault(neighbor, neighbor.temperature) : ambient;
            conductance += transfer; weightedAmbient += transfer * reservoir;
        }
        var backend = TemperatureBridgeResolver.get();
        r.localSources.clear();
        for (var entry : r.sourceCells.entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!level.isLoaded(pos)) { world.remove(r); return; }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            float effect = backend.blockTemperatureCelsius(level, pos, state);
            if (!Float.isFinite(effect)) {
                int sign = ThermalBlocks.taggedSource(state);
                effect = sign > 0 ? TemperatureSettings.get().heatSourceOffset()
                        : sign < 0 ? TemperatureSettings.get().coolingSourceOffset() : 0;
            }
            if (effect != 0) r.localSources.put(pos, effect);
            int exposedFaces = 0;
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = pos.relative(direction);
                if (level.isLoaded(adjacent) && level.getBlockState(adjacent).getCollisionShape(level, adjacent).isEmpty())
                    exposedFaces++;
            }
            power += effect * RoomHeatBalance.sourceShare(entry.getValue(), exposedFaces)
                    * TemperatureSettings.get().roomSourcePower();
        }
        double seconds = Math.min(5, Math.max(0, now - r.lastTick) / 20.0);
        r.temperature = RoomHeatBalance.advance(r.temperature, r.shape.cells().size() * TemperatureSettings.get().roomHeatCapacity(),
                power, conductance, conductance > 0 ? weightedAmbient / conductance : ambient, seconds);
        r.lastTick = now;
        RoomHeatData.get(level).put(r.anchor, r.temperature);
    }
    /** Operative warmth near a source is distinct from the region's stored air temperature. */
    private static double experienced(Region room, BlockPos position) {
        double heat = 0, cool = 0;
        for (var source : room.localSources.entrySet()) {
            double effect = RoomHeatBalance.localExposure(source.getValue(), position.distSqr(source.getKey()));
            if (effect > 0) heat += effect; else cool += effect;
        }
        var settings = TemperatureSettings.get();
        return room.temperature + RoomHeatBalance.warmExposure(room.temperature,
                Math.min(heat, Math.abs(settings.heatSourceOffset()) * 1.5))
                + Math.max(cool, -Math.abs(settings.coolingSourceOffset()) * 1.5);
    }
    public static void clear() { WORLDS.clear(); }
}
