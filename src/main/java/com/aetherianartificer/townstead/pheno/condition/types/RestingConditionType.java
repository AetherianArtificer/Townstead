package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.rest.RestEvents;
import com.aetherianartificer.townstead.rest.RestSpot;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code pheno:resting}: the entity is asleep, optionally in one of the named rest spots
 * ({@code spot}, an id or a list).
 *
 * <p>JSON: {@code { "type":"pheno:resting", "spot":"examplepack:hammock" }}</p>
 */
public final class RestingConditionType implements ConditionType {
    public static final String KEY = "pheno:resting";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        List<ResourceLocation> spots = new ArrayList<>();
        if (json.has("spot")) {
            JsonElement raw = json.get("spot");
            if (raw.isJsonArray()) {
                for (JsonElement element : raw.getAsJsonArray()) {
                    ResourceLocation id = DataPackLang.parseId(element.getAsString());
                    if (id != null) spots.add(id);
                }
            } else {
                ResourceLocation id = DataPackLang.parseId(raw.getAsString());
                if (id != null) spots.add(id);
            }
        }
        return ctx -> {
            if (ctx.entity() == null || !ctx.entity().isSleeping()) return false;
            if (spots.isEmpty()) return true;
            RestSpot spot = RestEvents.sleepingIn(ctx.entity());
            return spot != null && spots.contains(spot.id());
        };
    }
}
