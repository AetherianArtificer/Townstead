package com.aetherianartificer.townstead.client.animation.nativeclip;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;

import java.util.Comparator;
import java.util.Map;

/** Cosmetic root pose. Collapse horizontal displacement is owned by the server. */
public final class BedrockRootMotion {
    private BedrockRootMotion() {}

    public static void apply(LivingEntity entity, PoseStack poses, float partialTick) {
        if (entity.isPassenger()) return;
        long now = entity.level().getGameTime();
        boolean collapse = NativePlaybackRegistry.hasCollapse(entity.getId(), now);
        // One root owner, rather than adding simultaneous hops from different channels.
        var owner = NativePlaybackRegistry.forEntity(entity.getId(), now).entrySet().stream()
                .filter(entry -> {
                    var playback = entry.getValue();
                    if (collapse && !com.aetherianartificer.townstead.performance.CollapseMotion.CLIP
                            .equals(playback.clip().toString())) return false;
                    var clip = NativeClipRegistry.getBedrock(playback.clip()).orElse(null);
                    return clip != null && clip.bones().containsKey("root")
                            && NativeLocomotionPolicy.lowerBodyWeight(playback.clip(), entity.walkAnimation.speed(partialTick)) > 0
                            && BedrockPerformanceSampler.blend(clip, playback.elapsed(now, partialTick),
                            playback.expiresAt() - now - partialTick) > 0;
                })
                .max(Comparator.<Map.Entry<String, NativePlaybackRegistry.Playback>>comparingInt(e -> e.getValue().priority())
                        .thenComparing(Map.Entry::getKey)).orElse(null);
        if (owner == null) return;
        var playback = owner.getValue();
        var clip = NativeClipRegistry.getBedrock(playback.clip()).orElse(null);
        if (clip == null) return;
        float[] p = BedrockPerformanceSampler.rootTranslation(clip, playback.elapsed(now, partialTick),
                playback.expiresAt() - now - partialTick, false);
        float lowerBodyWeight = NativeLocomotionPolicy.lowerBodyWeight(playback.clip(), entity.walkAnimation.speed(partialTick));
        for (int axis = 0; axis < p.length; axis++) p[axis] *= lowerBodyWeight;
        var root = clip.bones().get("root");
        float time = playback.elapsed(now, partialTick);
        time = clip.loop() == BedrockPerformanceClip.Loop.LOOP
                ? time % clip.durationTicks() : Math.min(time, clip.durationTicks());
        float[] rotation = root.rotation() == null ? new float[3] : root.rotation().sample(time);
        float weight = BedrockPerformanceSampler.blend(clip, playback.elapsed(now, partialTick),
                playback.expiresAt() - now - partialTick) * lowerBodyWeight;
        for (int axis = 0; axis < 3; axis++) rotation[axis] *= weight;
        if (com.aetherianartificer.townstead.performance.CollapseMotion.CLIP.equals(playback.clip().toString())) {
            // The server already moved the body midpoint. Keep the fall's foot pivot
            // visually correct without adding its horizontal trajectory a second time.
            var anchor = com.aetherianartificer.townstead.performance.CollapseMotion.anchor(rotation);
            p[0] = (float) -anchor.x;
            p[2] = (float) -anchor.z;
        }
        var definition = RigModels.definition(RigModels.rigBaseFor(entity));
        var motion = definition == null || definition.emote() == null
                ? RigDefinition.BodyMotion.FULL : definition.emote().bodyMotion();
        if (motion.active()) {
            poses.translate(p[0] * motion.scale(), motion.clampY(p[1]), p[2] * motion.scale());
            poses.mulPose(com.aetherianartificer.townstead.performance.CollapseMotion.rotation(rotation));
        }
    }
}
