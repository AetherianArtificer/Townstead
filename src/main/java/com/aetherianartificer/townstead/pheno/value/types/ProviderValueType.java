package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.state.StateProviders;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * {@code pheno:provider}: the current reading of a registered state provider, so a condition can
 * ask what another mod reports. A place-aware provider answers for the frame's position, so the
 * reading follows a hazard probe. {@code default} (0) stands in when the provider has no reading.
 *
 * <pre>
 *   { "type": "pheno:provider", "provider": "examplemod:sun_damage", "default": 0 }
 * </pre>
 */
public final class ProviderValueType implements ValueType {
    public static final String KEY = "pheno:provider";

    @Override public String key() { return KEY; }

    @Override
    public Value parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "provider", ""));
        if (id == null) return null;
        double fallback = GsonHelper.getAsDouble(json, "default", 0);
        return context -> {
            if (context.self() == null) return fallback;
            Double reading = StateProviders.readAt(id, context.self(),
                    net.minecraft.core.BlockPos.containing(context.pos()));
            return reading == null ? fallback : reading;
        };
    }
}
