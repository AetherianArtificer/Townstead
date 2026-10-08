package com.aetherianartificer.townstead.work.condition;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.profession.ProfessionCapacity;
import com.aetherianartificer.townstead.profession.ProfessionSites;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Counts sites, blocks, or living entities in authored workplace geometry. */
public final class WorksiteConditionType implements ConditionType {
    public static final String KEY = "townstead:worksite";

    private enum Scope { ASSIGNED, PROFESSION, VILLAGE }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Scope scope;
        try {
            scope = Scope.valueOf(GsonHelper.getAsString(json, "scope", "assigned")
                    .toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        List<String> buildings = strings(json.get("buildings"));
        if (buildings == null) return null;
        BlockCondition block = json.has("block_condition")
                ? BlockConditions.parse(json.get("block_condition")) : null;
        Condition entity = json.has("entity_condition")
                ? Conditions.parse(json.get("entity_condition")) : null;
        if ((json.has("block_condition") && block == null)
                || (json.has("entity_condition") && entity == null)
                || (block != null && entity != null)) return null;
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        int compareTo = GsonHelper.getAsInt(json, "compare_to", 1);
        return ctx -> test(ctx, scope, buildings, block, entity, comparison, compareTo);
    }

    private static boolean test(ConditionContext ctx, Scope scope, List<String> patterns,
                                BlockCondition block, Condition entity,
                                Comparison comparison, int compareTo) {
        if (!(ctx.entity() instanceof VillagerEntityMCA villager)
                || !(ctx.level() instanceof ServerLevel level)) return false;
        ProfessionDef profession = ProfessionDefs.byId(BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()));
        List<Place> places = places(level, villager, profession, scope, patterns);
        int count;
        if (block != null) count = countBlocks(level, places, block, comparison, compareTo);
        else if (entity != null) count = countEntities(level, places, entity, comparison, compareTo);
        else count = places.size();
        return comparison.compare(count, compareTo);
    }

    private static List<Place> places(ServerLevel level, VillagerEntityMCA villager,
                                      ProfessionDef profession, Scope scope,
                                      List<String> patterns) {
        LinkedHashSet<Place> result = new LinkedHashSet<>();
        if (scope == Scope.ASSIGNED) {
            ProfessionSites.assignedSite(level, villager, profession).ifPresent(site -> {
                if (site.building() != null && matches(patterns, site.building().getType())) {
                    result.add(new Place(site.building(), null));
                } else if (site.post() != null && patterns.isEmpty()) {
                    result.add(new Place(null, site.post()));
                }
            });
            return List.copyOf(result);
        }
        var village = ProfessionCapacity.resolveVillage(villager);
        if (village.isEmpty()) return List.of();
        if (scope == Scope.VILLAGE) {
            for (Building building : McaBuildings.all(village.get())) {
                if (building.isComplete() && matches(patterns, building.getType())) {
                    result.add(new Place(building, null));
                }
            }
            return List.copyOf(result);
        }
        for (ProfessionSites.Site site : ProfessionSites.sites(level, village.get(), profession)) {
            if (site.building() != null && matches(patterns, site.building().getType())) {
                result.add(new Place(site.building(), null));
            } else if (site.post() != null && patterns.isEmpty()) {
                result.add(new Place(null, site.post()));
            }
        }
        return List.copyOf(result);
    }

    private static int countBlocks(ServerLevel level, List<Place> places, BlockCondition condition,
                                   Comparison comparison, int compareTo) {
        Set<Long> visited = new LinkedHashSet<>();
        int count = 0;
        for (Place place : places) {
            for (BlockPos pos : positions(level, place)) {
                if (!visited.add(pos.asLong()) || !level.hasChunkAt(pos)
                        || !condition.test(level, pos)) continue;
                count++;
                if (canFinishEarly(comparison, count, compareTo)) return count;
            }
        }
        return count;
    }

    private static int countEntities(ServerLevel level, List<Place> places, Condition condition,
                                     Comparison comparison, int compareTo) {
        Set<UUID> visited = new LinkedHashSet<>();
        int count = 0;
        for (Place place : places) {
            AABB bounds = bounds(level, place);
            if (bounds == null) continue;
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, bounds)) {
                if (!inside(place, candidate.blockPosition()) || !visited.add(candidate.getUUID())
                        || !condition.test(new ConditionContext(candidate))) continue;
                count++;
                if (canFinishEarly(comparison, count, compareTo)) return count;
            }
        }
        return count;
    }

    private static Iterable<BlockPos> positions(ServerLevel level, Place place) {
        if (place.building() != null) {
            BlockPos a = place.building().getPos0();
            BlockPos b = place.building().getPos1();
            return a == null || b == null ? List.of() : BlockPos.betweenClosed(
                    Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                    Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()),
                    Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        }
        if (place.post() == null) return List.of();
        List<BlockPos> positions = new ArrayList<>();
        for (long packed : com.aetherianartificer.townstead.work.WorkSiteBounds
                .workAreaAround(level, place.post())) positions.add(BlockPos.of(packed));
        return positions;
    }

    private static AABB bounds(ServerLevel level, Place place) {
        if (place.building() != null) {
            BlockPos a = place.building().getPos0();
            BlockPos b = place.building().getPos1();
            if (a == null || b == null) return null;
            return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                    Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()) + 1,
                    Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
        }
        return place.post() == null ? null : new AABB(place.post()).inflate(8, 4, 8);
    }

    private static boolean inside(Place place, BlockPos pos) {
        if (place.building() == null) return true;
        BlockPos a = place.building().getPos0();
        BlockPos b = place.building().getPos1();
        return a != null && b != null
                && pos.getX() >= Math.min(a.getX(), b.getX())
                && pos.getX() <= Math.max(a.getX(), b.getX())
                && pos.getY() >= Math.min(a.getY(), b.getY())
                && pos.getY() <= Math.max(a.getY(), b.getY())
                && pos.getZ() >= Math.min(a.getZ(), b.getZ())
                && pos.getZ() <= Math.max(a.getZ(), b.getZ());
    }

    private static boolean canFinishEarly(Comparison comparison, int count, int compareTo) {
        return comparison == Comparison.GREATER_OR_EQUAL && count >= compareTo
                || comparison == Comparison.GREATER && count > compareTo;
    }

    private static boolean matches(List<String> patterns, String buildingType) {
        if (patterns.isEmpty()) return true;
        if (buildingType == null) return false;
        for (String pattern : patterns) {
            if (pattern.endsWith("*")
                    ? buildingType.startsWith(pattern.substring(0, pattern.length() - 1))
                    : buildingType.equals(pattern)) return true;
        }
        return false;
    }

    private static List<String> strings(JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        if (!element.isJsonArray()) return null;
        List<String> values = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonPrimitive() || !child.getAsJsonPrimitive().isString()) return null;
            values.add(child.getAsString());
        }
        return List.copyOf(values);
    }

    private record Place(Building building, BlockPos post) {}
}
