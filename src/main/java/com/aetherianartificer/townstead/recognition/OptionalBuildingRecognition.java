package com.aetherianartificer.townstead.recognition;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.mca.BuildingCandidatePolicy;
import com.aetherianartificer.townstead.compat.mca.McaBuildingNbt;
import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recognises data-declared buildings whose enclosure is optional (or absent).
 *
 * <p>The scan only runs when a player asks MCA to recognise a building. It collects the blocks
 * MCA's own {@link BuildingType} says belong to each open-air-capable type and lets MCA's own
 * {@link Building#matchesType(BuildingType)} decide completeness. No recipe, station, or mod id is
 * encoded here.</p>
 *
 * <p>An open-air building has no walls to bound it. A type that declares {@link SiteRequirements}
 * is bounded by its own ground instead: the deck it stands on, the paddock its fences close. Only
 * a type that describes no site falls back to {@code mergeRange}. Height is bounded around the
 * site's own level: generously upward, so a lantern on a mast still belongs to the pier, and only a
 * little downward, so a mineshaft or a cave pool under a field never does.</p>
 */
public final class OptionalBuildingRecognition {
    /** How far from the player the nearest block of a building may be. */
    private static final int DISCOVERY_RADIUS = 16;
    private static final int DEFAULT_REACH = 12;
    /** Liquids are surroundings rather than furniture; only the nearest few are recorded. */
    private static final int LIQUID_CAP = 64;
    /** How close a reported problem must be to the type's own blocks to be about that type. */
    private static final int NEAR_OWN_BLOCK = 2;
    /** How far below the site's lowest cell its parts may sit: a pier's water under the deck. */
    private static final int PARTS_BELOW_SITE = 6;
    /** How far below the anchor a type without a site may reach: a farm's water is level with it. */
    private static final int PARTS_BELOW_ANCHOR = 2;
    /** How far above the site, or the anchor, its parts may sit: a lantern on a mast. */
    private static final int PARTS_ABOVE = 16;
    /** How far above or below the village's buildings a Full Scan still looks for sites. */
    private static final int VILLAGE_HEIGHT_MARGIN = 8;
    /** How far above or below a site's box a player still stands in it, for removal. */
    private static final int STAND_IN_HEIGHT = 2;
    /** How many of a record's stored blocks a recheck also starts from, besides its middle. */
    private static final int RECHECK_ORIGINS = 8;
    /** Two candidates are one place when either covers more than this share of the other's ground. */
    private static final double OVERLAP_SHARE = 0.5;
    /** How far out from the player a fence still surrounds them, and on how many sides. */
    private static final int FENCE_LOOK = 16;
    private static final int FENCED_SIDES = 3;

    /** MCA floor-system v2's native external registration path. Absent on older MCA. */
    private static final Method PROCESS_EXTERNAL = findProcessExternal();
    private static final Method VALIDATE_LEGACY = findMethod(Building.class,
            "validateBuilding", net.minecraft.world.level.Level.class, Set.class);

    /**
     * A building the world holds. {@code anchor} is the block it grew from; {@code columns} is the
     * ground it stands on, as packed columns, which is what two candidates compete for.
     */
    public record Candidate(String typeName, BuildingType type,
                            Map<ResourceLocation, List<BlockPos>> blocks,
                            BlockPos min, BlockPos max, boolean holdsOrigin,
                            BlockPos anchor, Set<Long> columns) {
        public Candidate {
            Map<ResourceLocation, List<BlockPos>> stable = new LinkedHashMap<>();
            blocks.forEach((id, positions) -> stable.put(id, List.copyOf(positions)));
            blocks = Map.copyOf(stable);
            columns = Set.copyOf(columns);
        }

        public List<BlockPos> positions() {
            return blocks.values().stream().flatMap(List::stream).toList();
        }
    }

    public enum Registration { CREATED, EXISTING, FAILED }

    /**
     * What a report found: a building, or else the type and problem of the site it nearly was.
     * {@code ties} is the best candidate and every other kind that fits the same ground, best
     * first, one per tier family; more than one means the player chooses.
     */
    public record Search(Optional<Candidate> candidate, @Nullable String typeName,
                         @Nullable SiteRequirements.Problem problem, List<Candidate> ties) {
        static final Search NOTHING = new Search(Optional.empty(), null, null, List.of());

        public Search {
            ties = List.copyOf(ties);
        }
    }

    /** Result of the explicit village-wide pass used by Blueprint's Refresh action. */
    public record RefreshResult(int created, int refreshed) {}

    /** Result of judging the records already on the books again. */
    public record RecheckResult(int kept, int removed) {}

    public record Removed(Village village, int buildingId) {}

    private record MatchedBlock(ResourceLocation id, Block block, BlockPos pos) {}
    private record Existing(Village village, Building building) {}
    /** {@code core}: the recipe group an anchor must belong to; null lets any part anchor. */
    private record TypeEntry(String name, BuildingType type, int reach, @Nullable ResourceLocation core) {}
    private record RoomBounds(Village village, Building room) {}

    /** One pass's rooms. Every seed asks about the same blocks, so each answer is kept for the pass. */
    private static final class Rooms {
        private final List<RoomBounds> bounds = new ArrayList<>();
        private final Map<Long, Boolean> inside = new HashMap<>();

        void add(Village village, Building room) {
            bounds.add(new RoomBounds(village, room));
        }
    }

    private OptionalBuildingRecognition() {}

    /**
     * Discover every complete open-air site in the loaded part of a village. MCA's ordinary
     * external-building action starts at the player's feet, which is useful while building but
     * cannot import a naturally generated village. Refresh deliberately performs the wider pass.
     */
    public static RefreshResult reconcileVillage(ServerLevel level, Village village) {
        if (level == null || village == null) {
            return new RefreshResult(0, 0);
        }
        removeLegacyMeetingRecords(village);
        // Records already on the books are judged first, so a wrong one neither survives the
        // scan nor stretches the village it is measured against.
        recheckVillage(level, village);
        List<TypeEntry> types = openAirTypes();
        if (types.isEmpty()) return new RefreshResult(0, 0);
        int maxReach = types.stream().mapToInt(TypeEntry::reach).max().orElse(DEFAULT_REACH);

        BlockPos center = new BlockPos(village.getCenter().getX(), village.getCenter().getY(),
                village.getCenter().getZ());
        var box = village.getBox();
        // Height comes from the rooms alone: an open-air record is what this scan is checking.
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Building building : McaBuildings.all(village)) {
            if (isRecheckable(level, village, building)) continue;
            minY = Math.min(minY, building.getPos0().getY());
            maxY = Math.max(maxY, building.getPos1().getY());
        }
        if (minY > maxY) {
            minY = box.minY();
            maxY = box.maxY();
        }
        int villageReach = Math.max(Math.max(center.getX() - box.minX(), box.maxX() - center.getX()),
                Math.max(center.getZ() - box.minZ(), box.maxZ() - center.getZ()));
        int radius = Math.min(160, Math.max(48, villageReach + Math.max(24, maxReach)));
        List<List<MatchedBlock>> visible = sweep(level, center, radius, types);
        Rooms rooms = registeredRooms(level, center);
        SiteRequirements.Scan scan = new SiteRequirements.Scan(level);
        Set<String> attempted = new HashSet<>();
        List<Candidate> found = new ArrayList<>();

        for (int i = 0; i < types.size(); i++) {
            TypeEntry entry = types.get(i);
            List<MatchedBlock> matches = visible.get(i);
            matches.sort(Comparator.comparingDouble(block -> block.pos().distSqr(center)));
            // Only saves work: a seed inside a site of this type already found finds it again.
            Set<Long> covered = new HashSet<>();
            for (MatchedBlock seed : matches) {
                if (covered.contains(seed.pos().asLong())) continue;
                if (entry.core() != null && !isCore(entry, seed)) continue;
                // Mineshafts and caves under the village are not its buildings.
                if (seed.pos().getY() < minY - VILLAGE_HEIGHT_MARGIN
                        || seed.pos().getY() > maxY + VILLAGE_HEIGHT_MARGIN) continue;
                Candidate candidate = assemble(level, scan, seed.pos(), entry, matches, rooms, null);
                if (candidate == null) continue;
                candidate.positions().forEach(pos -> covered.add(pos.asLong()));
                String key = candidate.typeName() + ':' + candidate.min().asLong() + ':' + candidate.max().asLong();
                if (attempted.add(key)) found.add(candidate);
            }
        }

        // A kept record keeps its kind: the player may have chosen it over a higher priority.
        found.removeIf(candidate -> !otherKindsOnGround(level, candidate).isEmpty());
        // Every kind competes at once for the same ground, so the order of the types never decides.
        found.sort(ranking(false));
        int created = 0;
        int refreshed = 0;
        for (Candidate candidate : SiteGeometry.select(found, OptionalBuildingRecognition::overlap)) {
            // A site the player removed stays removed until they work on it again.
            if (DismissedSites.dismissed(level, center(candidate))) continue;
            Registration registration = register(level, candidate);
            if (registration == Registration.CREATED) created++;
            else if (registration == Registration.EXISTING) refreshed++;
        }
        return new RefreshResult(created, refreshed);
    }

    /**
     * Judges every open-air record of the village again with the recognizer that made it. A record
     * that no longer qualifies is removed; one that does is brought up to what stands there now,
     * a higher or lower tier included. A record whose ground is not loaded is left alone: unknown is
     * not demolished.
     */
    public static RecheckResult recheckVillage(ServerLevel level, Village village) {
        if (level == null || village == null) return new RecheckResult(0, 0);
        List<TypeEntry> types = openAirTypes();
        if (types.isEmpty()) return new RecheckResult(0, 0);
        Rooms rooms = new Rooms();
        for (Building building : McaBuildings.all(village)) {
            if (!McaBuildings.isOpenAirRecord(level, village, building)) {
                rooms.add(village, building);
            }
        }
        SiteRequirements.Scan scan = new SiteRequirements.Scan(level);
        List<Integer> stale = new ArrayList<>();
        boolean changed = false;
        int kept = 0;
        for (Building building : List.copyOf(McaBuildings.all(village))) {
            if (!isRecheckable(level, village, building)) continue;
            List<TypeEntry> family = types.stream()
                    .filter(entry -> sameOpenAirSiteFamily(entry.name(), building.getType()))
                    .toList();
            // A type this pack no longer offers is the sanitizer's to retire, not ours to judge.
            if (family.isEmpty() || !loaded(level, building, family)) continue;
            Candidate again = recognizeAgain(level, scan, building, family, rooms);
            if (again == null) {
                stale.add(building.getId());
                continue;
            }
            kept++;
            if (!again.typeName().equals(building.getType())
                    || !again.min().equals(building.getPos0()) || !again.max().equals(building.getPos1())) {
                McaBuildings.putSynthetic(village, building.getId(), toNbt(building.getId(), again));
                changed = true;
            }
        }
        for (int id : stale) {
            changed |= McaBuildings.remove(village, id);
            Townstead.LOGGER.info("[OptionalBuilding] removed open-air building {} from village {}: "
                    + "it no longer qualifies", id, village.getId());
        }
        if (changed) {
            village.calculateDimensions();
            village.markDirty();
        }
        return new RecheckResult(kept, stale.size());
    }

    /**
     * Townstead's own open-air record: not an MCA room, not the room form of an optional type, and
     * not one of MCA's grouped sites.
     */
    public static boolean isOpenAirSite(ServerLevel level, Village village, Building building) {
        return building != null && !building.isStrictScan()
                && BuildingEnclosurePolicies.modeOf(building.getType()).allowsOpenAir()
                && McaBuildings.isOpenAirRecord(level, village, building);
    }

    private static boolean isRecheckable(ServerLevel level, Village village, Building building) {
        return isOpenAirSite(level, village, building)
                && !CatalogDataLoader.isActiveSupersededBuildingType(building.getType());
    }

    /** Every chunk the record's site could reach is loaded, so a failed match means it is gone. */
    private static boolean loaded(ServerLevel level, Building building, List<TypeEntry> family) {
        int reach = family.stream().mapToInt(TypeEntry::reach).max().orElse(DEFAULT_REACH) + 2;
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        for (int chunkX = (min.getX() - reach) >> 4; chunkX <= (max.getX() + reach) >> 4; chunkX++) {
            for (int chunkZ = (min.getZ() - reach) >> 4; chunkZ <= (max.getZ() + reach) >> 4; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    /**
     * The best tier of the record's family that still stands on its ground, seeded from where the
     * record says its blocks are, so the check starts where the building was found.
     */
    private static @Nullable Candidate recognizeAgain(ServerLevel level, SiteRequirements.Scan scan,
                                                      Building building, List<TypeEntry> family,
                                                      Rooms rooms) {
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        BlockPos center = building.getCenter();
        int halfExtent = Math.max(max.getX() - min.getX(), max.getZ() - min.getZ()) / 2 + 1;
        int maxReach = family.stream().mapToInt(TypeEntry::reach).max().orElse(DEFAULT_REACH);
        List<List<MatchedBlock>> visible = sweep(level, center, halfExtent + maxReach + DISCOVERY_RADIUS, family);

        List<BlockPos> origins = new ArrayList<>();
        origins.add(center);
        List<BlockPos> stored = building.getBlockPosStream().toList();
        int step = Math.max(1, stored.size() / RECHECK_ORIGINS);
        for (int i = 0; i < stored.size() && origins.size() <= RECHECK_ORIGINS; i += step) {
            origins.add(stored.get(i));
        }
        List<Candidate> found = new ArrayList<>();
        for (int i = 0; i < family.size(); i++) {
            for (BlockPos origin : origins) {
                Candidate candidate = assemble(level, scan, origin, family.get(i), visible.get(i), rooms, null);
                if (candidate != null && overlaps(candidate, min, max)) found.add(candidate);
            }
        }
        return found.stream().min(ranking(false)).orElse(null);
    }

    /**
     * The one order every path ranks candidates in. A report ranks the site the player stands in
     * first, whatever kind it is; after that the type's priority, then the larger recipe, so a
     * higher tier beats a lower one on the same ground.
     */
    private static Comparator<Candidate> ranking(boolean holdsOriginFirst) {
        Comparator<Candidate> byType = Comparator.<Candidate>comparingInt(c -> c.type().priority()).reversed()
                .thenComparingInt(c -> -c.type().getMinBlocks())
                .thenComparing(Candidate::typeName);
        return holdsOriginFirst
                ? Comparator.<Candidate, Boolean>comparing(c -> !c.holdsOrigin()).thenComparing(byType)
                : byType;
    }

    /** One place, so only one building: the same anchor, or mostly the same ground. */
    private static boolean overlap(Candidate first, Candidate second) {
        return first.anchor().equals(second.anchor())
                || SiteGeometry.overlapShare(first.columns(), second.columns()) > OVERLAP_SHARE;
    }

    /** Whether the block belongs to the recipe group the type's sites grow from. */
    private static boolean isCore(TypeEntry entry, MatchedBlock block) {
        return entry.type().getGroups(Map.of(block.id(), List.of(block.pos()))).containsKey(entry.core());
    }

    /**
     * The recipe group a type's sites grow from: the one its data names, else the rarest solid
     * group of its recipe, which is its centerpiece (a square's bell, a camp's fire). A type whose
     * site rules bound it and that names no core lets any of its parts start the search.
     */
    private static @Nullable ResourceLocation coreOf(String name, BuildingType type) {
        ResourceLocation named = SiteRequirements.core(name);
        Map<ResourceLocation, Integer> groups = type.getGroups();
        if (named != null) return groups.containsKey(named) ? named : null;
        if (!SiteRequirements.of(name).isEmpty()) return null;
        return groups.entrySet().stream()
                .filter(group -> !isLiquidGroup(group.getKey()))
                .min(Comparator.<Map.Entry<ResourceLocation, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(group -> group.getKey().toString()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static boolean isLiquidGroup(ResourceLocation group) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(group)
                && net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(group) instanceof LiquidBlock;
    }

    private static boolean overlaps(Candidate candidate, BlockPos min, BlockPos max) {
        return candidate.min().getX() <= max.getX() && candidate.max().getX() >= min.getX()
                && candidate.min().getZ() <= max.getZ() && candidate.max().getZ() >= min.getZ();
    }

    /** Every type that may stand in the open, best first: priority, then the larger recipe. */
    private static List<TypeEntry> openAirTypes() {
        List<TypeEntry> types = new ArrayList<>();
        for (Map.Entry<String, BuildingEnclosurePolicies.Mode> policy
                : BuildingEnclosurePolicies.snapshot().entrySet()) {
            if (!policy.getValue().allowsOpenAir()) continue;
            if (CatalogDataLoader.isActiveSupersededBuildingType(policy.getKey())) continue;
            BuildingType type = BuildingTypes.getInstance().getBuildingTypes().get(policy.getKey());
            if (type == null) continue;
            types.add(new TypeEntry(policy.getKey(), type, type.mergeRange() > 0 ? type.mergeRange() : DEFAULT_REACH,
                    coreOf(policy.getKey(), type)));
        }
        types.sort(Comparator.<TypeEntry>comparingInt(entry -> entry.type().priority()).reversed()
                .thenComparingInt(entry -> -entry.type().getMinBlocks())
                .thenComparing(TypeEntry::name));
        return types;
    }

    /** Best complete optional/open-air building around the report position. */
    public static Optional<Candidate> find(ServerLevel level, BlockPos origin) {
        return search(level, origin).candidate();
    }

    /**
     * As {@link #find}, and when nothing was found, why the enclosure the player stands by is not
     * one. A problem is only kept where the type's own blocks stand near it, so a house with a
     * missing door is left to MCA's room scan rather than reported as a broken fence.
     */
    public static Search search(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null) return Search.NOTHING;
        List<TypeEntry> types = openAirTypes();
        if (types.isEmpty()) return Search.NOTHING;
        int maxReach = types.stream().mapToInt(TypeEntry::reach).max().orElse(DEFAULT_REACH);

        List<List<MatchedBlock>> visible = sweep(level, origin, DISCOVERY_RADIUS + maxReach, types);
        Rooms rooms = registeredRooms(level, origin);
        SiteRequirements.Scan scan = new SiteRequirements.Scan(level);
        List<Candidate> matches = new ArrayList<>();
        SiteRequirements.Problem[] problem = new SiteRequirements.Problem[1];
        String[] problemType = new String[1];
        // An enclosure's problem is only worth saying to someone standing amid fences; anyone else
        // near a fence is adding something else, and MCA's own answer is the useful one.
        boolean amidFences = WalkEnclosure.fencedSides(scan, origin.getX(), origin.getY(), origin.getZ(),
                FENCE_LOOK) >= FENCED_SIDES;
        for (int i = 0; i < types.size(); i++) {
            TypeEntry entry = types.get(i);
            Candidate candidate = assemble(level, scan, origin, entry, visible.get(i), rooms, found -> {
                if (amidFences && problem[0] == null && nearOwnBlock(level, entry.type(), found.at())) {
                    problem[0] = found;
                    problemType[0] = entry.name();
                }
            });
            if (candidate != null) matches.add(candidate);
        }
        if (matches.isEmpty()) return new Search(Optional.empty(), problemType[0], problem[0], List.of());
        matches.sort(ranking(true));
        Candidate best = matches.get(0);
        List<Candidate> ties = new ArrayList<>();
        for (Candidate match : matches) {
            if (match != best && !overlap(best, match)) continue;
            // A higher tier of the same family is a better building, not a different one.
            if (ties.stream().anyMatch(tie -> sameOpenAirSiteFamily(tie.typeName(), match.typeName()))) continue;
            ties.add(match);
        }
        return new Search(Optional.of(best), null, null, ties);
    }

    /** The candidate of this type on the ground a report at {@code origin} finds, if it still stands. */
    public static Optional<Candidate> findType(ServerLevel level, BlockPos origin, String typeName) {
        return search(level, origin).ties().stream()
                .filter(candidate -> candidate.typeName().equals(typeName))
                .findFirst();
    }

    /**
     * Register a site that has just closed, but only into a village that already exists: MCA's
     * native external path founds a village when none is near, and a fence in the wild must not.
     */
    public static Registration registerInVillage(ServerLevel level, Candidate candidate) {
        if (level == null || candidate == null || candidate.positions().isEmpty()) return Registration.FAILED;
        if (VillageManager.get(level).findNearestVillage(registrationAnchor(candidate), Village.MERGE_MARGIN)
                .isEmpty()) {
            return Registration.FAILED;
        }
        // Ground a record of another kind holds keeps that kind; only the player changes it.
        if (!otherKindsOnGround(level, candidate).isEmpty()) return Registration.FAILED;
        return register(level, candidate);
    }

    /**
     * Register a site the player added or chose by hand. A record of another kind on the same
     * ground is replaced: the player has said what this place is.
     */
    public static Registration registerByHand(ServerLevel level, Candidate candidate) {
        if (level == null || candidate == null || candidate.positions().isEmpty()) return Registration.FAILED;
        for (Existing other : otherKindsOnGround(level, candidate)) {
            McaBuildings.remove(other.village(), other.building().getId());
            other.village().calculateDimensions();
            other.village().markDirty();
        }
        return register(level, candidate);
    }

    /** Open-air records of another tier family standing on most of the candidate's ground. */
    private static List<Existing> otherKindsOnGround(ServerLevel level, Candidate candidate) {
        Optional<Village> village = VillageManager.get(level)
                .findNearestVillage(center(candidate), Village.MERGE_MARGIN);
        if (village.isEmpty()) return List.of();
        List<Existing> out = new ArrayList<>();
        for (Building building : McaBuildings.all(village.get())) {
            if (sameOpenAirSiteFamily(candidate.typeName(), building.getType())) continue;
            if (!isOpenAirSite(level, village.get(), building)) continue;
            if (SiteGeometry.overlapShare(candidate.columns(), boxColumns(building)) > OVERLAP_SHARE) {
                out.add(new Existing(village.get(), building));
            }
        }
        return out;
    }

    private static Set<Long> boxColumns(Building building) {
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        Set<Long> columns = new HashSet<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) columns.add(SiteGeometry.column(x, z));
        }
        return columns;
    }

    /** How far the type's site may reach from where its walk starts. */
    public static int reach(String typeName) {
        BuildingType type = typeName == null ? null : BuildingTypes.getInstance().getBuildingTypes().get(typeName);
        return type != null && type.mergeRange() > 0 ? type.mergeRange() : DEFAULT_REACH;
    }

    private static boolean nearOwnBlock(ServerLevel level, BuildingType type, BlockPos at) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -NEAR_OWN_BLOCK; dx <= NEAR_OWN_BLOCK; dx++) {
            for (int dy = -NEAR_OWN_BLOCK; dy <= NEAR_OWN_BLOCK; dy++) {
                for (int dz = -NEAR_OWN_BLOCK; dz <= NEAR_OWN_BLOCK; dz++) {
                    cursor.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (level.isLoaded(cursor) && matchesBlock(type, level.getBlockState(cursor))) return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether MCA can recognise the same report as an ordinary room. Optional means room first,
     * outdoor second; this read-only probe prevents the fallback from stealing enclosed builds.
     */
    public static boolean roomCanHandle(VillageManager manager, BlockPos origin, String actionName) {
        //? if >=1.21 {
        try {
            ServerLevel level = managerLevel(manager);
            var scan = "ADD_ROOM".equals(actionName) || "SCAN_ROOM".equals(actionName)
                    ? com.aetherianartificer.townstead.compat.mca.McaRoomWorkflow.analyzeRoom(level, origin)
                    : com.aetherianartificer.townstead.compat.mca.McaRoomWorkflow.analyzeBuildingAddition(level, origin);
            return successful(scan.result());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Townstead.LOGGER.debug("[OptionalBuilding] MCA room probe failed: {}", ex.toString());
        }
        //?}

        // Pre-floor-system MCA exposes the old read-only Building.validateBuilding scan instead.
        if (VALIDATE_LEGACY != null) {
            try {
                Building probe = new Building(origin);
                return successful(VALIDATE_LEGACY.invoke(probe, managerLevel(manager), Set.of()));
            } catch (ReflectiveOperationException ex) {
                Townstead.LOGGER.debug("[OptionalBuilding] legacy room probe failed: {}", ex.toString());
            }
        }
        return false;
    }

    /** Register the candidate through MCA's native external path where available. */
    public static Registration register(ServerLevel level, Candidate candidate) {
        if (level == null || candidate == null || candidate.positions().isEmpty()) return Registration.FAILED;
        Existing existing = findExisting(level, candidate);
        if (existing != null) {
            replaceCanonical(level, existing, candidate);
            return Registration.EXISTING;
        }

        VillageManager manager = VillageManager.get(level);
        if (PROCESS_EXTERNAL != null) {
            try {
                // Let MCA own village creation/attachment and stable id allocation. Its
                // incremental addPOI path deliberately collapses an external building to a
                // point marker, though, so use it only to create the record and replace that
                // record immediately with Townstead's already-validated complete footprint.
                PROCESS_EXTERNAL.invoke(manager, registrationAnchor(candidate), candidate.type());
                existing = findExisting(level, candidate);
                if (existing != null) {
                    replaceCanonical(level, existing, candidate);
                    return Registration.CREATED;
                }
            } catch (ReflectiveOperationException ex) {
                Townstead.LOGGER.warn("[OptionalBuilding] native external registration failed for {}: {}",
                        candidate.typeName(), ex.toString());
            }
        }

        // Older MCA has no separate ExternalBuilding map. Store the same open-air record in its
        // unified building map; BuildingValidateOpenAirMixin owns its non-room validation there.
        Optional<Village> village = manager.findNearestVillage(center(candidate), Village.MERGE_MARGIN);
        if (village.isEmpty()) {
            Townstead.LOGGER.warn("[OptionalBuilding] cannot attach {}: no nearby village and MCA has no "
                    + "native external-building creator", candidate.typeName());
            return Registration.FAILED;
        }
        Village host = village.get();
        int id = syntheticId(host, candidate);
        McaBuildings.putSynthetic(host, id, toNbt(id, candidate));
        host.calculateDimensions();
        host.markDirty();
        return Registration.CREATED;
    }

    /**
     * Removes an optional/open-air building near the report position. MCA's native removal only
     * succeeds when the player's feet are literally inside an external building's tight furniture
     * bounds, which is rarely possible for a stand. Resolve the nearest saved optional building
     * within its declared interaction margin so an incomplete or partially dismantled site also
     * remains removable. An ordinary room containing the player always keeps MCA's native path.
     */
    public static Optional<Removed> remove(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null) return Optional.empty();

        Optional<Village> village = VillageManager.get(level).findNearestVillage(origin, 32);
        if (village.isEmpty()) return Optional.empty();
        for (Building building : McaBuildings.all(village.get())) {
            if (BuildingEnclosurePolicies.modeOf(building.getType()).allowsOpenAir()) continue;
            if (McaBuildingCompat.contains(level, village.get(), building, origin)) return Optional.empty();
        }
        Optional<Building> nearest = findNearby(village.get(), origin);
        if (nearest.isEmpty()) return Optional.empty();
        return Optional.of(removeExisting(level, new Existing(village.get(), nearest.get())));
    }

    /**
     * Client/server-safe lookup used both to expose MCA's removal control and to delete. The site
     * the player stands in wins, the smallest when several overlap there; only when they stand in
     * none does the nearest within its type's margin answer, so a pen never removes the farm beside it.
     */
    public static Optional<Building> findNearby(Village village, BlockPos origin) {
        if (village == null || origin == null) return Optional.empty();
        Building containing = null;
        long containingArea = Long.MAX_VALUE;
        Building nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Building building : McaBuildings.all(village)) {
            if (!BuildingEnclosurePolicies.modeOf(building.getType()).allowsOpenAir()) continue;
            if (CatalogDataLoader.isActiveSupersededBuildingType(building.getType())) continue;
            if (standsIn(building, origin)) {
                long area = (long) (building.getPos1().getX() - building.getPos0().getX() + 1)
                        * (building.getPos1().getZ() - building.getPos0().getZ() + 1);
                if (area < containingArea) {
                    containing = building;
                    containingArea = area;
                }
                continue;
            }
            BuildingType type = BuildingTypes.getInstance().getBuildingTypes().get(building.getType());
            int reach = type == null ? 2 : Math.max(1, type.getMargin());
            double distance = distanceToBoundsSqr(building, origin);
            if (distance <= (double) reach * reach && distance < nearestDistance) {
                nearest = building;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(containing != null ? containing : nearest);
    }

    /** Inside the site's box from above, and no more than a step or two off its height. */
    private static boolean standsIn(Building building, BlockPos pos) {
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()
                && pos.getY() >= min.getY() - STAND_IN_HEIGHT && pos.getY() <= max.getY() + STAND_IN_HEIGHT;
    }

    private static Removed removeExisting(ServerLevel level, Existing existing) {
        Village village = existing.village();
        int id = existing.building().getId();
        DismissedSites.dismiss(level, existing.building().getPos0(), existing.building().getPos1(),
                existing.building().getType());
        McaBuildings.remove(village, id);
        village.calculateDimensions();
        village.markDirty();
        return new Removed(village, id);
    }

    private static double distanceToBoundsSqr(Building building, BlockPos pos) {
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        int dx = pos.getX() < min.getX() ? min.getX() - pos.getX()
                : Math.max(0, pos.getX() - max.getX());
        int dy = pos.getY() < min.getY() ? min.getY() - pos.getY()
                : Math.max(0, pos.getY() - max.getY());
        int dz = pos.getZ() < min.getZ() ? min.getZ() - pos.getZ()
                : Math.max(0, pos.getZ() - max.getZ());
        return (double) dx * dx + (double) dy * dy + (double) dz * dz;
    }

    /**
     * One pass over the area for every candidate type. Sections whose palette holds nothing any
     * type wants are skipped whole, which is what makes an unlimited height affordable: the
     * terrain under a site and the air above it are never read block by block.
     */
    private static List<List<MatchedBlock>> sweep(
            ServerLevel level, BlockPos origin, int radius, List<TypeEntry> types) {
        List<List<MatchedBlock>> visible = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) visible.add(new ArrayList<>());
        Map<BlockState, int[]> wantedBy = new HashMap<>();
        java.util.function.Function<BlockState, int[]> wanted = state ->
                wantedBy.computeIfAbsent(state, key -> typesMatching(types, key));

        int minX = origin.getX() - radius, maxX = origin.getX() + radius;
        int minZ = origin.getZ() - radius, maxZ = origin.getZ() + radius;
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    if (section == null || section.hasOnlyAir()
                            || !section.maybeHas(state -> wanted.apply(state).length > 0)) continue;
                    int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
                    for (int x = Math.max(minX, chunkX << 4); x <= Math.min(maxX, (chunkX << 4) + 15); x++) {
                        for (int z = Math.max(minZ, chunkZ << 4); z <= Math.min(maxZ, (chunkZ << 4) + 15); z++) {
                            for (int y = 0; y < 16; y++) {
                                BlockState state = section.getBlockState(x & 15, y, z & 15);
                                if (state.isAir()) continue;
                                int[] owners = wanted.apply(state);
                                if (owners.length == 0) continue;
                                MatchedBlock matched = new MatchedBlock(
                                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                                        state.getBlock(), new BlockPos(x, baseY + y, z));
                                for (int owner : owners) visible.get(owner).add(matched);
                            }
                        }
                    }
                }
            }
        }
        return visible;
    }

    private static int[] typesMatching(List<TypeEntry> types, BlockState state) {
        List<Integer> owners = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            if (matchesBlock(types.get(i).type(), state)) owners.add(i);
        }
        return owners.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Whether MCA's recipe for the type counts this block. */
    public static boolean matchesBlock(BuildingType type, BlockState state) {
        // MCA 7.7 keeps tags separate from the direct map and resolves them from the
        // BlockState; that lookup also teaches getGroups() the concrete block -> tag
        // mapping needed by the completeness check below. Reading getBlockToGroup()
        // directly misses every tag-only block until MCA happens to cache it.
        return type.matchesBlock(state);
    }

    /** Rooms keep what is inside them; an open-air site never claims furniture through a wall. */
    private static Rooms registeredRooms(ServerLevel level, BlockPos origin) {
        Rooms rooms = new Rooms();
        Optional<Village> village = VillageManager.get(level)
                .findNearestVillage(origin, Math.max(Village.MERGE_MARGIN, Village.PLAYER_BORDER_MARGIN));
        if (village.isEmpty()) return rooms;
        for (Building building : McaBuildings.all(village.get())) {
            if (!McaBuildings.isOpenAirRecord(level, village.get(), building)) {
                rooms.add(village.get(), building);
            }
        }
        return rooms;
    }

    private static boolean insideRoom(ServerLevel level, Rooms rooms, BlockPos pos) {
        return rooms.inside.computeIfAbsent(pos.asLong(), ignored -> insideAnyRoom(level, rooms.bounds, pos));
    }

    private static boolean insideAnyRoom(ServerLevel level, List<RoomBounds> rooms, BlockPos pos) {
        for (RoomBounds entry : rooms) {
            BlockPos min = entry.room().getPos0();
            BlockPos max = entry.room().getPos1();
            if (pos.getX() < min.getX() || pos.getX() > max.getX()
                    || pos.getY() < min.getY() || pos.getY() > max.getY()
                    || pos.getZ() < min.getZ() || pos.getZ() > max.getZ()) continue;
            if (McaBuildingCompat.contains(level, entry.village(), entry.room(), pos)) return true;
        }
        return false;
    }

    private static Candidate assemble(ServerLevel level, SiteRequirements.Scan scan, BlockPos origin,
                                      TypeEntry entry, List<MatchedBlock> visible, Rooms rooms,
                                      java.util.function.Consumer<SiteRequirements.Problem> problems) {
        if (visible.isEmpty()) return null;
        BuildingType type = entry.type();

        // Discovery is intentionally generous so the player need not stand on a particular block,
        // but completeness is evaluated only inside this type's own grouping distance. Otherwise
        // two separate stands visible at opposite edges of the scan could satisfy one another's
        // requirements and be registered as a single phantom building.
        long discoverySquared = (long) DISCOVERY_RADIUS * DISCOVERY_RADIUS;
        BlockPos anchor = visible.stream()
                .filter(block -> horizontalDistSqr(block.pos(), origin) <= discoverySquared)
                .filter(block -> !insideRoom(level, rooms, block.pos()))
                .filter(block -> entry.core() == null || isCore(entry, block))
                .min(Comparator.<MatchedBlock, Boolean>comparing(block -> block.block() instanceof LiquidBlock)
                        .thenComparingDouble(block -> block.pos().distSqr(origin)))
                .map(MatchedBlock::pos).orElse(null);
        if (anchor == null) return null;
        // The site comes first where a type declares one: its cells, not a radius, decide which
        // ingredients belong. A fence on the far side of the village is not part of this paddock
        // however close the anchor happens to be.
        Set<BlockPos> siteCells = Set.of();
        if (!SiteRequirements.of(entry.name()).isEmpty()) {
            SiteRequirements.Evaluation site = SiteRequirements.evaluate(
                    scan.excluding(pos -> insideRoom(level, rooms, pos)), entry.name(), List.of(origin, anchor),
                    anchor, entry.reach());
            if (site.verdict() != SiteRequirements.Verdict.SATISFIED) {
                if (site.problem() != null && problems != null) problems.accept(site.problem());
                return null;
            }
            siteCells = site.cells();
        }
        Set<Long> footprint = SiteGeometry.columnsNear(
                siteCells.stream().map(pos -> SiteGeometry.pack(pos.getX(), pos.getY(), pos.getZ())).toList(),
                SiteRequirements.LINK);
        long reachSquared = (long) entry.reach() * entry.reach();
        // Parts belong to the site's own level, never to whatever is buried under it.
        int floor = siteCells.isEmpty() ? anchor.getY() - PARTS_BELOW_ANCHOR
                : siteCells.stream().mapToInt(BlockPos::getY).min().getAsInt() - PARTS_BELOW_SITE;
        int ceiling = (siteCells.isEmpty() ? anchor.getY()
                : siteCells.stream().mapToInt(BlockPos::getY).max().getAsInt()) + PARTS_ABOVE;

        List<MatchedBlock> kept = new ArrayList<>();
        Map<ResourceLocation, List<MatchedBlock>> liquids = new LinkedHashMap<>();
        for (MatchedBlock matched : visible) {
            if (matched.pos().getY() < floor || matched.pos().getY() > ceiling) continue;
            if (footprint.isEmpty()
                    ? horizontalDistSqr(matched.pos(), anchor) > reachSquared
                    : !footprint.contains(
                            SiteGeometry.column(matched.pos().getX(), matched.pos().getZ()))) continue;
            if (matched.block() instanceof LiquidBlock) {
                liquids.computeIfAbsent(matched.id(), ignored -> new ArrayList<>()).add(matched);
            } else if (!insideRoom(level, rooms, matched.pos())) {
                kept.add(matched);
            }
        }
        int liquidCap = Math.max(LIQUID_CAP, type.getMinBlocks());
        for (List<MatchedBlock> sameLiquid : liquids.values()) {
            sameLiquid.sort(Comparator.comparingDouble(block -> block.pos().distSqr(anchor)));
            kept.addAll(sameLiquid.subList(0, Math.min(liquidCap, sameLiquid.size())));
        }

        Building probe = new Building(anchor);
        Map<ResourceLocation, List<BlockPos>> blocks = new LinkedHashMap<>();
        for (MatchedBlock matched : kept) {
            probe.addBlock(matched.block(), matched.pos());
            blocks.computeIfAbsent(matched.id(), ignored -> new ArrayList<>()).add(matched.pos());
        }
        if (blocks.isEmpty() || !matchesRequirements(type, probe.getBlocks())) return null;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<BlockPos> extent = new ArrayList<>(siteCells);
        kept.forEach(matched -> extent.add(matched.pos()));
        for (BlockPos pos : extent) {
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
        }
        // The ground it competes for: its site, or else the ground right around its own solid parts.
        Set<Long> columns = !footprint.isEmpty() ? footprint : SiteGeometry.columnsNear(kept.stream()
                .filter(matched -> !(matched.block() instanceof LiquidBlock))
                .map(matched -> SiteGeometry.pack(matched.pos().getX(), matched.pos().getY(), matched.pos().getZ()))
                .toList(), SiteRequirements.LINK);
        return new Candidate(entry.name(), type, blocks,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), holds(siteCells, kept, origin),
                anchor, columns);
    }

    /**
     * Whether the site's footprint holds {@code origin}: the ground of a type that declares a
     * site, else the area its own solid parts span. Liquids are surroundings and never stretch it.
     */
    private static boolean holds(Set<BlockPos> siteCells, List<MatchedBlock> kept, BlockPos origin) {
        if (!siteCells.isEmpty()) {
            return siteCells.stream().anyMatch(pos -> pos.getX() == origin.getX() && pos.getZ() == origin.getZ());
        }
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (MatchedBlock matched : kept) {
            if (matched.block() instanceof LiquidBlock) continue;
            minX = Math.min(minX, matched.pos().getX()); maxX = Math.max(maxX, matched.pos().getX());
            minZ = Math.min(minZ, matched.pos().getZ()); maxZ = Math.max(maxZ, matched.pos().getZ());
        }
        return origin.getX() >= minX && origin.getX() <= maxX && origin.getZ() >= minZ && origin.getZ() <= maxZ;
    }

    private static long horizontalDistSqr(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static Existing findExisting(ServerLevel level, Candidate candidate) {
        Set<Long> positions = new HashSet<>();
        candidate.positions().forEach(pos -> positions.add(pos.asLong()));
        BlockPos candidateCenter = center(candidate);
        for (Village village : VillageManager.get(level)) {
            for (Building building : McaBuildings.all(village)) {
                // A landing that has grown into a wharf is the same place at a new tier.
                if (!sameOpenAirSiteFamily(candidate.typeName(), building.getType())) continue;
                if (!McaBuildings.isOpenAirRecord(level, village, building)) continue;
                BlockPos oldCenter = building.getCenter();
                if (SiteRequirements.isEnclosed(candidate.typeName())) {
                    // Neighbouring pens share fences, so a shared block does not make them one
                    // pen. The same pen, grown or not, still has its old middle in its new box.
                    if (inside(candidate, oldCenter)) return new Existing(village, building);
                    continue;
                }
                if (building.getBlockPosStream().anyMatch(pos -> positions.contains(pos.asLong()))) {
                    return new Existing(village, building);
                }
                // Repair malformed records produced before synthetic BlockPos NBT used MCA's
                // codec shape. They retain their type and center but have no decoded blocks.
                if (inside(candidate, oldCenter) || building.containsPos(candidateCenter)) {
                    return new Existing(village, building);
                }
            }
        }
        return null;
    }

    private static boolean sameOpenAirSiteFamily(String first, String second) {
        if (BuildingCandidatePolicy.sameTierFamily(first, second)) return true;
        if (first == null || second == null) return false;
        return ("stable".equals(first) && "pen".equals(second))
                || ("pen".equals(first) && "stable".equals(second));
    }

    /**
     * The first village import used biome-specific meeting ids and one overly broad surface tag.
     * Refresh removes those obsolete synthetic records before recognizing the corrected conceptual
     * types, so affected worlds heal without asking players to edit MCA's save data.
     */
    private static void removeLegacyMeetingRecords(Village village) {
        List<Integer> stale = McaBuildings.all(village).stream()
                .filter(building -> building.getType() != null
                        && building.getType().startsWith("village_meeting_"))
                .map(Building::getId)
                .toList();
        if (stale.isEmpty()) return;
        boolean changed = false;
        for (int id : stale) changed |= McaBuildings.remove(village, id);
        if (changed) {
            village.calculateDimensions();
            village.markDirty();
        }
    }

    private static boolean inside(Candidate candidate, BlockPos pos) {
        return pos.getX() >= candidate.min().getX() && pos.getX() <= candidate.max().getX()
                && pos.getY() >= candidate.min().getY() && pos.getY() <= candidate.max().getY()
                && pos.getZ() >= candidate.min().getZ() && pos.getZ() <= candidate.max().getZ();
    }

    /**
     * Native external POI insertion is ideal for MCA-driven grouped types but is the wrong final
     * representation for an already-complete optional building: it retains every nearby matching
     * tag member and reduces geometry to the average POI point. Replace it atomically with the
     * exact candidate so map placement, containment, and later validation all share one footprint.
     */
    private static void replaceCanonical(ServerLevel level, Existing existing, Candidate candidate) {
        int id = existing.building().getId();
        Building replacement = McaBuildings.putSynthetic(existing.village(), id, toNbt(id, candidate));
        if (replacement == null) return;
        existing.village().calculateDimensions();
        existing.village().markDirty();
    }

    /** MCA 7.6 and floor-system MCA expose the same group maps, but not the same matcher method. */
    private static boolean matchesRequirements(BuildingType type,
            Map<ResourceLocation, List<BlockPos>> concreteBlocks) {
        Map<ResourceLocation, List<BlockPos>> actual = type.getGroups(concreteBlocks);
        for (Map.Entry<ResourceLocation, Integer> required : type.getGroups().entrySet()) {
            List<BlockPos> positions = actual.get(required.getKey());
            if (positions == null || positions.size() < required.getValue()) return false;
        }
        return true;
    }

    private static int syntheticId(Village village, Candidate candidate) {
        int hash = 31 * candidate.typeName().hashCode() + candidate.min().hashCode();
        int id = hash | Integer.MIN_VALUE;
        while (McaBuildings.byId(village, id) != null) id = (id - 1) | Integer.MIN_VALUE;
        return id;
    }

    private static CompoundTag toNbt(int id, Candidate candidate) {
        CompoundTag tag = new CompoundTag();
        BlockPos center = center(candidate);
        tag.putInt("id", id);
        tag.putInt("size", candidate.positions().size());
        tag.putInt("pos0X", candidate.min().getX());
        tag.putInt("pos0Y", candidate.min().getY());
        tag.putInt("pos0Z", candidate.min().getZ());
        tag.putInt("pos1X", candidate.max().getX());
        tag.putInt("pos1Y", candidate.max().getY());
        tag.putInt("pos1Z", candidate.max().getZ());
        tag.putInt("posX", center.getX());
        tag.putInt("posY", center.getY());
        tag.putInt("posZ", center.getZ());
        tag.putBoolean("isTypeForced", true);
        tag.putBoolean("strictScan", false);
        tag.putString("type", candidate.typeName());
        McaBuildingNbt.putDetachedDefaults(tag);
        CompoundTag blocks = new CompoundTag();
        candidate.blocks().forEach((blockId, positions) -> {
            ListTag list = new ListTag();
            for (BlockPos pos : positions) {
                list.add(McaBuildingNbt.blockPos(pos));
            }
            blocks.put(blockId.toString(), list);
        });
        tag.put("blocks2", blocks);
        return tag;
    }

    private static BlockPos center(Candidate candidate) {
        return new BlockPos(
                (candidate.min().getX() + candidate.max().getX()) / 2,
                (candidate.min().getY() + candidate.max().getY()) / 2,
                (candidate.min().getZ() + candidate.max().getZ()) / 2);
    }

    private static BlockPos registrationAnchor(Candidate candidate) {
        BlockPos center = center(candidate);
        return candidate.positions().stream()
                .min(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .orElse(center);
    }

    private static boolean successful(Object result) {
        if (!(result instanceof Enum<?> value)) return false;
        return "SUCCESS".equals(value.name()) || "IDENTICAL".equals(value.name());
    }

    private static Method findProcessExternal() {
        return findMethod(VillageManager.class, "processExternalBuilding", BlockPos.class, BuildingType.class);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /** Resolve VillageManager.world without a hard dependency on either MCA generation's field API. */
    private static ServerLevel managerLevel(VillageManager manager) throws ReflectiveOperationException {
        var field = VillageManager.class.getDeclaredField("world");
        field.setAccessible(true);
        return (ServerLevel) field.get(manager);
    }
}
