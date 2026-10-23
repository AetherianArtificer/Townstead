package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.culture.EnclosurePlan;
import com.aetherianartificer.townstead.culture.VillageEnclosurePiece;
import com.aetherianartificer.townstead.culture.VillageGround;
import com.aetherianartificer.townstead.culture.VillageLayouts;
import com.aetherianartificer.townstead.mixin.accessor.SinglePoolElementAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Plans a culture village at the size its stage rolls. Once it is planned, leaves out the buildings
 * that would stand over water or off a cliff, and adds the wall to one whose layout asks for one.
 */
@Mixin(Structure.class)
public abstract class StructureCultureVillageMixin {
    //? if neoforge {
    @Inject(method = "generate", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_226596_", remap = false, at = @At("HEAD"))
    *///?}
    private void townstead$rollStage(RegistryAccess registries, ChunkGenerator generator, BiomeSource biomes,
                                     RandomState random, StructureTemplateManager templates, long seed,
                                     ChunkPos chunk, int references, LevelHeightAccessor height,
                                     Predicate<Holder<Biome>> biomeTest, CallbackInfoReturnable<StructureStart> cir) {
        ResourceLocation key = registries.registryOrThrow(Registries.STRUCTURE).getKey((Structure) (Object) this);
        VillageLayouts.beginPlanning(VillageLayouts.stage(key, seed, chunk.x, chunk.z));
    }

    //? if neoforge {
    @Inject(method = "generate", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_226596_", remap = false, at = @At("RETURN"), cancellable = true)
    *///?}
    private void townstead$encloseVillage(RegistryAccess registries, ChunkGenerator generator, BiomeSource biomes,
                                          RandomState random, StructureTemplateManager templates, long seed,
                                          ChunkPos chunk, int references, LevelHeightAccessor height,
                                          Predicate<Holder<Biome>> biomeTest, CallbackInfoReturnable<StructureStart> cir) {
        VillageLayouts.Stage stage = VillageLayouts.planning();
        VillageLayouts.endPlanning();
        StructureStart start = cir.getReturnValue();
        if (start == null || !start.isValid()) return;
        ResourceLocation key = registries.registryOrThrow(Registries.STRUCTURE).getKey((Structure) (Object) this);
        if (!VillageLayouts.isCultureVillage(key)) return;
        List<StructurePiece> pieces = new ArrayList<>(start.getPieces());
        boolean changed = townstead$keepOnFirmGround(pieces, generator, random, height);
        VillageLayouts.Enclosure enclosure = stage != null && !stage.walled() ? null : VillageLayouts.enclosure(key);
        if (enclosure != null) {
            pieces.add(townstead$wall(pieces, enclosure));
            changed = true;
        }
        if (changed) {
            cir.setReturnValue(new StructureStart((Structure) (Object) this, start.getChunkPos(), references,
                    new PiecesContainer(pieces)));
        }
    }

    /** Leaves out buildings that would stand over water or off a cliff; the start piece always stays. */
    private static boolean townstead$keepOnFirmGround(List<StructurePiece> pieces, ChunkGenerator generator,
                                                      RandomState random, LevelHeightAccessor height) {
        VillageGround.Heights heights = new VillageGround.Heights() {
            @Override
            public int surface(int x, int z) {
                return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, random);
            }

            @Override
            public int floor(int x, int z) {
                return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, random);
            }
        };
        boolean removed = false;
        for (int i = pieces.size() - 1; i > 0; i--) {
            if (!(pieces.get(i) instanceof PoolElementStructurePiece pool
                    && pool.getElement() instanceof SinglePoolElement single)) continue;
            ResourceLocation template = ((SinglePoolElementAccessor) single).townstead$template().left().orElse(null);
            if (template == null || template.getPath().contains("/streets/")) continue;
            if (!VillageGround.fits(pool.getBoundingBox(), VillageLayouts.wet(template), heights)) {
                pieces.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    private static VillageEnclosurePiece townstead$wall(List<StructurePiece> village, VillageLayouts.Enclosure enclosure) {
        List<EnclosurePlan.Box> footprints = new ArrayList<>();
        List<EnclosurePlan.Box> streets = new ArrayList<>();
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (StructurePiece piece : village) {
            BoundingBox box = piece.getBoundingBox();
            EnclosurePlan.Box footprint = new EnclosurePlan.Box(box.minX(), box.minZ(), box.maxX(), box.maxZ());
            footprints.add(footprint);
            minY = Math.min(minY, box.minY());
            maxY = Math.max(maxY, box.maxY());
            if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single
                    && ((SinglePoolElementAccessor) single).townstead$template().left()
                    .map(id -> id.getPath().contains("/streets/")).orElse(false)) {
                streets.add(footprint);
            }
        }
        EnclosurePlan.Plan plan = EnclosurePlan.plan(footprints, streets, enclosure.margin(), enclosure.gates(),
                enclosure.gateWidth());
        return new VillageEnclosurePiece(plan, enclosure, minY - 8, maxY + 16);
    }
}
