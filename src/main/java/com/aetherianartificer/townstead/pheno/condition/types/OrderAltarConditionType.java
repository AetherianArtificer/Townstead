package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.building.BuildingCells;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.politics.order.OrderPlaces;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * True when the altar of the entity's order is placed (in the entity's world), and matches where
 * it was asked to be: inside a building of a type ({@code building}, a name or a list; a plain
 * name matches every tier), outside every building ({@code outside}), and under the open sky or
 * not ({@code sky}).
 * <pre>
 * { "type": "pheno:order_altar" }
 * { "type": "pheno:order_altar", "building": ["house", "big_house"] }
 * { "type": "pheno:order_altar", "outside": true, "sky": false }
 * </pre>
 */
public final class OrderAltarConditionType implements ConditionType {
    public static final String KEY = "pheno:order_altar";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        List<String> types = names(json.get("building"));
        Boolean outside = json.has("outside") ? json.get("outside").getAsBoolean() : null;
        Boolean sky = json.has("sky") ? json.get("sky").getAsBoolean() : null;
        return ctx -> {
            if (ctx.entity() == null || !(ctx.entity().level() instanceof ServerLevel level)) return false;
            GlobalPos altar = OrderPlaces.altar(level, ctx.entity());
            if (altar == null || !altar.dimension().equals(level.dimension())) return false;
            BlockPos pos = altar.pos();
            Building building = types.isEmpty() && outside == null ? null : BuildingCells.at(level, pos);
            if (outside != null && outside != (building == null)) return false;
            if (!types.isEmpty() && (building == null
                    || types.stream().noneMatch(name -> CanBuildConditionType.matches(building.getType(), name)))) return false;
            return sky == null || sky == level.canSeeSky(pos.above());
        };
    }

    private static List<String> names(@Nullable JsonElement raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isJsonNull()) return out;
        if (raw.isJsonArray()) raw.getAsJsonArray().forEach(e -> out.add(e.getAsString().toLowerCase(Locale.ROOT)));
        else out.add(raw.getAsString().toLowerCase(Locale.ROOT));
        return out;
    }
}
