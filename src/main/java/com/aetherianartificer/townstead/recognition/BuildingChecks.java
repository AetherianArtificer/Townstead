package com.aetherianartificer.townstead.recognition;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.decoration.DecorationInstance;
import com.aetherianartificer.townstead.decoration.DecorationSavedData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks on a building MCA already recognized, declared in the {@code requires} array of an
 * {@code extended_buildings} entry next to the site rules. They read only what MCA recorded (the
 * floor area and box) and the decorations Townstead recognized, so nothing is scanned again.
 *
 * <ul>
 *   <li>{@code {"decoration": "townstead:throne", "count": 1}}: decorations anchored inside.</li>
 *   <li>{@code {"size": {"min": 60, "max": 400}}}: MCA's floor area.</li>
 *   <li>{@code {"height": {"min": 12}}}: the height of MCA's box; on the floor system, of the
 *       whole structure the room belongs to.</li>
 * </ul>
 */
public final class BuildingChecks {
    public sealed interface Check permits DecorationCount, Size, Height {}

    public record DecorationCount(ResourceLocation decoration, int count) implements Check {}

    public record Size(int min, int max) implements Check {}

    public record Height(int min, int max) implements Check {}

    /** Why a building is not a type: the check it failed and the value it actually has. */
    public record Failure(String type, Check check, int actual, BlockPos center) {
        /** "This looks like a Keep, but it needs 1 Throne. It has 0." */
        public Component message() {
            Component name = Component.translatable("buildingType." + type);
            if (check instanceof DecorationCount d) {
                var definition = com.aetherianartificer.townstead.decoration.Decorations.definition(d.decoration());
                Component decoration = definition == null ? Component.literal(d.decoration().toString())
                        : Component.translatable(definition.translationKey());
                return Component.translatable("townstead.building_check.decoration", name, d.count(), decoration, actual);
            }
            if (check instanceof Size s) {
                return actual < s.min()
                        ? Component.translatable("townstead.building_check.size_min", name, s.min(), actual)
                        : Component.translatable("townstead.building_check.size_max", name, s.max(), actual);
            }
            Height h = (Height) check;
            return actual < h.min()
                    ? Component.translatable("townstead.building_check.height_min", name, h.min(), actual)
                    : Component.translatable("townstead.building_check.height_max", name, h.max(), actual);
        }
    }

    private static volatile Map<String, List<Check>> BY_TYPE = Map.of();

    private BuildingChecks() {}

    public static void replaceAll(Map<String, List<Check>> next) {
        Map<String, List<Check>> stable = new LinkedHashMap<>();
        next.forEach((type, checks) -> {
            if (type != null && checks != null && !checks.isEmpty()) stable.put(type, List.copyOf(checks));
        });
        BY_TYPE = Map.copyOf(stable);
    }

    public static Map<String, List<Check>> snapshot() {
        return BY_TYPE;
    }

    public static List<Check> of(String buildingType) {
        return buildingType == null ? List.of() : BY_TYPE.getOrDefault(buildingType, List.of());
    }

    static boolean isCheck(JsonObject json) {
        return json.has("decoration") || json.has("size") || json.has("height");
    }

    public static List<Check> parse(JsonArray array) {
        List<Check> out = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject json = GsonHelper.convertToJsonObject(element, "requires entry");
            if (json.has("decoration")) {
                ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "decoration"));
                int count = GsonHelper.getAsInt(json, "count", 1);
                if (id == null || count < 1) throw new IllegalArgumentException("invalid 'decoration' check");
                out.add(new DecorationCount(id, count));
            } else if (json.has("size")) {
                JsonObject range = GsonHelper.getAsJsonObject(json, "size");
                out.add(new Size(GsonHelper.getAsInt(range, "min", 0), GsonHelper.getAsInt(range, "max", Integer.MAX_VALUE)));
            } else if (json.has("height")) {
                JsonObject range = GsonHelper.getAsJsonObject(json, "height");
                out.add(new Height(GsonHelper.getAsInt(range, "min", 0), GsonHelper.getAsInt(range, "max", Integer.MAX_VALUE)));
            }
        }
        return out;
    }

    /** The level that holds this exact village record, or null when none does. */
    public static @Nullable ServerLevel levelOf(net.minecraft.server.MinecraftServer server, Village village) {
        if (server == null || village == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (net.conczin.mca.server.world.data.VillageManager.get(level).getOrEmpty(village.getId()).orElse(null) == village) {
                return level;
            }
        }
        return null;
    }

    /** The first check this building fails as {@code type}, or null when it passes them all. */
    public static @Nullable Failure failure(ServerLevel level, Village village, Building building, String type) {
        for (Check check : of(type)) {
            int actual;
            boolean passes;
            if (check instanceof DecorationCount d) {
                actual = decorations(level, building, d.decoration());
                passes = actual >= d.count();
            } else if (check instanceof Size s) {
                actual = McaBuildings.size(building);
                passes = actual >= s.min() && actual <= s.max();
            } else {
                Height h = (Height) check;
                actual = height(village, building);
                passes = actual >= h.min() && actual <= h.max();
            }
            if (!passes) return new Failure(type, check, actual, building.getCenter());
        }
        return null;
    }

    private static int decorations(ServerLevel level, Building building, ResourceLocation decoration) {
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        BlockPos center = building.getCenter();
        int radius = Math.max(max.getX() - min.getX(), Math.max(max.getY() - min.getY(), max.getZ() - min.getZ())) + 1;
        int count = 0;
        for (DecorationInstance instance : DecorationSavedData.get(level).within(center, radius)) {
            BlockPos anchor = instance.anchor();
            if (instance.decorationId().equals(decoration)
                    && anchor.getX() >= min.getX() && anchor.getX() <= max.getX()
                    && anchor.getY() >= min.getY() && anchor.getY() <= max.getY()
                    && anchor.getZ() >= min.getZ() && anchor.getZ() <= max.getZ()) {
                count++;
            }
        }
        return count;
    }

    private static int height(Village village, Building building) {
        //? if >=1.21 {
        var structure = village.getStructureFor(building);
        if (structure.isPresent()) {
            return structure.get().getPos1().getY() - structure.get().getPos0().getY() + 1;
        }
        //?}
        return building.getPos1().getY() - building.getPos0().getY() + 1;
    }
}
