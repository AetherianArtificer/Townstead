package com.aetherianartificer.townstead.building;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.village.TownRange;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Standing room in and around village buildings. */
public final class BuildingCells {
    private BuildingCells() {}

    /** The village building that holds {@code pos}, if any. */
    public static @Nullable Building at(ServerLevel level, BlockPos pos) {
        return TownRange.at(level, pos)
                .flatMap(village -> McaBuildings.all(village).stream().filter(b -> b.containsPos(pos)).findFirst())
                .orElse(null);
    }

    /**
     * A standing cell within {@code reach} of {@code center}, inside the building that holds it, so
     * whoever uses it stays in the room. Without a building, a cell right beside it.
     */
    public static @Nullable BlockPos inside(ServerLevel level, BlockPos center, int reach, RandomSource random) {
        Building room = at(level, center);
        int within = room == null ? 2 : reach;
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-within, -1, -within), center.offset(within, 1, within))) {
            if (pos.distManhattan(center) < 2) continue;
            if (room != null && !room.containsPos(pos)) continue;
            if (standable(level, pos)) cells.add(pos.immutable());
        }
        return cells.isEmpty() ? null : cells.get(random.nextInt(cells.size()));
    }

    /** Any standing cell inside {@code building}. */
    public static @Nullable BlockPos inside(ServerLevel level, Building building, RandomSource random) {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(building.getPos0(), building.getPos1())) {
            if (building.containsPos(pos) && standable(level, pos)) cells.add(pos.immutable());
        }
        return cells.isEmpty() ? null : cells.get(random.nextInt(cells.size()));
    }

    /** The standing cell just outside the building that holds {@code center}, nearest to it. */
    public static @Nullable BlockPos outside(ServerLevel level, BlockPos center) {
        Building room = at(level, center);
        if (room != null) return outside(level, room, center);
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 1, 4))) {
            if (pos.distManhattan(center) >= 3 && standable(level, pos)) cells.add(pos.immutable());
        }
        return cells.stream().min(Comparator.comparingDouble(pos -> pos.distSqr(center))).orElse(null);
    }

    /** The standing cell just outside {@code building}, nearest to {@code near}. */
    public static @Nullable BlockPos outside(ServerLevel level, Building building, BlockPos near) {
        BlockPos lo = building.getPos0(), hi = building.getPos1();
        int minX = Math.min(lo.getX(), hi.getX()) - 3, maxX = Math.max(lo.getX(), hi.getX()) + 3;
        int minZ = Math.min(lo.getZ(), hi.getZ()) - 3, maxZ = Math.max(lo.getZ(), hi.getZ()) + 3;
        int minY = Math.min(lo.getY(), hi.getY()) - 1, maxY = Math.min(Math.max(lo.getY(), hi.getY()), minY + 4);
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (building.containsPos(pos) || !standable(level, pos)) continue;
            cells.add(pos.immutable());
        }
        return cells.stream().min(Comparator.comparingDouble(pos -> pos.distSqr(near))).orElse(null);
    }

    public static boolean standable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
}
