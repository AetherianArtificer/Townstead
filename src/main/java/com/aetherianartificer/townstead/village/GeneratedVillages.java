package com.aetherianartificer.townstead.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The pieces of a village the world generated: its houses, smithy and farms as the generator laid them out, before MCA has
 * found any of them as buildings. For what belongs to a generated village from the start, whatever a player builds later.
 */
public final class GeneratedVillages {

    private GeneratedVillages() {}

    /** The boxes of every piece of the generated village whose pieces reach the chunk at {@code anchor}. Empty if none. */
    public static List<BoundingBox> pieces(ServerLevel level, BlockPos anchor) {
        List<BoundingBox> out = new ArrayList<>();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var starts = level.structureManager().startsForStructure(new ChunkPos(anchor),
                structure -> registry.wrapAsHolder(structure).is(StructureTags.VILLAGE));
        for (var start : starts) {
            if (!start.isValid()) continue;
            for (StructurePiece piece : start.getPieces()) out.add(piece.getBoundingBox());
        }
        return out;
    }

    /** The box of the generated village piece holding {@code pos}, or null for anywhere a village didn't generate. */
    public static @Nullable BoundingBox pieceAt(ServerLevel level, BlockPos pos) {
        var start = level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);
        if (!start.isValid()) return null;
        for (StructurePiece piece : start.getPieces()) if (piece.getBoundingBox().isInside(pos)) return piece.getBoundingBox();
        return null;
    }
}
