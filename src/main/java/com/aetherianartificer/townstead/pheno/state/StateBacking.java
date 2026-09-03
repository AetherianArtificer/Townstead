package com.aetherianartificer.townstead.pheno.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** One pluggable read source, and optionally write target, for a canonical state. */
public record StateBacking(
        ResourceLocation id,
        ResourceLocation state,
        SourceType type,
        @Nullable ResourceLocation ownedResource,
        @Nullable ResourceLocation statusEffect,
        @Nullable Condition appliesTo,
        int readPriority,
        int writePriority,
        boolean writable,
        double presenceValue,
        Map<Integer, Level> amplifierLevels) {

    public static final String SCHEMA = "pheno:state_backing/v1";
    public enum SourceType { OWNED, STATUS_EFFECT }
    public record Level(@Nullable Double amount, @Nullable String tier) {}

    public StateBacking {
        amplifierLevels = Map.copyOf(amplifierLevels);
    }

    static StateBacking parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        ResourceLocation state = DataPackLang.parseId(GsonHelper.getAsString(json, "state", ""));
        if (state == null) throw new IllegalArgumentException("'state' must be a resource id");
        if (!json.has("source") || !json.get("source").isJsonObject()) {
            throw new IllegalArgumentException("'source' must be an object");
        }
        JsonObject source = json.getAsJsonObject("source");
        String rawType = GsonHelper.getAsString(source, "type", "").toLowerCase(Locale.ROOT);
        SourceType type = switch (rawType) {
            case "pheno:owned" -> SourceType.OWNED;
            case "pheno:status_effect" -> SourceType.STATUS_EFFECT;
            default -> throw new IllegalArgumentException("unknown state backing source '" + rawType + "'");
        };
        ResourceLocation effect = type == SourceType.STATUS_EFFECT
                ? DataPackLang.parseId(GsonHelper.getAsString(source, "effect", "")) : null;
        if (type == SourceType.STATUS_EFFECT && effect == null) {
            throw new IllegalArgumentException("status-effect source requires an 'effect' id");
        }
        ResourceLocation resource = type == SourceType.OWNED && source.has("resource")
                ? DataPackLang.parseId(GsonHelper.getAsString(source, "resource", "")) : state;
        if (type == SourceType.OWNED && resource == null) {
            throw new IllegalArgumentException("owned source 'resource' must be a resource id");
        }
        Condition applies = json.has("applies_to") ? Conditions.parse(json.get("applies_to")) : null;
        if (json.has("applies_to") && applies == null) {
            throw new IllegalArgumentException("'applies_to' is not a valid Pheno condition");
        }
        int readPriority = GsonHelper.getAsInt(json, "read_priority", 0);
        int writePriority = GsonHelper.getAsInt(json, "write_priority", readPriority);
        boolean writable = GsonHelper.getAsBoolean(json, "writable", type == SourceType.OWNED);
        if (writable && type != SourceType.OWNED) {
            throw new IllegalArgumentException("v1 status-effect backings are observation-only");
        }
        double presence = GsonHelper.getAsDouble(source, "presence_value", 1);
        if (!Double.isFinite(presence)) throw new IllegalArgumentException("'presence_value' must be finite");
        Map<Integer, Level> levels = new LinkedHashMap<>();
        if (source.has("amplifier")) {
            if (!source.get("amplifier").isJsonObject()) throw new IllegalArgumentException("'amplifier' must be an object");
            for (Map.Entry<String, JsonElement> entry : source.getAsJsonObject("amplifier").entrySet()) {
                int amplifier;
                try { amplifier = Integer.parseInt(entry.getKey()); }
                catch (NumberFormatException exception) { throw new IllegalArgumentException("amplifier keys must be integers"); }
                if (amplifier < 0) throw new IllegalArgumentException("amplifier keys cannot be negative");
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive()) throw new IllegalArgumentException("amplifier mappings must be numbers or tier strings");
                if (value.getAsJsonPrimitive().isNumber()) levels.put(amplifier, new Level(value.getAsDouble(), null));
                else if (value.getAsJsonPrimitive().isString()) levels.put(amplifier, new Level(null, value.getAsString()));
                else throw new IllegalArgumentException("amplifier mappings must be numbers or tier strings");
            }
        }
        return new StateBacking(id, state, type, resource, effect, applies, readPriority, writePriority,
                writable, presence, levels);
    }
}
