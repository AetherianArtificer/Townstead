package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.state.EntityStateDefinition;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/** Queries a canonical open state independently of whichever backing currently supplies it. */
public final class EntityStateConditionType implements ConditionType {
    public static final String KEY = "pheno:state";

    @Override public String key() { return KEY; }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "state", ""));
        if (id == null) return null;
        ResourceLocation source = json.has("source")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "source", "")) : null;
        if (json.has("source") && source == null) return null;
        String tier = json.has("tier") ? GsonHelper.getAsString(json, "tier", "") : null;
        String minTier = json.has("min_tier") ? GsonHelper.getAsString(json, "min_tier", "") : null;
        String maxTier = json.has("max_tier") ? GsonHelper.getAsString(json, "max_tier", "") : null;
        double min = GsonHelper.getAsDouble(json, "min", -Double.MAX_VALUE);
        double max = GsonHelper.getAsDouble(json, "max", Double.MAX_VALUE);
        long minRemaining = GsonHelper.getAsLong(json, "min_remaining", 0);
        long maxRemaining = GsonHelper.getAsLong(json, "max_remaining", Long.MAX_VALUE);
        boolean active = GsonHelper.getAsBoolean(json, "active", true);
        return ctx -> {
            EntityStates.Resolved resolved = EntityStates.resolve(ctx.entity(), id);
            if (resolved.active() != active) return false;
            if (!active) return true;
            if (source != null && !source.equals(resolved.source())) return false;
            if (resolved.amount() < min || resolved.amount() > max
                    || resolved.remaining() < minRemaining || resolved.remaining() > maxRemaining) return false;
            if (tier != null && !tier.equals(resolved.tier())) return false;
            if (minTier != null || maxTier != null) {
                EntityStateDefinition definition = EntityStates.definition(id);
                if (definition == null) return false;
                EntityStateDefinition.Tier lower = minTier == null ? null : definition.tier(minTier);
                EntityStateDefinition.Tier upper = maxTier == null ? null : definition.tier(maxTier);
                if ((minTier != null && lower == null) || (maxTier != null && upper == null)) return false;
                if (lower != null && resolved.tierIndex() < definition.tiers().indexOf(lower)) return false;
                if (upper != null && resolved.tierIndex() > definition.tiers().indexOf(upper)) return false;
            }
            return true;
        };
    }
}
