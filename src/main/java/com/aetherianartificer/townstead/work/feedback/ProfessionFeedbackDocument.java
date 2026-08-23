package com.aetherianartificer.townstead.work.feedback;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/** Singular profession-feedback sidecars, following the profession/path/skill file layout. */
public final class ProfessionFeedbackDocument {
    public static final String SETTINGS_SCHEMA = "townstead:profession_feedback/v1";
    public static final String RULE_SCHEMA = "townstead:profession_feedback_rule/v1";

    private ProfessionFeedbackDocument() {}

    public record Settings(ResourceLocation profession, long interval, double range) {
        public static Settings parse(ResourceLocation profession, JsonObject json) {
            TownsteadSchema.validateRequired(json, SETTINGS_SCHEMA);
            return new Settings(profession,
                    Math.max(1L, GsonHelper.getAsLong(json, "interval", 1200L)),
                    Math.max(1.0, GsonHelper.getAsDouble(json, "range", 24.0)));
        }
    }

    public record Rule(ResourceLocation source, ResourceLocation profession, String id,
                       int priority, Trigger trigger, Condition when,
                       String translation, int variants) {
        public static Rule parse(ResourceLocation source, ResourceLocation profession,
                                 String id, JsonObject json) {
            TownsteadSchema.validateRequired(json, RULE_SCHEMA);
            String triggerName = GsonHelper.getAsString(json, "trigger", "event");
            Trigger trigger;
            try {
                trigger = Trigger.valueOf(triggerName.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("'trigger' must be 'event', 'immediate', or 'periodic'");
            }
            Condition when = Conditions.ALWAYS;
            if (json.has("when")) {
                when = Conditions.parse(json.get("when"));
                if (when == null) throw new IllegalArgumentException("'when' is not a valid Pheno condition");
            }
            JsonObject dialogue = GsonHelper.getAsJsonObject(json, "dialogue");
            String translation = GsonHelper.getAsString(dialogue, "translate", "").trim();
            if (translation.isEmpty()) {
                throw new IllegalArgumentException("'dialogue.translate' is required");
            }
            return new Rule(source, profession, id,
                    GsonHelper.getAsInt(json, "priority", 0), trigger, when,
                    translation, Math.max(1, GsonHelper.getAsInt(json, "variants", 1)));
        }

        public boolean periodic() {
            return trigger == Trigger.PERIODIC;
        }

        public boolean immediate() {
            return trigger == Trigger.IMMEDIATE;
        }
    }

    public enum Trigger {
        EVENT,
        IMMEDIATE,
        PERIODIC
    }
}
