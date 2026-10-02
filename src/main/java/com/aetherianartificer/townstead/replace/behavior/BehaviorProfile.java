package com.aetherianartificer.townstead.replace.behavior;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How a replaced mob acts while it is wild: a set of behaviors, each a registered type with its
 * own options. {@code "behaviors": [{"type":"pheno:hunt","radius":16}, "pheno:bite"]}.
 */
public record BehaviorProfile(ResourceLocation id, Map<ResourceLocation, Behavior> behaviors) {
    public static final String SCHEMA = "townstead:behavior_profile/v1";

    public BehaviorProfile {
        behaviors = Map.copyOf(behaviors);
    }

    public <T extends Behavior> @Nullable T get(ResourceLocation type, Class<T> kind) {
        Behavior behavior = behaviors.get(type);
        return kind.isInstance(behavior) ? kind.cast(behavior) : null;
    }

    static BehaviorProfile parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        Map<ResourceLocation, Behavior> behaviors = new LinkedHashMap<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "behaviors")) {
            JsonObject options = element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            String raw = element.isJsonObject() ? GsonHelper.getAsString(options, "type") : element.getAsString();
            ResourceLocation type = DataPackLang.parseId(raw);
            BehaviorTypes.Parser parser = type == null ? null : BehaviorTypes.parser(type);
            if (parser == null) throw new IllegalArgumentException("unknown behavior type '" + raw + "'");
            behaviors.put(type, parser.parse(options));
        }
        return new BehaviorProfile(id, behaviors);
    }

    /** One configured behavior. */
    public interface Behavior {}
}
