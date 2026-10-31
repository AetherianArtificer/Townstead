package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

import java.util.function.Supplier;
//? if neoforge {
import net.neoforged.neoforge.registries.DeferredRegister;
//?} else {
/*import net.minecraftforge.registries.DeferredRegister;
*///?}

/** Structure processors culture village data packs name in their processor lists. */
public final class VillageProcessors {
    public static final DeferredRegister<StructureProcessorType<?>> TYPES = DeferredRegister.create(
            Registries.STRUCTURE_PROCESSOR, Townstead.MOD_ID);
    /** {@code townstead:village_palette}: see {@link VillagePaletteProcessor}. */
    public static final Supplier<StructureProcessorType<VillagePaletteProcessor>> PALETTE =
            TYPES.register("village_palette", () -> () -> VillagePaletteProcessor.CODEC);

    private VillageProcessors() {}
}
