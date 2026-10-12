package com.aetherianartificer.townstead.recognition;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import it.unimi.dsi.fastutil.longs.LongList;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps enclosures current as the world changes: a pen is added when its ring closes, and a pen
 * already on the books is checked again when something near it changes.
 *
 * <p>Almost no change can close anything, so each one passes through cheap tests before any real
 * walk: whether it changes where an animal can stand at all, whether the type's own blocks are
 * beside it, and whether the ground around it is still one piece ({@link WalkEnclosure#localSides}).
 * Only a change that parts the ground starts a walk, and that walk costs about the size of the
 * smaller side. Villages with MCA's Auto Scan off are left alone, as MCA leaves their rooms.</p>
 */
public final class EnclosureWatcher {
    /** A placed block settles this long before it is read, so a gate added after the last fence is one change. */
    private static final int SETTLE_TICKS = 20;
    private static final int RECHECK_TICKS = 40;
    private static final int MAX_PENDING = 1024;
    private static final int MAX_CHANGES_PER_TICK = 16;
    private static final int MAX_RECHECKS_PER_TICK = 2;
    /** How close a change must be to a type's own block to be about that type. */
    private static final int NEAR_OWN_BLOCK = 2;
    /** How far outside a pen's box a change still touches it. */
    private static final int TOUCH_MARGIN = 2;

    private enum Shape { OPEN, SOLID, BARRIER }

    private record Change(ServerLevel level, BlockPos pos, long due) {}
    private record Recheck(ServerLevel level, int villageId, int buildingId, BlockPos source, long due) {}

    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final Map<BlockState, Shape> SHAPES = new ConcurrentHashMap<>();

    private static final class State {
        final Map<String, Change> changes = new LinkedHashMap<>();
        final Map<String, Recheck> rechecks = new LinkedHashMap<>();
        final Set<String> open = new HashSet<>();
    }

    private EnclosureWatcher() {}

    /** From every block replacement on a server level, so it must stay cheap. */
    public static void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (level == null || pos == null || oldState == null || newState == null) return;
        if (shape(oldState) == shape(newState)) return;
        if (!Switchboard.get(TownsteadConfig.ENABLE_ENCLOSURE_RECOGNITION)) return;
        synchronized (STATES) {
            State state = STATES.computeIfAbsent(level.getServer(), ignored -> new State());
            if (state.changes.size() >= MAX_PENDING) return;
            BlockPos at = pos.immutable();
            state.changes.put(key(level, at), new Change(level, at, level.getGameTime() + SETTLE_TICKS));
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        List<Change> changes = new ArrayList<>();
        List<Recheck> rechecks = new ArrayList<>();
        synchronized (STATES) {
            State state = STATES.get(server);
            if (state == null) return;
            if (!Switchboard.get(TownsteadConfig.ENABLE_ENCLOSURE_RECOGNITION)) {
                state.changes.clear();
                state.rechecks.clear();
                return;
            }
            drain(state.changes.values().iterator(), changes, MAX_CHANGES_PER_TICK, Change::due, Change::level);
            drain(state.rechecks.values().iterator(), rechecks, MAX_RECHECKS_PER_TICK, Recheck::due, Recheck::level);
        }
        for (Change change : changes) {
            try {
                process(change.level(), change.pos());
            } catch (RuntimeException ex) {
                Townstead.LOGGER.warn("[Enclosure] change at {} failed: {}", change.pos(), ex.toString());
            }
        }
        for (Recheck recheck : rechecks) {
            try {
                recheck(recheck);
            } catch (RuntimeException ex) {
                Townstead.LOGGER.warn("[Enclosure] recheck of {} failed: {}", recheck.buildingId(), ex.toString());
            }
        }
    }

    /** Datapack reload: tags decide what is a barrier. */
    public static void invalidateShapes() {
        SHAPES.clear();
    }

    private static <T> void drain(Iterator<T> it, List<T> out, int limit,
                                  java.util.function.ToLongFunction<T> due,
                                  java.util.function.Function<T, ServerLevel> level) {
        while (it.hasNext() && out.size() < limit) {
            T item = it.next();
            if (level.apply(item).getGameTime() < due.applyAsLong(item)) continue;
            it.remove();
            out.add(item);
        }
    }

    private static void process(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        Village village = VillageManager.get(level).findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village == null || !village.isAutoScan()) return;

        for (Building building : McaBuildings.all(village)) {
            if (!SiteRequirements.isEnclosed(building.getType())) continue;
            if (!McaBuildings.isOpenAirRecord(level, village, building) || !touches(building, pos)) continue;
            EnclosureFootprints.invalidate(level, village, building);
            scheduleRecheck(level, village, building, pos);
        }

        List<BuildingType> types = enclosedTypes();
        if (types.isEmpty() || !nearOwnBlock(level, types, pos)) return;
        SiteRequirements.Scan scan = new SiteRequirements.Scan(level);
        LongList sides = WalkEnclosure.localSides(scan, pos.getX(), pos.getY(), pos.getZ());
        if (sides.size() < 2) return;
        int radius = 0;
        int maxInterior = 0;
        for (BuildingType type : types) {
            radius = Math.max(radius, OptionalBuildingRecognition.reach(type.name()));
            maxInterior = Math.max(maxInterior, SiteRequirements.maxInterior(type.name()));
        }
        for (WalkEnclosure.Region region : WalkEnclosure.closedSides(scan, sides, radius, maxInterior)) {
            if (region.status() != WalkEnclosure.Status.FOUND) continue;
            // The cell nearest the change: recognition looks for the type's blocks near it.
            addClosed(level, nearest(region, pos));
        }
    }

    private static void addClosed(ServerLevel level, BlockPos inside) {
        Optional<OptionalBuildingRecognition.Candidate> found = OptionalBuildingRecognition.find(level, inside);
        if (found.isEmpty() || !SiteRequirements.isEnclosed(found.get().typeName())) return;
        OptionalBuildingRecognition.Candidate candidate = found.get();
        if (BuildingEnclosurePolicies.modeOf(candidate.typeName()).allowsRoom()
                && OptionalBuildingRecognition.roomCanHandle(VillageManager.get(level), inside, "ADD_BUILDING")) {
            return;
        }
        OptionalBuildingRecognition.Registration registration =
                OptionalBuildingRecognition.registerInVillage(level, candidate);
        // EXISTING is a pen on the books closing again, which its recheck reports.
        if (registration == OptionalBuildingRecognition.Registration.FAILED) return;
        VillageManager.get(level).findNearestVillage(inside, Village.MERGE_MARGIN).ifPresent(village ->
                com.aetherianartificer.townstead.compat.mca.BuildingReportReconciler.reconcile(
                        level, inside, village, Townstead.LOGGER));
        if (registration == OptionalBuildingRecognition.Registration.CREATED) {
            EnclosureNotices.added(EnclosureNotices.nearest(level, inside), candidate.typeName());
        }
    }

    private static void recheck(Recheck recheck) {
        ServerLevel level = recheck.level();
        Village village = VillageManager.get(level).getOrEmpty(recheck.villageId()).orElse(null);
        if (village == null) return;
        Building building = McaBuildings.byId(village, recheck.buildingId());
        if (building == null) return;
        String type = building.getType();
        List<BlockPos> seeds = new ArrayList<>(building.getBlockPosStream().toList());
        seeds.add(building.getCenter());
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        int reach = OptionalBuildingRecognition.reach(type);

        SiteRequirements.Scan scan = new SiteRequirements.Scan(level).bounded(new WalkEnclosure.Bounds(
                min.getX() - 1, min.getZ() - 1, max.getX() + 1, max.getZ() + 1));
        SiteRequirements.Evaluation evaluation = SiteRequirements.evaluate(scan, type, seeds, building.getCenter(), reach);
        if (evaluation.verdict() == SiteRequirements.Verdict.UNSATISFIED) {
            // Out of the old box may only mean the pen has grown.
            scan = new SiteRequirements.Scan(level);
            evaluation = SiteRequirements.evaluate(scan, type, seeds, building.getCenter(), reach);
        }
        if (evaluation.verdict() == SiteRequirements.Verdict.UNKNOWN) return;

        String penKey = key(level, recheck.villageId(), recheck.buildingId());
        ServerPlayer player = EnclosureNotices.nearest(level, recheck.source());
        if (evaluation.verdict() == SiteRequirements.Verdict.SATISFIED) {
            boolean wasOpen;
            synchronized (STATES) {
                wasOpen = state(level).open.remove(penKey);
            }
            if (wasOpen) EnclosureNotices.closed(player, type);
            BlockPos inside = scan.found();
            if (inside != null) {
                // The same pen at its new size; register replaces the record in place.
                OptionalBuildingRecognition.find(level, inside)
                        .filter(candidate -> SiteRequirements.isEnclosed(candidate.typeName()))
                        .ifPresent(candidate -> OptionalBuildingRecognition.register(level, candidate));
            }
            EnclosureFootprints.invalidate(level, village, building);
            return;
        }
        SiteRequirements.Problem problem = evaluation.problem();
        if (problem == null || problem.reason() != SiteRequirements.Reason.OPEN) return;
        boolean newlyOpen;
        synchronized (STATES) {
            newlyOpen = state(level).open.add(penKey);
        }
        if (newlyOpen) EnclosureNotices.opened(player, type, problem.at());
    }

    private static void scheduleRecheck(ServerLevel level, Village village, Building building, BlockPos source) {
        synchronized (STATES) {
            state(level).rechecks.put(key(level, village.getId(), building.getId()),
                    new Recheck(level, village.getId(), building.getId(), source, level.getGameTime() + RECHECK_TICKS));
        }
    }

    private static boolean touches(Building building, BlockPos pos) {
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        return pos.getX() >= min.getX() - TOUCH_MARGIN && pos.getX() <= max.getX() + TOUCH_MARGIN
                && pos.getZ() >= min.getZ() - TOUCH_MARGIN && pos.getZ() <= max.getZ() + TOUCH_MARGIN
                && pos.getY() >= min.getY() - TOUCH_MARGIN && pos.getY() <= max.getY() + TOUCH_MARGIN;
    }

    private static BlockPos nearest(WalkEnclosure.Region region, BlockPos pos) {
        long best = 0L;
        long bestDistance = Long.MAX_VALUE;
        var it = region.interior().iterator();
        while (it.hasNext()) {
            long cell = it.nextLong();
            long dx = SiteGeometry.x(cell) - pos.getX();
            long dy = SiteGeometry.y(cell) - pos.getY();
            long dz = SiteGeometry.z(cell) - pos.getZ();
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                best = cell;
                bestDistance = distance;
            }
        }
        return BlockPos.of(best);
    }

    private static List<BuildingType> enclosedTypes() {
        List<BuildingType> out = new ArrayList<>();
        for (String name : BuildingEnclosurePolicies.snapshot().keySet()) {
            if (!SiteRequirements.isEnclosed(name)) continue;
            BuildingType type = BuildingTypes.getInstance().getBuildingTypes().get(name);
            if (type != null) out.add(type);
        }
        return out;
    }

    private static boolean nearOwnBlock(ServerLevel level, List<BuildingType> types, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -NEAR_OWN_BLOCK; dx <= NEAR_OWN_BLOCK; dx++) {
            for (int dy = -NEAR_OWN_BLOCK; dy <= NEAR_OWN_BLOCK; dy++) {
                for (int dz = -NEAR_OWN_BLOCK; dz <= NEAR_OWN_BLOCK; dz++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    for (BuildingType type : types) {
                        if (OptionalBuildingRecognition.matchesBlock(type, state)) return true;
                    }
                }
            }
        }
        return false;
    }

    /** Coarse enough that water spreading or a crop growing never queues anything. */
    private static Shape shape(BlockState state) {
        return SHAPES.computeIfAbsent(state, s -> {
            if (s.is(BlockTags.FENCES) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.WALLS)
                    || s.is(BlockTags.DOORS)) {
                return Shape.BARRIER;
            }
            try {
                return s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty() ? Shape.OPEN : Shape.SOLID;
            } catch (RuntimeException ex) {
                // Some modded blocks read their neighbours for a shape; this is every setBlock, so never throw.
                return Shape.SOLID;
            }
        });
    }

    private static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level.getServer(), ignored -> new State());
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.asLong();
    }

    private static String key(ServerLevel level, int villageId, int buildingId) {
        return level.dimension().location() + "#" + villageId + ":" + buildingId;
    }
}
