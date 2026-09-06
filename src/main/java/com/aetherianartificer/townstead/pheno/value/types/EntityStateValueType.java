package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/** Numeric projections of one canonical state. */
public final class EntityStateValueType implements ValueType {
    public enum Projection { AMOUNT, TIER, REMAINING }
    private final String key;
    private final Projection projection;

    public EntityStateValueType(String key, Projection projection) {
        this.key = key;
        this.projection = projection;
    }

    @Override public String key() { return key; }

    @Override
    public Value parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "state", ""));
        if (id == null) return null;
        return context -> {
            if (context.self() == null) return 0;
            EntityStates.Resolved state = EntityStates.resolve(context.self(), id);
            return switch (projection) {
                case AMOUNT -> state.amount();
                case TIER -> state.tierIndex();
                case REMAINING -> state.remaining();
            };
        };
    }
}
