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
        double power, conductance, reservoir, capacity;
        double wallCapacity, wallTemperature, airToWalls, externalLoss;
        int unresolved;
        List<String> sourceDetails = new ArrayList<>();
        List<String> controls = new ArrayList<>();
        boolean dirty, solved;
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
        long lastStep = Long.MIN_VALUE;
        double energyError, supplied, escaped;
        boolean valid;
        String status = "Awaiting thermal update";
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
        return sample(level, position, false);
    }

    public static OptionalDouble airAt(ServerLevel level, BlockPos position) {
        return sample(level, position, true);
    }

    /** Control requires a fresh successful solve, rather than stale saved air after a failed update. */
    public static OptionalDouble controlAirAt(ServerLevel level, BlockPos position) {
        OptionalDouble air = airAt(level,position);
        World world = WORLDS.get(level);
        Region region = world == null ? null : world.cells.get(position.asLong());
        return air.isPresent() && world.valid && region != null && region.solved && !region.dirty
                && level.getGameTime()-region.lastTick <= 60 ? air : OptionalDouble.empty();
    }

    private static OptionalDouble sample(ServerLevel level, BlockPos position, boolean airOnly) {
        if (!TemperatureSettings.get().roomHeatEnabled() || RoomHeatBackend.bypassed() || !level.getServer().isSameThread()) return OptionalDouble.empty();
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
                for (Direction d : Direction.values()) {
                    BlockPos candidate = position.relative(d);
                    if (kind(level, candidate) == ThermalRegionScan.Kind.INTERIOR) { pos = candidate; break; }
                }
                if (pos == null) return OptionalDouble.empty();
            }
        }
        long key = pos.asLong(), now = level.getGameTime();
        Region old = world.cells.get(key);
        if (old != null && !old.dirty) { old.lastUsed = now; return OptionalDouble.of(airOnly ? old.temperature : experienced(level, old, position)); }
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
            return OptionalDouble.empty(); // Never evict a neighbor merely because another sensor was queried.
        }
        Region region = new Region(shape, now, initial);
        for (var face : shape.faces()) region.boundaryKinds.put(face.outside(), kind(level, BlockPos.of(face.outside())));
        // Rebuilding geometry must not restart the thermal clock during cooking/block updates.
        if (!replaced.isEmpty()) region.lastTick = replaced.stream().mapToLong(r -> r.lastTick).min().orElse(now);
        world.add(region);

        return OptionalDouble.of(airOnly ? region.temperature : experienced(level, region, position));
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
        if (!level.getServer().isSameThread()) return;
        // Preserve lit/open changes, but never resurrect stored heat when a block is replaced.
        var stored = RoomHeatData.get(level);
        String material = wallIdentity(level.getBlockState(pos));
        stored.retainSolidMaterial(pos.asLong(), material);
        for (Direction direction : Direction.values())
            stored.retainWallMaterial(new ThermalRegionScan.Face(pos.relative(direction).asLong(), pos.asLong()), material);
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
        VillagerDoorCleanup.tick(server);
        if (!TemperatureSettings.get().roomHeatEnabled()) return;
        for (ServerLevel level : server.getAllLevels()) {
            World world = WORLDS.get(level);
            if (world == null) continue;
            long now = level.getGameTime();
            for (Region r : new ArrayList<>(world.regions)) {
                if (r.chunkSamples.stream().anyMatch(p -> !level.isLoaded(p))) { world.remove(r); continue; }
                if (r.dirty) at(level, BlockPos.of(r.anchor));
            }
            if (world.lastStep != Long.MIN_VALUE && now - world.lastStep < 20) continue;
            // Discover connected interiors whether or not a player or villager queried them.
            // sample() bounds scans per tick. Until resolved, an interior is insulated, not outdoors.
            discoverNeighbors(level, world);
            double seconds = Math.min(5, world.lastStep == Long.MIN_VALUE ? 1 : Math.max(0, now-world.lastStep)/20.0)
                    * Math.min(100, TemperatureSettings.get().thermalTimeScale());
            advanceNetwork(level, world, Math.min(500, seconds));
            world.lastStep = now;
        }
    }

    private static double outside(ServerLevel level, ThermalRegionScan.Region shape) {
        BlockPos anchor = BlockPos.of(Collections.min(shape.cells()));
        int top = shape.cells().stream().mapToInt(p -> BlockPos.of(p).getY()).max().orElse(anchor.getY());
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, anchor.getX(), anchor.getZ());
        return RoomHeatBackend.outdoor(level, new BlockPos(anchor.getX(), Math.min(level.getMaxBuildHeight()-1, Math.max(surface, top + 2)), anchor.getZ()));
    }

    private static void discoverNeighbors(ServerLevel level, World world) {
        Map<Long,ThermalRegionScan.Kind> kinds = new HashMap<>();
        for (Region r : new ArrayList<>(world.regions)) {
            if (r.dirty) continue;
            for (var face : r.shape.faces()) {
                BlockPos start = BlockPos.of(face.outside()), direction = start.subtract(BlockPos.of(face.inside()));
                for (int depth = 0; depth <= ThermalBoundary.MAX_DEPTH; depth++) {
                    BlockPos pos = start.offset(direction.getX()*depth, direction.getY()*depth, direction.getZ()*depth);
                    var type = kinds.computeIfAbsent(pos.asLong(), p -> kind(level,BlockPos.of(p)));
                    if (type == ThermalRegionScan.Kind.UNLOADED || type == ThermalRegionScan.Kind.EXTERIOR) break;
                    if (type == ThermalRegionScan.Kind.INTERIOR) {
                        if (!world.cells.containsKey(pos.asLong()) && world.regions.size() < MAX_ROOMS) airAt(level, pos);
                        break;
                    }
                }
            }
        }
    }

    private record PhysicalEdge(long a, long b) {
        static PhysicalEdge of(long a, long b) { return new PhysicalEdge(Math.min(a,b), Math.max(a,b)); }
    }
    private static final class NetworkBuild {
        final List<ThermalNetwork.Node> nodes = new ArrayList<>();
        final List<ThermalNetwork.Link> links = new ArrayList<>();
        final Map<Region,Integer> rooms = new LinkedHashMap<>();
        final Map<Long,Integer> solids = new LinkedHashMap<>();
        final Map<Long,String> materials = new HashMap<>();
        final Set<PhysicalEdge> faces = new HashSet<>();
        void link(long aPos, long bPos, int a, int b, double g) {
            if (a != b && faces.add(PhysicalEdge.of(aPos,bPos))) links.add(new ThermalNetwork.Link(a,b,g));
        }
        void outside(long aPos, long bPos, int a, double g, double ambient) {
            if (!faces.add(PhysicalEdge.of(aPos,bPos))) return;
            var old = nodes.get(a);
            double total = old.outsideConductance()+g;
            nodes.set(a, new ThermalNetwork.Node(old.capacity(),old.temperature(),old.power(),total,
                    (old.outsideConductance()*old.outside()+g*ambient)/total));
        }
    }

    private static void advanceNetwork(ServerLevel level, World world, double seconds) {
        var settings = TemperatureSettings.get();
        var stored = RoomHeatData.get(level);
        var build = new NetworkBuild();
        world.valid = false;
        var states = new HashMap<Long,BlockState>();
        var kinds = new HashMap<Long,ThermalRegionScan.Kind>();
        var ambients = new HashMap<Region,Double>();
        List<Region> regions = world.regions.stream().filter(r -> !r.dirty)
                .sorted(Comparator.comparingLong(r -> r.anchor)).toList();
        for (Region r : regions) {
            double ambient = outside(level,r.shape);
            ambients.put(r,ambient);
            r.capacity = r.shape.cells().size()*settings.roomHeatCapacity();
            r.reservoir = ambient; r.unresolved = 0;
            r.power = sourcePower(level,r,states);
            build.rooms.put(r,build.nodes.size());
            build.nodes.add(new ThermalNetwork.Node(r.capacity,r.temperature,r.power,
                    RoomHeatBalance.ventilation(r.shape.cells().size(),settings.airChangesPerHour()),ambient));
        }
        for (Region r : regions) {
            for (var face : r.shape.faces()) {
                BlockPos previous = BlockPos.of(face.inside());
                BlockPos start = BlockPos.of(face.outside()), direction = start.subtract(previous);
                int previousNode = build.rooms.get(r);
                double previousHalfResistance = 0;
                for (int depth = 0; depth <= ThermalBoundary.MAX_DEPTH; depth++) {
                    BlockPos pos = start.offset(direction.getX()*depth,direction.getY()*depth,direction.getZ()*depth);
                    var type = kinds.computeIfAbsent(pos.asLong(), p -> kind(level,BlockPos.of(p)));
                    if (type == ThermalRegionScan.Kind.UNLOADED) { r.unresolved++; break; }
                    if (type != ThermalRegionScan.Kind.BARRIER) {
                        double g = previousHalfResistance > 0 ? 1/previousHalfResistance : settings.roomOpeningConductance();
                        if (type == ThermalRegionScan.Kind.EXTERIOR) {
                            build.outside(previous.asLong(),pos.asLong(),previousNode,g,ambients.get(r));
                        } else {
                            Region neighbor = world.cells.get(pos.asLong());
                            Integer other = build.rooms.get(neighbor);
                            if (other == null) r.unresolved++;
                            else build.link(previous.asLong(),pos.asLong(),previousNode,other,g);
                        }
                        break;
                    }
                    if (depth == ThermalBoundary.MAX_DEPTH) {
                        // Reaching a scan budget is not evidence of an outdoor reservoir.
                        // The explored solid retains its heat; report the unresolved continuation.
                        r.unresolved++;
                        break;
                    }
                    BlockState state = states.computeIfAbsent(pos.asLong(), p -> level.getBlockState(BlockPos.of(p)));
                    double half = .5/ThermalMaterials.conductance(state,settings);
                    Integer node = build.solids.get(pos.asLong());
                    if (node == null) {
                        if (build.nodes.size() >= 32768) { world.status = "Thermal node budget exceeded; update deferred"; return; }
                        boolean open = ThermalMaterials.openAperture(state);
                        String material = open ? "open_aperture" : wallIdentity(state);
                        node = build.nodes.size();
                        build.solids.put(pos.asLong(),node); build.materials.put(pos.asLong(),material);
                        build.nodes.add(new ThermalNetwork.Node(open ? settings.roomHeatCapacity()
                                : ThermalMaterials.surfaceCapacity(state,settings)*6,
                                stored.solidTemperature(pos.asLong(),material,ambients.get(r)),0,0,ambients.get(r)));
                    }
                    build.link(previous.asLong(),pos.asLong(),previousNode,node,1/(previousHalfResistance+half));
                    previous = pos; previousNode = node; previousHalfResistance = half;
                }
            }
        }
        ThermalNetwork.Result result;
        try { result = ThermalNetwork.advance(build.nodes,build.links,seconds); }
        catch (IllegalStateException invalid) {
            world.status = "Thermal solve deferred: " + invalid.getMessage();
            return;
        }
        for (var entry : build.solids.entrySet())
            stored.putSolid(entry.getKey(),build.materials.get(entry.getKey()),result.temperatures()[entry.getValue()]);
        for (var entry : build.rooms.entrySet()) {
            Region r = entry.getKey(); int index = entry.getValue();
            r.temperature = result.temperatures()[index];
            r.solved = true;
            r.wallTemperature = r.wallCapacity = r.airToWalls = 0;
            r.conductance = build.nodes.get(index).outsideConductance();
            r.externalLoss = r.conductance*(r.temperature-r.reservoir);
            for (var link : build.links) {
                int other = link.a() == index ? link.b() : link.b() == index ? link.a() : -1;
                if (other < 0) continue;
                r.airToWalls += link.conductance()*(r.temperature-result.temperatures()[other]);
                r.wallTemperature += link.conductance()*result.temperatures()[other];
                r.wallCapacity += link.conductance();
            }
            r.wallTemperature = r.wallCapacity > 0 ? r.wallTemperature/r.wallCapacity : r.temperature;
            stored.put(r.anchor,r.temperature);
            r.lastTick = level.getGameTime();
        }
        world.valid = true;
        world.energyError = result.errorJoules(); world.supplied = result.suppliedJoules(); world.escaped = result.escapedJoules();
        world.status = build.rooms.size()+" rooms / "+build.solids.size()+" solid or aperture nodes / "+build.links.size()+" shared links";
    }

    private static double sourcePower(ServerLevel level, Region r, Map<Long,BlockState> states) {
        var settings = TemperatureSettings.get();
        if (r.sourceCells == null) {
            r.sourceCells = new TreeMap<>();
            for (long cell : r.shape.cells())
                if (!level.getBlockState(BlockPos.of(cell)).isAir()) r.sourceCells.put(cell,6);
            for (var face : r.shape.faces())
                if (!level.getBlockState(BlockPos.of(face.outside())).isAir()) r.sourceCells.merge(face.outside(),1,Integer::sum);
        }
        double power = 0;
        r.localSources.clear(); r.sourceDetails.clear(); r.controls.clear();
        for (var entry : r.sourceCells.entrySet()) {
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
            var profile = settings.appliance(id);
            ThermalAppliance.Output output;
            if (profile != null) output = profile.output(nativeEffect,Float.isFinite(nativeEffect) ? nativeEffect != 0 : ThermalBlocks.isActive(state));
            else {
                float effect = nativeEffect;
                if (!Float.isFinite(effect)) {
                    int sign = ThermalBlocks.taggedSource(state);
                    effect = sign > 0 ? settings.heatSourceOffset() : sign < 0 ? settings.coolingSourceOffset() : 0;
                }
                output = new ThermalAppliance.Output(RoomHeatBalance.roomSourcePower(effect,settings.roomSourcePower(),1,1,
                        state.is(ThermalBlocks.COOKING_SOURCES),settings.cookingRoomHeatFraction()),effect,true);
            }
            if (output.radiantDegrees() != 0) r.localSources.put(pos,output.radiantDegrees());
            int exposed = 0;
            for (Direction d : Direction.values()) {
                BlockPos adjacent = pos.relative(d);
                if (level.isLoaded(adjacent) && level.getBlockState(adjacent).getCollisionShape(level,adjacent).isEmpty()) exposed++;
            }
            double share = r.shape.cells().contains(pos.asLong()) ? 1 : RoomHeatBalance.sourceShare(entry.getValue(),exposed);
            double watts = output.watts()*share;
            power += watts;
            if (watts != 0 || profile != null && (profile.watts() != 0 || profile.radiantDegrees() != 0))
                r.sourceDetails.add(id+" at "+pos.toShortString()+": "+Math.round(watts)+" W; "
                        +(output.estimated() ? "estimated native/tag fallback" : "explicit profile")+"; native "+nativeEffect);
        }
        return power;
    }
    /** Operative warmth near a source is distinct from the region's stored air temperature. */
    private static double experienced(ServerLevel level, Region room, BlockPos position) {
        double heat = 0, cool = 0;
        for (var source : room.localSources.entrySet()) {
            double effect = RoomHeatBalance.localExposure(source.getValue(), position.distSqr(source.getKey()));
            if (effect == 0 || !visible(level, position, source.getKey())) continue;
            if (effect > 0) heat += effect; else cool += effect;
        }
        return room.temperature + heat + cool;
    }

    private static boolean visible(ServerLevel level, BlockPos observer, BlockPos source) {
        var start = net.minecraft.world.phys.Vec3.atCenterOf(observer);
        var end = net.minecraft.world.phys.Vec3.atCenterOf(source);
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) * 2));
        for (int i = 0; i <= steps; i++) {
            if (!level.isLoaded(BlockPos.containing(start.lerp(end, i / (double) steps)))) return false;
        }
        var hit = level.clip(exposureRay(start, end));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(source);
    }

    private static String wallIdentity(BlockState state) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())
                + "/" + ThermalMaterials.material(state).name();
    }

    static net.minecraft.world.level.ClipContext exposureRay(net.minecraft.world.phys.Vec3 start,
                                                            net.minecraft.world.phys.Vec3 end) {
        // This environmental sample has no entity. The Entity overload dereferences its argument.
        return new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty());
    }

    /** A field diagnostic: stored temperature, fluxes and expected equilibrium at current inputs. */
    public static Optional<String> describe(ServerLevel level, BlockPos pos) {
        at(level, pos);
        World world = WORLDS.get(level);
        if (world == null) return Optional.empty();
        Region r = world.cells.get(pos.asLong());
        if (r == null) return Optional.empty();
        return Optional.of(String.format(java.util.Locale.ROOT,
                "Air %.1f C; operative %.1f C; outside %.1f C; volume %d; input %.0f W; air-to-network %.0f W; ventilation loss %.0f W; adjacent mean %.1f C; unresolved faces %d. Network: %s; supplied %.1f J; escaped %.1f J; balance error %.6f J. Sources: %s. Controllers: %s",
                r.temperature,experienced(level,r,pos),r.reservoir,r.shape.cells().size(),r.power,r.airToWalls,
                r.externalLoss,r.wallTemperature,r.unresolved,world.status,world.supplied,world.escaped,world.energyError,
                String.join(" | ",r.sourceDetails),String.join(" | ",r.controls)));
    }
    public static void clear() { WORLDS.clear(); }
}
