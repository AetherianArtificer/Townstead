package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.building.BuildingCells;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pet.VillagerPets;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.village.TownRange;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Sets where the pets of the villager side of the action rest while their keeper sleeps or is
 * away. The building is the nearest one of a {@code building} type in the keeper's village (a type
 * or a list of types), else the keeper's {@code home}, else the building they stand in
 * ({@code here}, the default). {@code inside} picks a spot in the building, else just outside it.
 * <pre>
 * { "type": "pheno:pet_rest", "building": "inn", "inside": false, "entity_type": "minecraft:wolf" }
 * { "type": "pheno:pet_rest", "place": "home", "inside": true }
 * </pre>
 */
public final class PetRestActionType implements ActionType {
    public static final String KEY = "pheno:pet_rest";
    private static final double PET_RANGE = 64;
    private static final int NEAR_REACH = 4;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        List<String> types = strings(json.get("building"));
        boolean home = "home".equals(GsonHelper.getAsString(json, "place", "here"));
        boolean inside = GsonHelper.getAsBoolean(json, "inside", false);
        ResourceLocation petType = json.has("entity_type") ? DataPackLang.parseId(GsonHelper.getAsString(json, "entity_type")) : null;
        return ctx -> {
            VillagerEntityMCA keeper = ctx.entity() instanceof VillagerEntityMCA v ? v
                    : ctx.other() instanceof VillagerEntityMCA v ? v : null;
            if (keeper == null || !(keeper.level() instanceof ServerLevel level)) {
                ctx.fail();
                return;
            }
            BlockPos from = keeper.blockPosition();
            Building building;
            if (!types.isEmpty()) {
                building = nearestOfType(level, from, types);
                if (building == null) {
                    ctx.fail();
                    return;
                }
            } else if (home) {
                Optional<GlobalPos> pos = keeper.getResidency().getHome();
                building = pos.filter(p -> p.dimension().equals(level.dimension()))
                        .map(p -> BuildingCells.at(level, p.pos())).orElse(null);
                if (building == null) {
                    ctx.fail();
                    return;
                }
            } else {
                building = BuildingCells.at(level, from);
            }
            BlockPos spot;
            if (building == null) spot = inside ? BuildingCells.inside(level, from, NEAR_REACH, keeper.getRandom()) : BuildingCells.outside(level, from);
            else spot = inside ? BuildingCells.inside(level, building, keeper.getRandom()) : BuildingCells.outside(level, building, from);
            if (spot == null) {
                ctx.fail();
                return;
            }
            boolean any = false;
            for (Mob pet : VillagerPets.petsOf(keeper, PET_RANGE)) {
                if (petType != null && !petType.equals(BuiltInRegistries.ENTITY_TYPE.getKey(pet.getType()))) continue;
                VillagerPets.setRest(pet, spot);
                any = true;
            }
            if (!any) ctx.fail();
        };
    }

    private static @Nullable Building nearestOfType(ServerLevel level, BlockPos from, List<String> types) {
        return TownRange.at(level, from).flatMap(village -> McaBuildings.all(village).stream()
                .filter(b -> types.stream().anyMatch(t -> matches(b.getType(), t)))
                .min(Comparator.comparingDouble(b -> b.getCenter().distSqr(from)))).orElse(null);
    }

    /** A full type, or its last segment: {@code hunter_lodge} matches {@code compat/vampirism/hunter_lodge}. */
    private static boolean matches(@Nullable String actual, String expected) {
        if (actual == null) return false;
        return actual.equals(expected) || actual.endsWith("/" + expected);
    }

    private static List<String> strings(@Nullable JsonElement raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isJsonNull()) return out;
        if (raw.isJsonArray()) raw.getAsJsonArray().forEach(e -> out.add(e.getAsString()));
        else out.add(raw.getAsString());
        out.removeIf(String::isBlank);
        return out;
    }
}
