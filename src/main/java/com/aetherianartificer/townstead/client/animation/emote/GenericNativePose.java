package com.aetherianartificer.townstead.client.animation.emote;

import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;
import com.aetherianartificer.townstead.client.animation.nativeclip.BedrockPerformanceClip;
import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Native reactions use the same pack-authored retargeting limits as external emotes. */
final class GenericNativePose {
    private GenericNativePose() {}

    static void apply(ModelPart root, RigDefinition.EmoteMap map, BedrockPerformanceClip clip,
                      float elapsed, float remaining, float legWeight, boolean mounted, boolean furnitureLegs) {
        float tail = clip.loop() == BedrockPerformanceClip.Loop.ONCE
                ? Math.min(remaining, clip.durationTicks() - elapsed) : remaining;
        float blend = Mth.clamp(Math.min(elapsed / 4F, tail / 6F), 0F, 1F);
        if (blend <= 0) return;
        float time = clip.loop() == BedrockPerformanceClip.Loop.LOOP
                ? elapsed % clip.durationTicks() : Math.min(elapsed, clip.durationTicks());
        for (var entry : clip.bones().entrySet()) {
            String name = entry.getKey();
            if (name.equals("root")) continue; // Entity render matrix already owns root motion.
            boolean hinge = name.endsWith("forearm") || name.endsWith("shin");
            String channel = name.replace("forearm", "arm").replace("shin", "leg");
            if (channel.equals("torso")) channel = "body";
            boolean leg = channel.endsWith("_leg");
            if (mounted && (channel.equals("body") || (leg && !furnitureLegs))) continue;
            var ch = map.channels().get(channel);
            if (ch == null) continue;
            float weight = blend * (leg ? Mth.clamp(legWeight, 0F, 1F) : 1F);
            if (weight <= 0) continue;
            var track = entry.getValue();
            float[] r = track.rotation() == null ? new float[3] : track.rotation().sample(time);
            if (hinge) {
                // A short, rigid Ribbit arm has no human elbow. Only rigs opting into bends receive it.
                var path = RigModels.bonePath(root, ch.bone());
                if (ch.bend() && path != null && track.rotation() != null)
                    EmoteReflection.applyBend(path[path.length - 1], 0F, weight * ch.bendGain() * radians(r[0]));
                continue;
            }
            float[] p = track.position() == null ? new float[3] : track.position().sample(time);
            var pose = new EmoteSampler.BonePose(radians(r[0]), radians(r[1]), radians(r[2]),
                    track.position() != null, p[0], -p[1], p[2], false, 1F, 1F, 1F, false, 0F, 0F);
            GenericEmoteApplier.applyChannel(root, ch, pose, weight, track.rotation() != null);
        }
    }

    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }
}
