package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.pheno.seek.SeekPlaces;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Villages as places a seek can search: {@code townstead:village}, the seeker's own village (a villager's home, or the
 * village they stand in), and {@code townstead:neighbouring_villages}, the other villages whose middles lie within
 * {@code distance} blocks (512 by default) of its own, nearest first, up to {@code villages} of them (2 by default).
 * {@code "of":"origin"} takes the village of whoever started the seek instead. {@code townstead:building} is each building of
 * the seeker's village whose MCA building {@code type} is given, such as {@code "type":"compat/bitofamess/laundry"}.
 */
public final class VillagePlaces {

    private VillagePlaces() {}

    public static void register() {
        SeekPlaces.register(ResourceLocation.tryParse("townstead:village"), (seeker, origin, focus, params) -> {
            Village village = villageOf(anchor(seeker, origin, params));
            return village == null ? List.of() : List.of(box(village));
        });
        SeekPlaces.register(ResourceLocation.tryParse("townstead:building"), (seeker, origin, focus, params) -> buildings(seeker, origin, params));
        SeekPlaces.register(ResourceLocation.tryParse("townstead:neighbouring_villages"), (seeker, origin, focus, params) -> {
            LivingEntity anchor = anchor(seeker, origin, params);
            Village home = villageOf(anchor);
            if (!(anchor.level() instanceof ServerLevel level)) return List.of();
            int distance = Mth.clamp(GsonHelper.getAsInt(params, "distance", 512), 16, 4096);
            int count = Mth.clamp(GsonHelper.getAsInt(params, "villages", 2), 1, 16);
            Vec3i from = home != null ? home.getCenter() : anchor.blockPosition();
            List<Village> near = new ArrayList<>();
            for (Village village : VillageManager.get(level)) {
                if (home != null && village.getId() == home.getId()) continue;
                if (distanceSqr(village.getCenter(), from) <= (double) distance * distance) near.add(village);
            }
            near.sort(Comparator.comparingDouble(v -> distanceSqr(v.getCenter(), from)));
            List<AABB> out = new ArrayList<>();
            for (int i = 0; i < Math.min(count, near.size()); i++) out.add(box(near.get(i)));
            return out;
        });
    }

    /** The buildings of the seeker's village whose MCA type is {@code type}, each as its box. */
    private static List<AABB> buildings(Mob seeker, LivingEntity origin, JsonObject params) {
        String type = GsonHelper.getAsString(params, "type", "").trim();
        Village village = type.isEmpty() ? null : villageOf(anchor(seeker, origin, params));
        if (village == null) return List.of();
        List<AABB> out = new ArrayList<>();
        village.getBuildingsOfType(type).forEach(building -> {
            var p0 = building.getPos0();
            var p1 = building.getPos1();
            out.add(new AABB(Math.min(p0.getX(), p1.getX()), Math.min(p0.getY(), p1.getY()), Math.min(p0.getZ(), p1.getZ()),
                    Math.max(p0.getX(), p1.getX()) + 1, Math.max(p0.getY(), p1.getY()) + 1, Math.max(p0.getZ(), p1.getZ()) + 1));
        });
        return out;
    }

    private static LivingEntity anchor(Mob seeker, LivingEntity origin, JsonObject params) {
        return "origin".equals(GsonHelper.getAsString(params, "of", "self")) && origin != null ? origin : seeker;
    }

    private static Village villageOf(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return null;
        try {
            if (entity instanceof VillagerEntityMCA villager) {
                Optional<Village> home = villager.getResidency().getHomeVillage();
                if (home.isPresent()) return home.get();
            }
            return VillageManager.get(level).findNearestVillage(entity.blockPosition(), Village.MERGE_MARGIN).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static AABB box(Village village) {
        BoundingBox b = village.getBox();
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1).inflate(8);
    }

    private static double distanceSqr(Vec3i a, Vec3i b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
