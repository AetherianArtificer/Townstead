package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import java.util.function.Supplier;
//? if neoforge {
import net.neoforged.neoforge.registries.DeferredRegister;
//?} else {
/*import net.minecraftforge.registries.DeferredRegister;
*///?}

/** Structure pieces Townstead adds to culture villages; they save with the world, so their ids must stay. */
public final class VillagePieceTypes {
    public static final DeferredRegister<StructurePieceType> TYPES = DeferredRegister.create(
            Registries.STRUCTURE_PIECE, Townstead.MOD_ID);
    /** {@code townstead:village_enclosure}: see {@link VillageEnclosurePiece}. */
    public static final Supplier<StructurePieceType> ENCLOSURE =
            TYPES.register("village_enclosure", () -> VillageEnclosurePiece::new);

    private VillagePieceTypes() {}
}
