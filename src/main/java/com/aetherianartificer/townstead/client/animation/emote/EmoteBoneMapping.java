package com.aetherianartificer.townstead.client.animation.emote;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps Emotecraft / playerAnim bone names (camelCase or snake_case) to Townstead's
 * canonical {@link com.aetherianartificer.townstead.client.animation.AnimationTargetMap}
 * part names.
 *
 * <p>The {@code torso} / {@code body} bones are deliberately <b>not</b> mapped
 * at the model-part level. Emotecraft applies those bones' translation +
 * rotation at the entity-render matrix stack (see {@code
 * EmoteBodyTransformSampler} and {@code LivingEntityRendererEmoteMixin}), which
 * rotates and translates the whole entity together — head, arms, legs, body
 * cube all follow as one. Head/arm/leg keyframes then layer their per-part
 * rotation on top of that whole-entity transform, exactly the way Emotecraft's
 * native player pipeline composes them. Mapping {@code torso} or {@code body}
 * to a model part here would compound with the matrix-stack rotation and tear
 * the model apart (limbs flipping through the torso during a backflip).</p>
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
            Map.entry("waist", new Targets("body", List.of()))
            // "torso" and "body" intentionally absent: the entity-level matrix
            // stack mixin applies their translation + rotation at world level so
            // the whole model rotates together, the way Emotecraft does.
    );

    public static Targets mapTargets(String emoteBoneName) {
        if (emoteBoneName == null) return Targets.EMPTY;
        String key = emoteBoneName.trim().toLowerCase(Locale.ROOT).replace("_", "");
        return MAP.getOrDefault(key, Targets.EMPTY);
    }
}
