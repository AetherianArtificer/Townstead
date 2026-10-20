package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.culture.VillageClearings;
import com.aetherianartificer.townstead.culture.VillageEnclosurePiece;
import com.aetherianartificer.townstead.culture.VillageLayouts;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Marks the ground of culture villages near a chunk while it is decorated, so trees keep off it. */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorVillageClearingMixin {
    //? if neoforge {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_213609_", remap = false, at = @At("HEAD"))
    *///?}
    private void townstead$markClearings(WorldGenLevel level, ChunkAccess chunk, StructureManager structures,
                                         CallbackInfo ci) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<StructureStart> starts = structures.startsForStructure(chunk.getPos(),
                structure -> VillageLayouts.isCultureVillage(registry.getKey(structure)));
        int minX = chunk.getPos().getMinBlockX() - 8, maxX = chunk.getPos().getMaxBlockX() + 8;
        int minZ = chunk.getPos().getMinBlockZ() - 8, maxZ = chunk.getPos().getMaxBlockZ() + 8;
        List<BoundingBox> clearings = new ArrayList<>();
        for (StructureStart start : starts) {
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof VillageEnclosurePiece || !(piece instanceof PoolElementStructurePiece pool)) continue;
                boolean street = pool.getElement().toString().contains("/streets/");
                BoundingBox box = VillageClearings.widen(piece.getBoundingBox(),
                        street ? VillageClearings.STREET_MARGIN : VillageClearings.BUILDING_MARGIN);
                if (box.maxX() < minX || box.minX() > maxX || box.maxZ() < minZ || box.minZ() > maxZ) continue;
                clearings.add(box);
            }
        }
        VillageClearings.begin(clearings);
    }

    //? if neoforge {
    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "m_213609_", remap = false, at = @At("RETURN"))
    *///?}
    private void townstead$clearClearings(WorldGenLevel level, ChunkAccess chunk, StructureManager structures,
                                          CallbackInfo ci) {
        VillageClearings.end();
    }
}
