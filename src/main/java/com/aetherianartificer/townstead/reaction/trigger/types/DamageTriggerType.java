package com.aetherianartificer.townstead.reaction.trigger.types;

import com.aetherianartificer.townstead.reaction.ReactionConditions;
import com.aetherianartificer.townstead.reaction.TriggerIndex;
import com.aetherianartificer.townstead.reaction.trigger.TriggerInstance;
import com.aetherianartificer.townstead.reaction.trigger.TriggerType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Fires from an actual damage event, viewed as its victim, attacker, or a nearby witness. */
public final class DamageTriggerType implements TriggerType {
    public static final String KEY = "damage";
    public static final Set<String> ROLES = Set.of("victim", "attacker", "witness");

    @Override public String key() { return KEY; }

    public record Instance(String role, float minAmount, List<String> sources) implements TriggerInstance {
        @Override public String typeKey() { return KEY; }
    }

    @Override
    public TriggerInstance parse(JsonObject json) {
        String role = GsonHelper.getAsString(json, "role", "victim").toLowerCase(Locale.ROOT);
        if (!ROLES.contains(role)) return null;
        float minAmount = GsonHelper.getAsFloat(json, "min_amount", 0);
        if (!Float.isFinite(minAmount) || minAmount < 0) return null;
        List<String> sources = ReactionConditions.parseStringArray(json, "sources").stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).toList();
        return new Instance(role, minAmount, sources);
    }

    @Override
    public void index(TriggerInstance instance, ResourceLocation reactionId, TriggerIndex.Builder builder) {
        if (instance instanceof Instance damage) builder.add(KEY, damage.role(), reactionId);
    }
}
