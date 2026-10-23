package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.culture.VillageFoundations;
import com.aetherianartificer.townstead.culture.VillageLayouts;
import com.aetherianartificer.townstead.mixin.accessor.SinglePoolElementAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sets the buildings of a culture village on foundations once the part of it in a chunk is placed. */
@Mixin(StructureStart.class)
public abstract class StructureStartVillageFoundationMixin {
    //? if neoforge {
    @Inject(method = "placeInChunk", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "m_226850_", remap = false, at = @At("RETURN"))
    *///?}
    private void townstead$foundations(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                       RandomSource random, BoundingBox area, ChunkPos chunk, CallbackInfo ci) {
        StructureStart self = (StructureStart) (Object) this;
        if (!self.isValid()) return;
        ResourceLocation key = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(self.getStructure());
        if (!VillageLayouts.isCultureVillage(key)) return;
        for (StructurePiece piece : self.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single)) continue;
            if (!piece.getBoundingBox().intersects(area)) continue;
            ResourceLocation template = ((SinglePoolElementAccessor) single).townstead$template().left().orElse(null);
            if (template == null || template.getPath().contains("/streets/")) continue;
            VillageFoundations.fill(level, piece.getBoundingBox(), area, VillageLayouts.wet(template));
        }
    }
}
