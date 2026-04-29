package com.aetherianartificer.townstead.shepherd;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Locates Livestock Pens in the villager's village and finds shearable
 * sheep inside them. Pens are MCA buildings with type "pen" — see
 * {@code data/mca/building_types/pen.json}. Sheep outside any pen are
 * intentionally ignored: shepherds work the flock, not wild herds.
 */
public final class ShepherdPenScanner {
    private static final String PEN_TYPE = "pen";

    private ShepherdPenScanner() {}

    public record Pick(Building pen, Sheep sheep) {}

    public static List<Building> pens(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = resolveVillage(villager);
        if (villageOpt.isEmpty()) return Collections.emptyList();
        List<Building> out = new ArrayList<>();
        for (Building b : villageOpt.get().getBuildings().values()) {
            if (!b.isComplete()) continue;
            if (PEN_TYPE.equals(b.getType())) out.add(b);
        }
        return out;
    }

    /**
     * Find the closest shearable sheep across all pens in the village, or
     * {@code null} if none are available. A shearable sheep is an adult
     * Sheep with {@code !isSheared() && readyForShearing()} located inside
     * a pen's bounds.
     */
    @Nullable
    public static Pick pickShearable(ServerLevel level, VillagerEntityMCA villager) {
        Pick best = null;
        double bestDsq = Double.MAX_VALUE;
        for (Building pen : pens(level, villager)) {
            for (Sheep sheep : sheepIn(level, pen)) {
                if (!isShearable(sheep)) continue;
                double dsq = sheep.distanceToSqr(villager);
                if (dsq < bestDsq) {
                    bestDsq = dsq;
                    best = new Pick(pen, sheep);
                }
            }
        }
        return best;
    }

    public static boolean isShearable(Sheep sheep) {
        return sheep.isAlive() && !sheep.isBaby() && !sheep.isSheared() && sheep.readyForShearing();
    }

    private static List<Sheep> sheepIn(ServerLevel level, Building pen) {
        BlockPos p0 = pen.getPos0();
        BlockPos p1 = pen.getPos1();
        if (p0 == null || p1 == null) return Collections.emptyList();
        AABB box = new AABB(
                Math.min(p0.getX(), p1.getX()), Math.min(p0.getY(), p1.getY()), Math.min(p0.getZ(), p1.getZ()),
                Math.max(p0.getX(), p1.getX()) + 1, Math.max(p0.getY(), p1.getY()) + 1, Math.max(p0.getZ(), p1.getZ()) + 1);
        return level.getEntitiesOfClass(Sheep.class, box, s -> pen.containsPos(s.blockPosition()));
    }

    private static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().isWithinBorder(villager)) return home;
        Optional<Village> nearest = Village.findNearest(villager);
        if (nearest.isPresent() && nearest.get().isWithinBorder(villager)) return nearest;
        return Optional.empty();
    }
}
