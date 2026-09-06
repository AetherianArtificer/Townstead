package com.aetherianartificer.townstead.client.animation.nativeclip;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;

import java.util.Comparator;
import java.util.Map;

/** Cosmetic whole-character translation: no velocity, collision, or server-position changes. */
public final class BedrockRootMotion {
    private BedrockRootMotion() {}

    public static void apply(LivingEntity entity, PoseStack poses, float partialTick) {
        if (entity.isPassenger()) return;
        long now = entity.level().getGameTime();
        // One root owner, rather than adding simultaneous hops from different channels.
        var owner = NativePlaybackRegistry.forEntity(entity.getId(), now).entrySet().stream()
                .filter(entry -> {
                    var playback = entry.getValue();
                    var clip = NativeClipRegistry.getBedrock(playback.clip()).orElse(null);
                    return clip != null && clip.bones().containsKey("root")
                            && BedrockPerformanceSampler.blend(clip, now - playback.startedAt() + partialTick,
                            playback.expiresAt() - now - partialTick) > 0;
                })
                .max(Comparator.<Map.Entry<String, NativePlaybackRegistry.Playback>>comparingInt(e -> e.getValue().priority())
                        .thenComparing(Map.Entry::getKey)).orElse(null);
        if (owner == null) return;
        var playback = owner.getValue();
        var clip = NativeClipRegistry.getBedrock(playback.clip()).orElse(null);
        if (clip == null) return;
        float[] p = BedrockPerformanceSampler.rootTranslation(clip, now - playback.startedAt() + partialTick,
                playback.expiresAt() - now - partialTick, false);
        var definition = RigModels.definition(RigModels.rigBaseFor(entity));
        var motion = definition == null || definition.emote() == null
                ? RigDefinition.BodyMotion.FULL : definition.emote().bodyMotion();
        if (motion.active()) poses.translate(p[0] * motion.scale(), motion.clampY(p[1]), p[2] * motion.scale());
    }
}
