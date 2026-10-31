package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.clothing.VillageSpirits;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.Set;

/**
 * The community spirit of the village the entity stands in.
 *
 * <p>JSON: {@code { "type":"pheno:village_spirit", "dominant": "nautical" }} is true when that
 * axis has the most points; {@code { "type":"pheno:village_spirit", "axis": "scholar",
 * "min_share": 0.25 }} is true when that axis holds at least the share. Outside a village
 * nothing matches.</p>
 */
public final class VillageSpiritConditionType implements ConditionType {

    public static final String KEY = "pheno:village_spirit";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Set<String> dominant = ClothingQuery.strings(json.get("dominant"));
        String axis = GsonHelper.getAsString(json, "axis", "").trim();
        double minShare = GsonHelper.getAsFloat(json, "min_share", 0.25f);
        return ctx -> {
            if (ctx.entity() == null) return false;
            if (!dominant.isEmpty()) {
                String top = VillageSpirits.dominantOf(ctx.entity());
                return top != null && dominant.contains(top);
            }
            if (axis.isEmpty()) return false;
            return VillageSpirits.sharesOf(ctx.entity()).applyAsDouble(axis) >= minShare;
        };
    }
}
