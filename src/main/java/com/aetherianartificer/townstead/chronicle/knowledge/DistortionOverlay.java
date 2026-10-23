package com.aetherianartificer.townstead.chronicle.knowledge;

import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The delta between the truth event and what an account's knower believes.
 * Overlays compound down the gossip chain (telephone game); the believed
 * headline is the truth's params with substitutions applied.
 *
 * <p>Guardrails (looks-like-a-bug risk): first-hand accounts never carry an
 * overlay; at most ONE substitution per chain; players are never substituted
 * in; substitution only touches roles the template declares substitutable.</p>
 */
public record DistortionOverlay(float magnitudeMult, float valenceSkew,
                                @Nullable String substitutedRole,
                                @Nullable UUID substituteUuid,
                                @Nullable String substituteName) {

    private static final Gson GSON = new Gson();

    public static final DistortionOverlay NONE = new DistortionOverlay(1f, 0f, null, null, null);

    public boolean isNone() {
        return magnitudeMult == 1f && valenceSkew == 0f && substitutedRole == null;
    }

    public boolean hasSubstitution() {
        return substitutedRole != null;
    }

    /** One gossip hop: maybe drift, maybe (once per chain) misattribute a role. */
    public DistortionOverlay compound(ChronicleEventTemplate template, SpreadChannel channel,
                                      RandomSource random,
                                      @Nullable UUID candidateUuid, @Nullable String candidateName) {
        float mult = magnitudeMult;
        float skew = valenceSkew;
        ChronicleEventTemplate.DistortionSpec spec = template.distortion();
        if (channel.driftChance() > 0 && random.nextFloat() < channel.driftChance()) {
            mult *= spec.magnitudeDrift();
            skew += (random.nextFloat() * 2f - 1f) * spec.valenceDrift();
        }
        String role = substitutedRole;
        UUID subUuid = substituteUuid;
        String subName = substituteName;
        if (role == null && candidateUuid != null && candidateName != null
                && !spec.substitutableRoles().isEmpty()
                && channel.substitutionChance() > 0
                && random.nextFloat() < channel.substitutionChance()) {
            role = spec.substitutableRoles().iterator().next();
            subUuid = candidateUuid;
            subName = candidateName;
        }
        return new DistortionOverlay(mult, clampSkew(skew), role, subUuid, subName);
    }

    /** Believed display params: truth params with the misattributed name swapped in. */
    public Map<String, String> applyToParams(Map<String, String> truthParams) {
        if (substitutedRole == null || substituteName == null) return truthParams;
        Map<String, String> believed = new HashMap<>(truthParams);
        believed.put(substitutedRole, substituteName);
        return believed;
    }

    /** The believed holder of {@code role}: the substitute if this overlay swapped it. */
    public @Nullable UUID believedUuid(String role, @Nullable UUID truthUuid) {
        return role.equals(substitutedRole) && substituteUuid != null ? substituteUuid : truthUuid;
    }

    public @Nullable String toJson() {
        if (isNone()) return null;
        JsonObject json = new JsonObject();
        if (magnitudeMult != 1f) json.addProperty("mag", magnitudeMult);
        if (valenceSkew != 0f) json.addProperty("val", valenceSkew);
        if (substitutedRole != null) {
            json.addProperty("subRole", substitutedRole);
            if (substituteUuid != null) json.addProperty("subUuid", substituteUuid.toString());
            if (substituteName != null) json.addProperty("subName", substituteName);
        }
        return GSON.toJson(json);
    }

    public static DistortionOverlay fromJson(@Nullable String json) {
        if (json == null || json.isEmpty()) return NONE;
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            UUID subUuid = obj.has("subUuid") ? UUID.fromString(obj.get("subUuid").getAsString()) : null;
            return new DistortionOverlay(
                    obj.has("mag") ? obj.get("mag").getAsFloat() : 1f,
                    obj.has("val") ? obj.get("val").getAsFloat() : 0f,
                    obj.has("subRole") ? obj.get("subRole").getAsString() : null,
                    subUuid,
                    obj.has("subName") ? obj.get("subName").getAsString() : null);
        } catch (Exception e) {
            return NONE;
        }
    }

    private static float clampSkew(float skew) {
        return Math.max(-1f, Math.min(skew, 1f));
    }
}
