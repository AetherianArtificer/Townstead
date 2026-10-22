package com.aetherianartificer.townstead.culture;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Where a culture village may start, by the site rules of its {@code village_layout}: on ground
 * that is mostly dry, and clear of other culture villages. Structure sets can exclude only one other
 * set, so the distance between culture villages is kept here, and the village whose structure id
 * sorts later gives way, so two close sites never both cancel.
 */
public final class CultureVillageSites {
    /** Ground is sampled on a 5 by 5 grid, 8 blocks apart, around the start chunk. */
    private static final int SAMPLE_STEP = 8;
    private static final int SAMPLE_REACH = 2;

    private CultureVillageSites() {}

    public static boolean allows(ResourceLocation structure, ChunkGenerator generator, RegistryAccess registries,
                                 RandomState random, long seed, ChunkAccess chunk, ChunkPos pos) {
        VillageLayouts.Site site = VillageLayouts.site(structure);
        if (site.maxWater() < 1.0 && waterShare(generator, random, chunk, pos) > site.maxWater()) return false;
        return site.keepApart() <= 0 || !yieldsToNeighbour(structure, registries, seed, pos, site.keepApart());
    }

    /** The share of sampled columns whose surface is water. */
    static double waterShare(ChunkGenerator generator, RandomState random, ChunkAccess chunk, ChunkPos pos) {
        int wet = 0, total = 0;
        for (int dx = -SAMPLE_REACH; dx <= SAMPLE_REACH; dx++) {
            for (int dz = -SAMPLE_REACH; dz <= SAMPLE_REACH; dz++) {
                int x = pos.getMiddleBlockX() + dx * SAMPLE_STEP;
                int z = pos.getMiddleBlockZ() + dz * SAMPLE_STEP;
                int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, chunk, random);
                int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, chunk, random);
                if (surface > floor) wet++;
                total++;
            }
        }
        return (double) wet / total;
    }

    /** Whether another culture village, earlier by id, may start within {@code range} chunks. */
    static boolean yieldsToNeighbour(ResourceLocation structure, RegistryAccess registries, long seed,
                                     ChunkPos pos, int range) {
        for (StructureSet set : registries.registryOrThrow(Registries.STRUCTURE_SET)) {
            if (!(set.placement() instanceof RandomSpreadStructurePlacement spread)) continue;
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                ResourceLocation other = entry.structure().unwrapKey().map(ResourceKey::location).orElse(null);
                if (other == null || other.compareTo(structure) >= 0 || CultureVillages.culture(other) == null) continue;
                if (candidateWithin(spread, other, seed, pos, range)) return true;
            }
        }
        return false;
    }

    private static boolean candidateWithin(RandomSpreadStructurePlacement spread, ResourceLocation other, long seed,
                                           ChunkPos pos, int range) {
        int spacing = spread.spacing();
        for (int rx = Math.floorDiv(pos.x - range, spacing); rx <= Math.floorDiv(pos.x + range, spacing); rx++) {
            for (int rz = Math.floorDiv(pos.z - range, spacing); rz <= Math.floorDiv(pos.z + range, spacing); rz++) {
                ChunkPos candidate = spread.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
                if (Math.abs(candidate.x - pos.x) <= range && Math.abs(candidate.z - pos.z) <= range
                        && CultureVillages.allowsGeneration(other, seed, candidate)) {
                    return true;
                }
            }
        }
        return false;
    }
}
