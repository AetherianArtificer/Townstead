package com.aetherianartificer.townstead.client.animation.emote;

import java.util.Locale;
import java.util.Map;

/**
 * Maps Emotecraft / playeranimcore bone names (camelCase or snake_case, depending on
 * the emote author) to Townstead's canonical {@link
 * com.aetherianartificer.townstead.client.animation.AnimationTargetMap} part names.
 *
 * <p>Bones with no corresponding {@code ModelPart} on MCA's villager / player rig
 * (held items, capes) are mapped to {@code null} and dropped at parse time. {@code
 * hat} is also dropped in the first cut: the bridge's {@code syncMcaDependentParts}
 * does {@code model.hat.copyFrom(model.head)} after every adapter pass, which would
 * clobber any hat-targeted emote channel.</p>
 */
public final class EmoteBoneMapping {
    private EmoteBoneMapping() {}

    private static final Map<String, String> MAP = Map.ofEntries(
            Map.entry("head", "head"),
            Map.entry("body", "body"),
            Map.entry("torso", "body"),
            Map.entry("waist", "body"),
            Map.entry("leftarm", "left_arm"),
            Map.entry("rightarm", "right_arm"),
            Map.entry("leftleg", "left_leg"),
            Map.entry("rightleg", "right_leg")
    );

    /**
     * @return the Townstead canonical name, or {@code null} if the bone has no MCA
     *         analogue and should be dropped.
     */
    public static String mapOrNull(String emoteBoneName) {
        if (emoteBoneName == null) return null;
        String key = emoteBoneName.trim().toLowerCase(Locale.ROOT).replace("_", "");
        return MAP.get(key);
    }
}
