package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.naming.Naming;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Places a native Minecraft village and initializes Townstead's authored identity around it. */
public final class DebugVillageSpawner {
    public static final int VILLAGE_RADIUS = 160;

    public static final ResourceLocation PLAINS = ResourceLocation.tryParse("minecraft:village_plains");
    public static final ResourceLocation DESERT = ResourceLocation.tryParse("minecraft:village_desert");
    public static final ResourceLocation SAVANNA = ResourceLocation.tryParse("minecraft:village_savanna");
    public static final ResourceLocation SNOWY = ResourceLocation.tryParse("minecraft:village_snowy");
    public static final ResourceLocation TAIGA = ResourceLocation.tryParse("minecraft:village_taiga");

    private DebugVillageSpawner() {}

    public static Result spawn(CommandSourceStack source, FoundingProfileDefinition profile,
                               String requestedStructure, int residentCount) {
        if (source == null || profile == null || residentCount < 1) {
            return Result.failed("invalid_request", null);
        }
        ServerLevel level = source.getLevel();
        BlockPos anchor = BlockPos.containing(source.getPosition());
        VillageManager villages = VillageManager.get(level);

        Village blocking = villages.findNearestVillage(anchor, VILLAGE_RADIUS).orElse(null);
        if (blocking != null) return new Result(false, "near_village", null, blocking, 0, 0, null);
        AABB area = new AABB(anchor).inflate(VILLAGE_RADIUS, 64.0D, VILLAGE_RADIUS);
        Set<UUID> existingResidents = level.getEntitiesOfClass(Villager.class, area).stream()
                .map(Villager::getUUID)
                .collect(Collectors.toSet());
        refreshPoiData(level, anchor);
        Set<BlockPos> existingHomes = homes(level, anchor).collect(Collectors.toSet());

        ResourceLocation structureId = resolveStructure(level, anchor, requestedStructure);
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder.Reference<Structure> structure = registry
                .getHolder(ResourceKey.create(Registries.STRUCTURE, structureId)).orElse(null);
        if (structure == null) return Result.failed("unknown_structure", structureId);

        try {
            PlaceCommand.placeStructure(source, structure, anchor);
        } catch (CommandSyntaxException | RuntimeException exception) {
            return Result.failed("placement_failed", structureId);
        }
        // /place writes the blocks synchronously, but its POI sections are not guaranteed to be
        // rebuilt before this command continues. MCA cannot discover houses from beds it cannot
        // see yet, so make the loaded structure chunks consistent before taking the after-snapshot.
        refreshPoiData(level, anchor);

        // Native village structures may carry vanilla residents. The debug command's population
        // number is authoritative, so replace only residents created by this placement. Nearby
        // pre-existing villagers are deliberately preserved.
        for (Villager generated : level.getEntitiesOfClass(Villager.class, area)) {
            if (existingResidents.contains(generated.getUUID())) continue;
            if (generated instanceof VillagerEntityMCA mca) mca.getResidency().leaveHome();
            generated.discard();
        }

        // MCA normally discovers these homes over subsequent villager-AI ticks. Debug founding is
        // intentionally immediate, so ask it to scan the native structure's registered home POIs now.
        List<BlockPos> homes = homes(level, anchor)
                .filter(home -> !existingHomes.contains(home))
                .map(BlockPos::immutable)
                .toList();
        int recognized = 0;
        for (BlockPos home : homes) {
            if (villages.processBuilding(home) == Building.validationResult.SUCCESS) recognized++;
        }

        Village village = villages.findNearestVillage(anchor, VILLAGE_RADIUS).orElse(null);
        if (village == null || homes.isEmpty()) {
            return new Result(false, "mca_unrecognized", structureId, null, 0, 0, null);
        }

        FoundingProfileApplier.Result first = FoundingProfileApplier.apply(level, village, profile, anchor);
        if (!first.applied()) {
            return new Result(false, "profile_" + first.reason(), structureId, village,
                    McaBuildings.all(village).size(), 0, first);
        }

        int spawned = spawnResidents(level, profile, homes, residentCount);

        // Reapplying is idempotent and now has residents available to fill government offices.
        FoundingProfileApplier.Result founded = FoundingProfileApplier.apply(level, village, profile, anchor);
        int buildingCount = McaBuildings.all(village).size();
        return new Result(founded.applied(), founded.applied() ? "spawned" : "profile_" + founded.reason(),
                structureId, village, Math.max(buildingCount, recognized), spawned, founded);
    }

    /** Explicit repair path for a debug village that was placed before its profile settled. */
    public static RepopulationResult repopulate(CommandSourceStack source,
                                                FoundingProfileDefinition profile,
                                                int residentCount) {
        if (source == null || profile == null || residentCount < 1) {
            return RepopulationResult.failed("invalid_request");
        }
        ServerLevel level = source.getLevel();
        BlockPos anchor = BlockPos.containing(source.getPosition());
        Village village = VillageManager.get(level)
                .findNearestVillage(anchor, Village.MERGE_MARGIN).orElse(null);
        if (village == null) return RepopulationResult.failed("no_village");

        BlockPos center = new BlockPos(village.getCenter());
        refreshPoiData(level, center);
        List<BlockPos> homes = homes(level, center).map(BlockPos::immutable).toList();
        if (homes.isEmpty()) return RepopulationResult.failed("mca_unrecognized");

        Set<UUID> residentIds = village.getResidentsUUIDs().toList().stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        AABB area = new AABB(center).inflate(VILLAGE_RADIUS, 64.0D, VILLAGE_RADIUS);
        int removed = 0;
        for (VillagerEntityMCA resident : level.getEntitiesOfClass(VillagerEntityMCA.class, area)) {
            if (!residentIds.contains(resident.getUUID())) continue;
            resident.getResidency().leaveHome();
            resident.discard();
            removed++;
        }

        FoundingProfileApplier.Result first = FoundingProfileApplier.apply(level, village, profile, center);
        if (!first.applied()) {
            return new RepopulationResult(false, "profile_" + first.reason(), village,
                    removed, 0, first);
        }
        int spawned = spawnResidents(level, profile, homes, residentCount);
        FoundingProfileApplier.Result founded = FoundingProfileApplier.apply(level, village, profile, center);
        return new RepopulationResult(founded.applied(),
                founded.applied() ? "repopulated" : "profile_" + founded.reason(),
                village, removed, spawned, founded);
    }

    /** How far past the buildings a clear reaches, for paths, lamps and farm plots, and how high above them. */
    public static final int CLEAR_MARGIN = 8, CLEAR_HEADROOM = 12, MAX_CLEAR_SPAN = 320;

    /**
     * Removes the village the source stands in: its residents, then MCA's village record. With {@code clear},
     * also sets the village area to air, from the lowest building floor up, and removes loose items, frames,
     * armor stands, golems and villagers there. Players are never touched.
     */
    public static RemovalResult remove(CommandSourceStack source, boolean clear) {
        if (source == null) return RemovalResult.failed("invalid_request");
        ServerLevel level = source.getLevel();
        BlockPos anchor = BlockPos.containing(source.getPosition());
        VillageManager villages = VillageManager.get(level);
        // Same reach as the spawn check, so remove always finds the village that blocks a spawn here.
        Village village = villages.findNearestVillage(anchor, Village.MERGE_MARGIN)
                .or(() -> villages.findNearestVillage(anchor, VILLAGE_RADIUS)).orElse(null);
        if (village == null) return RemovalResult.failed("no_village");
        int id = village.getId();
        String name = village.getName();
        net.minecraft.world.level.levelgen.structure.BoundingBox box = village.getBox();
        // A village with no buildings at all keeps MCA's placeholder box at the world origin. Never clear that.
        boolean located = located(village);

        int minX = box.minX() - CLEAR_MARGIN, maxX = box.maxX() + CLEAR_MARGIN;
        int minZ = box.minZ() - CLEAR_MARGIN, maxZ = box.maxZ() + CLEAR_MARGIN;
        int minY = Math.max(level.getMinBuildHeight(), box.minY());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, box.maxY() + CLEAR_HEADROOM);
        boolean fits = maxX - minX <= MAX_CLEAR_SPAN && maxZ - minZ <= MAX_CLEAR_SPAN;
        net.minecraft.world.level.levelgen.structure.BoundingBox clearBox =
                new net.minecraft.world.level.levelgen.structure.BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);

        // A clear also takes any other village inside the area, such as the one-headstone village MCA makes
        // from a tombstone, so nothing is left to block a new spawn here.
        List<Village> doomed = new java.util.ArrayList<>(List.of(village));
        if (clear && located && fits) {
            List<Village> all = new java.util.ArrayList<>();
            villages.forEach(all::add);
            for (Village other : all) {
                if (other != village && other.getBox().intersects(clearBox)
                        && located(other)) {
                    doomed.add(other);
                }
            }
        }

        int removed = 0;
        for (Village gone : doomed) {
            Set<UUID> residentIds = gone.getResidentsUUIDs().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            BlockPos center = new BlockPos(gone.getCenter());
            for (VillagerEntityMCA resident : level.getEntitiesOfClass(VillagerEntityMCA.class,
                    new AABB(center).inflate(VILLAGE_RADIUS, 64.0D, VILLAGE_RADIUS))) {
                if (!residentIds.contains(resident.getUUID())) continue;
                resident.getResidency().leaveHome();
                resident.discard();
                removed++;
            }
            villages.removeVillage(gone.getId());
        }
        villages.setDirty();
        int others = doomed.size() - 1;
        if (!clear) return new RemovalResult(true, "removed", id, name, removed, 0, others);
        if (!located) return new RemovalResult(true, "nothing_to_clear", id, name, removed, 0, others);
        if (!fits) return new RemovalResult(true, "too_large_to_clear", id, name, removed, 0, others);
        AABB area = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        for (net.minecraft.world.entity.Entity entity : level.getEntities((net.minecraft.world.entity.Entity) null, area,
                entity -> entity instanceof net.minecraft.world.entity.item.ItemEntity
                        || entity instanceof net.minecraft.world.entity.decoration.HangingEntity
                        || entity instanceof net.minecraft.world.entity.decoration.ArmorStand
                        || entity instanceof net.minecraft.world.entity.animal.IronGolem
                        || entity instanceof Villager)) {
            entity.discard();
        }
        net.minecraft.world.level.block.state.BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cleared = 0;
        // Top down, so nothing above a cleared block falls or pops off; no drops and no neighbor updates.
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir()) continue;
                    level.setBlock(pos, air, net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                            | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS
                            | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
                    cleared++;
                }
            }
        }
        return new RemovalResult(true, "cleared", id, name, removed, cleared, others);
    }

    /** False for MCA's empty placeholder box at the world origin, which a village with nothing in it keeps. */
    private static boolean located(Village village) {
        net.minecraft.world.level.levelgen.structure.BoundingBox box = village.getBox();
        return box.minX() != 0 || box.minY() != 0 || box.minZ() != 0 || box.maxX() != 0 || box.maxY() != 0 || box.maxZ() != 0;
    }

    private static int spawnResidents(ServerLevel level, FoundingProfileDefinition profile,
                                      List<BlockPos> homes, int residentCount) {
        int spawned = 0;
        for (int index = 0; index < residentCount; index++) {
            BlockPos position = homes.get(index % homes.size()).above();
            VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                    .withAge(0)
                    .withPosition(Vec3.atBottomCenterOf(position))
                    .build();
            // Culture must exist before MCA chooses the given name during finalizeSpawn. Residency
            // is not reliable until the entity joins the level, so do not make naming depend on it.
            Naming.prepareFounder(villager, profile.culture());
            villager.getResidency().seekHome();
            //? if >=1.21 {
            villager.finalizeSpawn(level, level.getCurrentDifficultyAt(position),
                    MobSpawnType.COMMAND, null);
            //?} else {
            /*villager.finalizeSpawn(level, level.getCurrentDifficultyAt(position),
                    MobSpawnType.COMMAND, null, null);
            *///?}
            if (level.addFreshEntity(villager)) {
                villager.getResidency().seekHome();
                spawned++;
            }
        }
        return spawned;
    }

    private static java.util.stream.Stream<BlockPos> homes(ServerLevel level, BlockPos anchor) {
        return level.getPoiManager().findAll(
                        holder -> holder.is(PoiTypes.HOME), ignored -> true, anchor, VILLAGE_RADIUS,
                        PoiManager.Occupancy.ANY)
                .map(BlockPos::immutable)
                .distinct();
    }

    private static void refreshPoiData(ServerLevel level, BlockPos anchor) {
        int chunkRadius = Math.floorDiv(VILLAGE_RADIUS, 16) + 1;
        ChunkPos center = new ChunkPos(anchor);
        int minSectionY = SectionPos.blockToSectionCoord(
                Math.max(level.getMinBuildHeight(), anchor.getY() - 64));
        int maxSectionY = SectionPos.blockToSectionCoord(
                Math.min(level.getMaxBuildHeight() - 1, anchor.getY() + 64));
        for (int chunkX = center.x - chunkRadius; chunkX <= center.x + chunkRadius; chunkX++) {
            for (int chunkZ = center.z - chunkRadius; chunkZ <= center.z + chunkRadius; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    int sectionIndex = level.getSectionIndexFromSectionY(sectionY);
                    if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) continue;
                    LevelChunkSection section = chunk.getSection(sectionIndex);
                    if (section == null) continue;
                    level.getPoiManager().checkConsistencyWithBlocks(
                            SectionPos.of(chunkX, sectionY, chunkZ), section);
                }
            }
        }
    }

    public static ResourceLocation resolveStructure(ServerLevel level, BlockPos pos, String requested) {
        if (requested != null && !requested.isBlank() && !"auto".equalsIgnoreCase(requested)) {
            return switch (requested.toLowerCase(java.util.Locale.ROOT)) {
                case "plains" -> PLAINS;
                case "desert" -> DESERT;
                case "savanna" -> SAVANNA;
                case "snowy" -> SNOWY;
                case "taiga" -> TAIGA;
                default -> {
                    ResourceLocation parsed = ResourceLocation.tryParse(requested);
                    yield parsed == null ? PLAINS : parsed;
                }
            };
        }
        var biome = level.getBiome(pos);
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) return DESERT;
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) return SAVANNA;
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) return SNOWY;
        if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) return TAIGA;
        return PLAINS;
    }

    public record Result(boolean spawned, String reason, ResourceLocation structure, Village village,
                         int buildings, int residents, FoundingProfileApplier.Result founding) {
        static Result failed(String reason, ResourceLocation structure) {
            return new Result(false, reason, structure, null, 0, 0, null);
        }
    }

    public record RemovalResult(boolean removed, String reason, int villageId, String name, int residents, int blocks,
                                int otherVillages) {
        static RemovalResult failed(String reason) {
            return new RemovalResult(false, reason, -1, "", 0, 0, 0);
        }
    }

    public record RepopulationResult(boolean repopulated, String reason, Village village,
                                     int removedResidents, int residents,
                                     FoundingProfileApplier.Result founding) {
        static RepopulationResult failed(String reason) {
            return new RepopulationResult(false, reason, null, 0, 0, null);
        }
    }
}
