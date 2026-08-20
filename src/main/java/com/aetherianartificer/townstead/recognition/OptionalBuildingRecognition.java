package com.aetherianartificer.townstead.recognition;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.mca.McaBuildingNbt;
import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import com.aetherianartificer.townstead.village.TownsteadVillageSavedData;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
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
 */
public final class OptionalBuildingRecognition {
    private static final int HORIZONTAL_RADIUS = 16;
    private static final int VERTICAL_RADIUS = 6;

    /** MCA floor-system v2's native external registration path. Absent on older MCA. */
    private static final Method PROCESS_EXTERNAL = findProcessExternal();
    private static final Method ANALYZE_BUILDING = findMethod(VillageManager.class,
            "analyzeBuildingAddition", BlockPos.class);
    private static final Method ANALYZE_ROOM = findMethod(VillageManager.class,
            "analyzeRoom", BlockPos.class);
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

    public record Removed(Village village, int buildingId) {}

    private record MatchedBlock(ResourceLocation id, Block block, BlockPos pos) {}
    private record Existing(Village village, Building building) {}

    private OptionalBuildingRecognition() {}

    /** Best complete optional/open-air building around the report position. */
    public static Optional<Candidate> find(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null || BuildingEnclosurePolicies.snapshot().isEmpty()) {
            return Optional.empty();
        }

        List<Candidate> matches = new ArrayList<>();
        for (Map.Entry<String, BuildingEnclosurePolicies.Mode> policy
                : BuildingEnclosurePolicies.snapshot().entrySet()) {
            if (!policy.getValue().allowsOpenAir()) continue;
            if (CatalogDataLoader.isActiveSupersededBuildingType(policy.getKey())) continue;
            BuildingType type = BuildingTypes.getInstance().getBuildingTypes().get(policy.getKey());
            if (type == null) continue;
            Candidate candidate = collect(level, origin, policy.getKey(), type);
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
        Method analyzer = "ADD_ROOM".equals(actionName) ? ANALYZE_ROOM : ANALYZE_BUILDING;
        if (analyzer != null) {
            try {
                Object scan = analyzer.invoke(manager, origin);
                Method result = scan.getClass().getMethod("result");
                return successful(result.invoke(scan));
            } catch (ReflectiveOperationException ex) {
                Townstead.LOGGER.debug("[OptionalBuilding] MCA room probe failed: {}", ex.toString());
            }
        }

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
        storeOverlay(level, host, id, candidate);
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
        village.removeBuilding(id);
        TownsteadVillageSavedData.get(level.getServer()).removeBuilding(level, village.getId(), id);
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

    private static Candidate collect(ServerLevel level, BlockPos origin, String typeName, BuildingType type) {
        List<MatchedBlock> visible = new ArrayList<>();

        for (BlockPos mutable : BlockPos.betweenClosed(
                origin.offset(-HORIZONTAL_RADIUS, -VERTICAL_RADIUS, -HORIZONTAL_RADIUS),
                origin.offset(HORIZONTAL_RADIUS, VERTICAL_RADIUS, HORIZONTAL_RADIUS))) {
            if (!level.hasChunkAt(mutable)) continue;
            var state = level.getBlockState(mutable);
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
            // MCA 7.7 keeps tags separate from the direct map and resolves them from the
            // BlockState; that lookup also teaches getGroups() the concrete block -> tag
            // mapping needed by the completeness check below. MCA 7.6 eagerly expands tags
            // into getBlockToGroup(), so retain that generation's equivalent lookup.
            //? if >=1.21 {
            if (!type.matchesBlock(state)) continue;
            //?} else {
            /*if (!type.getBlockToGroup().containsKey(id)) continue;
            *///?}
            BlockPos pos = mutable.immutable();
            visible.add(new MatchedBlock(id, state.getBlock(), pos));
        }
        if (visible.isEmpty()) return null;

        // Discovery is intentionally generous so the player need not stand on a particular block,
        // but completeness is evaluated only inside this type's own grouping distance. Otherwise
        // two separate stands visible at opposite edges of the scan could satisfy one another's
        // requirements and be registered as a single phantom building.
        BlockPos anchor = visible.stream()
                .min(Comparator.comparingDouble(block -> block.pos().distSqr(origin)))
                .orElseThrow().pos();
        int groupRadius = type.mergeRange() > 0 ? type.mergeRange() : 12;
        double groupRadiusSquared = (double) groupRadius * groupRadius;

        Building probe = new Building(anchor, false);
        Map<ResourceLocation, List<BlockPos>> blocks = new LinkedHashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (MatchedBlock matched : visible) {
            BlockPos pos = matched.pos();
            if (pos.distSqr(anchor) > groupRadiusSquared) continue;
            probe.addBlock(matched.block(), pos);
            blocks.computeIfAbsent(matched.id(), ignored -> new ArrayList<>()).add(pos);
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
        }
        if (blocks.isEmpty() || !matchesRequirements(type, probe.getBlocks())) return null;
        return new Candidate(typeName, type, blocks,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private static Existing findExisting(ServerLevel level, Candidate candidate) {
        Set<Long> positions = new HashSet<>();
        candidate.positions().forEach(pos -> positions.add(pos.asLong()));
        BlockPos candidateCenter = center(candidate);
        for (Village village : VillageManager.get(level)) {
            for (Building building : McaBuildings.all(village)) {
                if (!candidate.typeName().equals(building.getType())) continue;
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
        storeOverlay(level, existing.village(), id, candidate);
        //? if >=1.21 {
        replacement.setInheritanceEnabled(existing.building().isInheritanceEnabled());
        //?}
        existing.village().calculateDimensions();
        existing.village().markDirty();
    }

    private static void storeOverlay(
            ServerLevel level, Village village, int id, Candidate candidate) {
        Map<String, long[]> packed = new LinkedHashMap<>();
        candidate.blocks().forEach((blockId, positions) -> {
            long[] values = new long[positions.size()];
            for (int i = 0; i < positions.size(); i++) values[i] = positions.get(i).asLong();
            packed.put(blockId.toString(), values);
        });
        TownsteadVillageSavedData.get(level.getServer()).putBuilding(
                level, village.getId(), id,
                new TownsteadVillageSavedData.BuildingOverlay(
                        "optional", candidate.typeName(),
                        new int[] {candidate.min().getX(), candidate.min().getY(), candidate.min().getZ(),
                                candidate.max().getX(), candidate.max().getY(), candidate.max().getZ()},
                        packed));
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
