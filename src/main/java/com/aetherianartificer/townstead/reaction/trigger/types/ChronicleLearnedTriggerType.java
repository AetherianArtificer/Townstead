package com.aetherianartificer.townstead.reaction.trigger.types;

import com.aetherianartificer.townstead.reaction.TriggerIndex;
import com.aetherianartificer.townstead.reaction.trigger.TriggerInstance;
import com.aetherianartificer.townstead.reaction.trigger.TriggerType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/** React to an account actually learned by this knower, not an omniscient world event. */
public final class ChronicleLearnedTriggerType implements TriggerType {
    public static final String KEY = "chronicle_learned";
    @Override public String key() { return KEY; }
    public record Instance(String template, String channel, double minFidelity) implements TriggerInstance {
        @Override public String typeKey() { return KEY; }
        public boolean matches(String templateId, String learnedChannel, double fidelity) {
            return (template.equals("*") || template.equals(templateId))
                    && (channel.equals("*") || channel.equals(learnedChannel))
                    && Double.isFinite(fidelity) && fidelity >= minFidelity;
        }
    }
    @Override public TriggerInstance parse(JsonObject json) {
        for (String field : json.keySet()) if (!java.util.Set.of("type", "template", "channel", "min_fidelity").contains(field)) return null;
        String template = GsonHelper.getAsString(json, "template", "*");
        String channel = GsonHelper.getAsString(json, "channel", "*");
        double fidelity = GsonHelper.getAsDouble(json, "min_fidelity", 0);
        if ((!template.equals("*") && ResourceLocation.tryParse(template) == null)
                || channel.isBlank() || !Double.isFinite(fidelity) || fidelity < 0 || fidelity > 1) return null;
        if (!template.equals("*")) template = ResourceLocation.tryParse(template).toString();
        return new Instance(template, channel, fidelity);
    }
    @Override public void index(TriggerInstance instance, ResourceLocation reactionId, TriggerIndex.Builder builder) {
        if (instance instanceof Instance learned) builder.add(KEY, learned.template(), reactionId);
    }
}
