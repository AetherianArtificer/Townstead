package com.aetherianartificer.townstead.client.animation.emote;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps Emotecraft / playerAnim bone names (camelCase or snake_case) to Townstead's
 * canonical {@link com.aetherianartificer.townstead.client.animation.AnimationTargetMap}
 * part names.
 *
 * <p>{@code torso} routes to the {@code body} ModelPart cube — that's the layer
 * Emotecraft's {@code PlayerModelMixin} sends it to via {@code
 * AnimationApplier.updatePart("torso", playerModel.body)}: pixel-unit
 * translation, radian rotation, applied as a per-part SET. The {@code body}
 * bone is a <b>separate</b> bone consumed at the entity-render matrix stack in
 * block units by {@link EmoteBodyTransformSampler}; it is intentionally NOT in
 * this map. Routing both to the same target would conflate two different
 * authoring channels (intra-model body sway vs whole-entity lift).</p>
 *
 * <p>Bones with no MCA analogue ({@code leftItem}, {@code rightItem}, {@code
 * cape}) and {@code hat} (which {@code syncMcaDependentParts} resyncs to {@code
 * head} after every adapter pass) return {@link Targets#EMPTY} and are dropped
 * at parse time.</p>
 */
public final class EmoteBoneMapping {
    private EmoteBoneMapping() {}

    /**
     * @param primary       part that receives a SET-blended transform (the visible
     *                      analogue of this emote bone). Null = no primary part.
     * @param propagateTo   parts that receive an ADD transform scaled by the same
     *                      blend, so they "follow" the parent bone the way
     *                      Emotecraft's hierarchy makes them follow.
     */
    public record Targets(String primary, List<String> propagateTo) {
        public static final Targets EMPTY = new Targets(null, List.of());
        public boolean isEmpty() {
            return primary == null && propagateTo.isEmpty();
        }
    }

    private static final Map<String, Targets> MAP = Map.ofEntries(
            Map.entry("head", new Targets("head", List.of())),
            Map.entry("leftarm", new Targets("left_arm", List.of())),
            Map.entry("rightarm", new Targets("right_arm", List.of())),
            Map.entry("leftleg", new Targets("left_leg", List.of())),
            Map.entry("rightleg", new Targets("right_leg", List.of())),
            Map.entry("torso", new Targets("body", List.of())),
            Map.entry("waist", new Targets("body", List.of()))
            // "body" intentionally absent: that bone is consumed at the matrix
            // stack by EmoteBodyTransformSampler in block units; sending it to
            // the body ModelPart in pixel units would add a sub-pixel intra-model
            // shift that compounds with the visible matrix-level lift.
    );

    public static Targets mapTargets(String emoteBoneName) {
        if (emoteBoneName == null) return Targets.EMPTY;
        String key = emoteBoneName.trim().toLowerCase(Locale.ROOT).replace("_", "");
        return MAP.getOrDefault(key, Targets.EMPTY);
    }
}
