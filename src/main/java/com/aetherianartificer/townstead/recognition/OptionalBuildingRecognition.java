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
 * a type that describes no site falls back to {@code mergeRange}. Height is never limited, so a
 * lantern on a mast belongs to the site below it however tall the mast is.</p>
 */
public final class OptionalBuildingRecognition {
    /** How far from the player the nearest block of a building may be. */
    private static final int DISCOVERY_RADIUS = 16;
    private static final int DEFAULT_REACH = 12;
    /** Liquids are surroundings rather than furniture; only the nearest few are recorded. */
    private static final int LIQUID_CAP = 64;

    /** MCA floor-system v2's native external registration path. Absent on older MCA. */
    private static final Method PROCESS_EXTERNAL = findProcessExternal();
    private static final Method VALIDATE_LEGACY = findMethod(Building.class,
            "validateBuilding", net.minecraft.world.level.Level.class, Set.class);

    public record Candidate(String typeName, BuildingType type,
                            Map<ResourceLocation, List<BlockPos>> blocks,
                            BlockPos min, BlockPos max) {
        public Candidate {
            Map<ResourceLocation, List<BlockPos>> stable = new LinkedHashMap<>();
            blocks.forEach((id, positions) -> stable.put(id, List.copyOf(positions)));
            blocks = Map.copyOf(stable);
        }

        public List<BlockPos> positions() {
            return blocks.values().stream().flatMap(List::stream).toList();
        }
    }

    public enum Registration { CREATED, EXISTING, FAILED }

    /** Result of the explicit village-wide pass used by Blueprint's Refresh action. */
    public record RefreshResult(int created, int refreshed) {}

    public record Removed(Village village, int buildingId) {}

    private record MatchedBlock(ResourceLocation id, Block block, BlockPos pos) {}
    private record Existing(Village village, Building building) {}
    private record TypeEntry(String name, BuildingType type, int reach) {}
    private record RoomBounds(Village village, Building room) {}

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
        if (BuildingEnclosurePolicies.snapshot().isEmpty()) return new RefreshResult(0, 0);

        List<TypeEntry> types = new ArrayList<>();
        int maxReach = 0;
        for (Map.Entry<String, BuildingEnclosurePolicies.Mode> policy
                : BuildingEnclosurePolicies.snapshot().entrySet()) {
            if (!policy.getValue().allowsOpenAir()) continue;
            if (CatalogDataLoader.isActiveSupersededBuildingType(policy.getKey())) continue;
            BuildingType type = BuildingTypes.getInstance().getBuildingTypes().get(policy.getKey());
            if (type == null) continue;
            int reach = type.mergeRange() > 0 ? type.mergeRange() : DEFAULT_REACH;
            types.add(new TypeEntry(policy.getKey(), type, reach));
            maxReach = Math.max(maxReach, reach);
        }
        if (types.isEmpty()) return new RefreshResult(0, 0);
        types.sort(Comparator.<TypeEntry>comparingInt(entry -> entry.type().priority()).reversed()
                .thenComparingInt(entry -> -entry.type().getMinBlocks())
                .thenComparing(TypeEntry::name));

        BlockPos center = new BlockPos(village.getCenter().getX(), village.getCenter().getY(),
                village.getCenter().getZ());
        var box = village.getBox();
        int villageReach = Math.max(Math.max(center.getX() - box.minX(), box.maxX() - center.getX()),
                Math.max(center.getZ() - box.minZ(), box.maxZ() - center.getZ()));
        int radius = Math.min(160, Math.max(48, villageReach + Math.max(24, maxReach)));
        List<List<MatchedBlock>> visible = sweep(level, center, radius, types);
        List<RoomBounds> rooms = registeredRooms(level, center);
        Set<String> attempted = new HashSet<>();
        Set<Long> claimed = new HashSet<>();
        int created = 0;
        int refreshed = 0;

        for (int i = 0; i < types.size(); i++) {
            TypeEntry entry = types.get(i);
            List<MatchedBlock> matches = visible.get(i);
            matches.sort(Comparator.comparingDouble(block -> block.pos().distSqr(center)));
            for (MatchedBlock seed : matches) {
                if (claimed.contains(seed.pos().asLong())) continue;
                Candidate candidate = assemble(level, seed.pos(), entry, matches, rooms);
                if (candidate == null) continue;
                String key = candidate.typeName() + ':' + candidate.min().asLong() + ':' + candidate.max().asLong();
                if (!attempted.add(key)) continue;
                if (candidate.positions().stream().anyMatch(pos -> claimed.contains(pos.asLong()))) continue;
                Registration registration = register(level, candidate);
                if (registration == Registration.FAILED) continue;
                candidate.positions().forEach(pos -> claimed.add(pos.asLong()));
                if (registration == Registration.CREATED) created++;
                else refreshed++;
            }
        }
        return new RefreshResult(created, refreshed);
    }

    /** Best complete optional/open-air building around the report position. */
    public static Optional<Candidate> find(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null || BuildingEnclosurePolicies.snapshot().isEmpty()) {
            return Optional.empty();
        }

        List<TypeEntry> types = new ArrayList<>();
        int maxReach = 0;
        for (Map.Entry<String, BuildingEnclosurePolicies.Mode> policy
                : BuildingEnclosurePolicies.snapshot().entrySet()) {
            if (!policy.getValue().allowsOpenAir()) continue;
            if (CatalogDataLoader.isActiveSupersededBuildingType(policy.getKey())) continue;
            BuildingType type = BuildingTypes.getInstance().getBuildingTypes().get(policy.getKey());
            if (type == null) continue;
            int reach = type.mergeRange() > 0 ? type.mergeRange() : DEFAULT_REACH;
            types.add(new TypeEntry(policy.getKey(), type, reach));
            maxReach = Math.max(maxReach, reach);
        }
        if (types.isEmpty()) return Optional.empty();

        List<List<MatchedBlock>> visible = sweep(level, origin, DISCOVERY_RADIUS + maxReach, types);
        List<RoomBounds> rooms = registeredRooms(level, origin);
        List<Candidate> matches = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            Candidate candidate = assemble(level, origin, types.get(i), visible.get(i), rooms);
            if (candidate != null) matches.add(candidate);
        }
        matches.sort(Comparator
                .<Candidate>comparingInt(c -> c.type().priority()).reversed()
                .thenComparingInt(c -> -c.type().getMinBlocks())
                .thenComparing(Candidate::typeName));
        return matches.stream().findFirst();
    }

    /**
     * Whether MCA can recognise the same report as an ordinary room. Optional means room first,
     * outdoor second; this read-only probe prevents the fallback from stealing enclosed builds.
     */
    public static boolean roomCanHandle(VillageManager manager, BlockPos origin, String actionName) {
        //? if >=1.21 {
        try {
            ServerLevel level = managerLevel(manager);
            var scan = "ADD_ROOM".equals(actionName)
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

    /** Client/server-safe lookup used both to expose MCA's removal control and to delete. */
    public static Optional<Building> findNearby(Village village, BlockPos origin) {
        if (village == null || origin == null) return Optional.empty();
        Building nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Building building : McaBuildings.all(village)) {
            if (!BuildingEnclosurePolicies.modeOf(building.getType()).allowsOpenAir()) continue;
            if (CatalogDataLoader.isActiveSupersededBuildingType(building.getType())) continue;
            BuildingType type = BuildingTypes.getInstance().getBuildingTypes().get(building.getType());
            int reach = type == null ? 2 : Math.max(1, type.getMargin());
            double distance = distanceToBoundsSqr(building, origin);
            if (distance <= (double) reach * reach && distance < nearestDistance) {
                nearest = building;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static Removed removeExisting(ServerLevel level, Existing existing) {
        Village village = existing.village();
        int id = existing.building().getId();
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
        // MCA 7.7 keeps tags separate from the direct map and resolves them from the
        // BlockState; that lookup also teaches getGroups() the concrete block -> tag
        // mapping needed by the completeness check below. MCA 7.6 eagerly expands tags
        // into getBlockToGroup(), so retain that generation's equivalent lookup.
        //? if <1.21 {
        /*ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        *///?}
        List<Integer> owners = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            //? if >=1.21 {
            if (types.get(i).type().matchesBlock(state)) owners.add(i);
            //?} else {
            /*if (types.get(i).type().getBlockToGroup().containsKey(id)) owners.add(i);
            *///?}
        }
        return owners.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Rooms keep what is inside them; an open-air site never claims furniture through a wall. */
    private static List<RoomBounds> registeredRooms(ServerLevel level, BlockPos origin) {
        List<RoomBounds> rooms = new ArrayList<>();
        Optional<Village> village = VillageManager.get(level)
                .findNearestVillage(origin, Math.max(Village.MERGE_MARGIN, Village.PLAYER_BORDER_MARGIN));
        if (village.isEmpty()) return rooms;
        for (Building building : McaBuildings.all(village.get())) {
            if (!McaBuildings.isOpenAirRecord(level, village.get(), building)) {
                rooms.add(new RoomBounds(village.get(), building));
            }
        }
        return rooms;
    }

    private static boolean insideRoom(ServerLevel level, List<RoomBounds> rooms, BlockPos pos) {
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

    private static Candidate assemble(ServerLevel level, BlockPos origin, TypeEntry entry,
                                      List<MatchedBlock> visible, List<RoomBounds> rooms) {
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
                    level, entry.name(), List.of(origin, anchor), anchor, entry.reach());
            if (site.verdict() != SiteRequirements.Verdict.SATISFIED) return null;
            siteCells = site.cells();
        }
        Set<Long> footprint = SiteGeometry.columnsNear(
                siteCells.stream().map(pos -> SiteGeometry.pack(pos.getX(), pos.getY(), pos.getZ())).toList(),
                SiteRequirements.LINK);
        long reachSquared = (long) entry.reach() * entry.reach();

        List<MatchedBlock> kept = new ArrayList<>();
        Map<ResourceLocation, List<MatchedBlock>> liquids = new LinkedHashMap<>();
        for (MatchedBlock matched : visible) {
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
        return new Candidate(entry.name(), type, blocks,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
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
                if (building.getBlockPosStream().anyMatch(pos -> positions.contains(pos.asLong()))) {
                    return new Existing(village, building);
                }
                // Repair malformed records produced before synthetic BlockPos NBT used MCA's
                // codec shape. They retain their type and center but have no decoded blocks.
                BlockPos oldCenter = building.getCenter();
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
