package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.client.animation.emote.EmoteSampler;
import com.aetherianartificer.townstead.client.animation.emote.ParsedEmote;
import com.aetherianartificer.townstead.client.animation.nativeclip.NativeClipRegistry;
import com.aetherianartificer.townstead.client.animation.nativeclip.NativePlaybackRegistry;
import com.aetherianartificer.townstead.client.animation.nativeclip.BedrockPerformanceClip;
import com.aetherianartificer.townstead.client.animation.nativeclip.BedrockPerformanceSampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Samples Townstead's bundled/resource-pack clips into the existing canonical target-map seam. */
public final class NativePerformanceSourceAdapter implements AnimationSourceAdapter {
    @Override public String id() { return "native_performance"; }
    @Override public boolean isAvailable() { return true; }

    @Override
    public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        long now = context.entity().level().getGameTime();
        float partial = context.animationProgress() - context.entity().tickCount;
        List<NativePlaybackRegistry.Playback> active = new ArrayList<>(
                NativePlaybackRegistry.forEntity(context.entity().getId(), now).values());
        active.sort(Comparator.comparingInt(NativePlaybackRegistry.Playback::priority));
        if (active.isEmpty()) return List.of();

        AnimationTargetMap<?> hostTargets = AnimationTargetMap.forMcaModel(context.model());
        List<AnimationTransform> out = new ArrayList<>();
        for (NativePlaybackRegistry.Playback playback : active) {
            boolean mounted = context.entity().isPassenger();
            String clipName = playback.clip().getPath();
            if (mounted && clipName.equals("relaxed_lean")) continue;
            BedrockPerformanceClip bedrock = NativeClipRegistry.getBedrock(playback.clip()).orElse(null);
            if (bedrock != null) {
                List<AnimationTransform> sampled = BedrockPerformanceSampler.sample(bedrock,
                        (now - playback.startedAt()) + partial, playback.expiresAt() - now - partial, hostTargets);
                for (AnimationTransform transform : sampled) {
                    boolean furnitureLegs = transform.target().endsWith("_leg")
                            && !clipName.equals("tap_foot") && !clipName.equals("stool_sit")
                            && !clipName.startsWith("recline");
                    if (!mounted || (!"body".equals(transform.target()) && !furnitureLegs)) out.add(transform);
                }
                continue;
            }
            ParsedEmote clip = NativeClipRegistry.get(playback.clip()).orElse(null);
            if (clip == null) continue; // A missing client resource is a safe no-animation fallback.
            float elapsed = (now - playback.startedAt()) + partial;
            // A semantic beat may outlive a one-shot gesture (for example a 21-second cocktail
            // round using a 1.4-second toast). Holding the clip's last keyed bend for the whole
            // beat leaves an otherwise neutral arm permanently curled. Once a one-shot clip has
            // completed it owns no model channels; looping ambient clips continue normally.
            if (clip.loopType() != ParsedEmote.LoopType.LOOP && elapsed >= clip.stopTick()) continue;
            float sample = elapsed;
            if (clip.loopType() == ParsedEmote.LoopType.LOOP) {
                sample = elapsed % Math.max(1, clip.stopTick());
            } else {
                sample = Math.min(elapsed, clip.stopTick());
            }
            float fadeIn = Math.min(1F, Math.max(0F, elapsed / 4F));
            float remaining = clip.loopType() == ParsedEmote.LoopType.LOOP
                    ? playback.expiresAt() - now - partial
                    : clip.stopTick() - elapsed;
            float fadeOut = Math.min(1F, Math.max(0F, remaining / 6F));
            List<AnimationTransform> sampled = EmoteSampler.sample(
                    clip, sample, Math.min(fadeIn, fadeOut), hostTargets);
            if (context.entity().isPassenger()) {
                // A mount owns the seated origin and torso. Body sway/lift from a social gesture
                // otherwise rocks the entire model through its chair. Seated performances still
                // retain their head, arm and hand expression; the stool layer owns the legs.
                for (AnimationTransform transform : sampled) {
                    if (!"body".equals(transform.target())) out.add(transform);
                }
            } else {
                out.addAll(sampled);
            }
        }
        return out;
    }

}
