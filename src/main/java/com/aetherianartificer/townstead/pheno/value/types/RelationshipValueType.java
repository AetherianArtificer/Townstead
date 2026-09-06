package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.chronicle.ChronicleSocialKnowledge;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.social.SocialKnowledge;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.UUID;

/** A named, directional relationship quality for conditions, weights, actions, and tests. */
public final class RelationshipValueType implements ValueType {
    public static final String KEY = "pheno:relationship";
    @Override public String key() { return KEY; }
    @Override public Value parse(JsonObject json) {
        String quality = GsonHelper.getAsString(json, "quality", "");
        if (quality.isBlank() || ResourceLocation.tryParse(quality) == null) return null;
        SocialTarget target = SocialTarget.parse(GsonHelper.getAsString(json, "toward", "other"), false);
        if (target == null) return null;
        return Value.subjectAware(context -> {
            SocialKnowledge knowledge = ChronicleSocialKnowledge.of(context);
            UUID toward = target.resolve(context);
            return knowledge == null || toward == null ? Double.NaN : knowledge.relationship(toward, quality);
        });
    }
}
