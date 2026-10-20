package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.culture.CultureVillageSites;
import com.aetherianartificer.townstead.culture.CultureVillages;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Culture village structures generate only while their culture is on in the Switchboard, at its rate,
 * on a site their layout accepts.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorCultureVillagesMixin {
    //? if neoforge {
    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_223104_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$gateCultureVillages(StructureSet.StructureSelectionEntry entry, StructureManager structures,
                                               RegistryAccess registries, RandomState random,
                                               StructureTemplateManager templates, long seed, ChunkAccess chunk,
                                               ChunkPos chunkPos, SectionPos section,
                                               CallbackInfoReturnable<Boolean> cir) {
        entry.structure().unwrapKey().ifPresent(key -> {
            if (!CultureVillages.allowsGeneration(key.location(), seed, chunkPos)
                    || !CultureVillageSites.allows(key.location(), (ChunkGenerator) (Object) this, registries,
                    random, seed, chunk, chunkPos)) {
                cir.setReturnValue(false);
            }
        });
    }
}
