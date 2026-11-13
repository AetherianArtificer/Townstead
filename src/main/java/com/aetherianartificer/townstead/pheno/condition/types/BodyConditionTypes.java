package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.root.attachment.AttachmentBody;
import com.aetherianartificer.townstead.root.attachment.BodyParts;
import com.aetherianartificer.townstead.root.attachment.BodyVolumes;
import com.aetherianartificer.townstead.root.attachment.WornAttachments;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/** The conditions bodies answer to: how marked they are, and what state their parts are in. */
public final class BodyConditionTypes {

    private BodyConditionTypes() {}

    /** At least {@code min} of a Trace: {@code { "type":"pheno:has_trace", "trace":"examplepack:ink", "min":0.3 }}. */
    public static final class HasTrace implements ConditionType {
        @Override public String key() { return "pheno:has_trace"; }

        @Override
        public Condition parse(JsonObject json) {
            String trace = GsonHelper.getAsString(json, "trace", "");
            float min = GsonHelper.getAsFloat(json, "min", 0.01f);
            return ctx -> {
                LivingEntity entity = ctx.entity();
                return entity != null && com.aetherianartificer.townstead.compat.mess.MessBridge.traceAmount(entity, trace) >= min;
            };
        }
    }

    /**
     * A part's stage: {@code { "type":"pheno:attachment_state", "attachment":"examplepack:wing", "state":"lost" }}.
     * {@code state} is {@code intact}, {@code wounded} or {@code lost}. Wearing nothing by that id is intact.
     */
    public static final class AttachmentState implements ConditionType {
        @Override public String key() { return "pheno:attachment_state"; }

        @Override
        public Condition parse(JsonObject json) {
            String attachment = GsonHelper.getAsString(json, "attachment", "");
            int wanted = switch (GsonHelper.getAsString(json, "state", "intact")) {
                case "wounded" -> AttachmentBody.Part.WOUNDED;
                case "lost" -> AttachmentBody.Part.LOST;
                default -> AttachmentBody.Part.INTACT;
            };
            return ctx -> {
                LivingEntity entity = ctx.entity();
                return entity != null && BodyParts.stage(entity, attachment) == wanted;
            };
        }
    }

    /** Whether a part that folds doesn't fit here: {@code { "type":"pheno:folded" }}. */
    public static final class Folded implements ConditionType {
        @Override public String key() { return "pheno:folded"; }

        @Override
        public Condition parse(JsonObject json) {
            return ctx -> {
                LivingEntity entity = ctx.entity();
                return entity != null && BodyVolumes.folded(entity);
            };
        }
    }
}
