package com.aetherianartificer.townstead.client.animation.emote;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps Emotecraft / playerAnim bone names (camelCase or snake_case) to Townstead's
 * canonical {@link com.aetherianartificer.townstead.client.animation.AnimationTargetMap}
 * part names.
 *
 * <p>Most emote bones map to exactly one Townstead model part. The two virtual
 * parent bones — {@code torso} and {@code body} — propagate transforms to the
 * children that visually hang from them, because Minecraft's {@link
 * net.minecraft.client.model.HumanoidModel} stores all parts as flat siblings
 * (no parent-child rotation). Without propagation, leaning the {@code torso}
 * tilts only the visible body block while head/arms stay vertical, producing a
 * detached neck.</p>
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
            Map.entry("waist", new Targets("body", List.of())),
            // torso = upper-body parent: head + arms follow it.
            Map.entry("torso", new Targets("body", List.of("head", "left_arm", "right_arm"))),
            // body = full-body parent: everything follows it.
            Map.entry("body", new Targets(
                    "body",
                    List.of("head", "left_arm", "right_arm", "left_leg", "right_leg")))
    );

    public static Targets mapTargets(String emoteBoneName) {
        if (emoteBoneName == null) return Targets.EMPTY;
        String key = emoteBoneName.trim().toLowerCase(Locale.ROOT).replace("_", "");
        return MAP.getOrDefault(key, Targets.EMPTY);
    }
}
