package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.culture.VillagePaletteProcessor;
import com.aetherianartificer.townstead.culture.VillagePalettes;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Picks a culture village's palette swaps when it places, from the biome at the village's centre, so
 * every building and street of the village is dressed alike wherever its pieces stand.
 */
@Mixin(StructureStart.class)
public abstract class StructureStartVillagePaletteMixin {
    //? if neoforge {
    @Inject(method = "placeInChunk", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_226850_", remap = false, at = @At("HEAD"))
    *///?}
    private void townstead$beginVillagePalette(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                               RandomSource random, BoundingBox box, ChunkPos chunk, CallbackInfo ci) {
        if (!VillagePalettes.any()) return;
        StructureStart self = (StructureStart) (Object) this;
        ResourceLocation key = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(self.getStructure());
        if (key == null || !VillagePalettes.dresses(key)) return;
        ChunkPos centre = self.getChunkPos();
        int x = centre.getMiddleBlockX(), z = centre.getMiddleBlockZ();
        RandomState state = level.getLevel().getChunkSource().randomState();
        int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, state);
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), state.sampler());
        VillagePaletteProcessor.begin(VillagePalettes.swapsFor(key, biome));
    }

    //? if neoforge {
    @Inject(method = "placeInChunk", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "m_226850_", remap = false, at = @At("RETURN"))
    *///?}
    private void townstead$endVillagePalette(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                             RandomSource random, BoundingBox box, ChunkPos chunk, CallbackInfo ci) {
        VillagePaletteProcessor.end();
    }
}
