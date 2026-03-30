package com.aetherianartificer.townstead.animation;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class VillagerResponsePoseLibrary {
    private VillagerResponsePoseLibrary() {}

    public static void apply(
            VillagerResponseAnimation animation,
            float progress,
            float ageInTicks,
            ModelPart head,
            ModelPart body,
            ModelPart rightArm,
            ModelPart leftArm,
            ModelPart rightLeg,
            ModelPart leftLeg
    ) {
        if (animation == null || progress < 0.0f) return;

        float envelope = envelope(progress);
        float oscillation = Mth.sin(ageInTicks * 0.45f);
        float pulse = Mth.sin(progress * Mth.PI * 2.0f);
        float beat = Mth.sin(progress * Mth.PI * 4.0f);
        float settle = 1.0f - progress;

        switch (animation) {
            case NEUTRAL_IDLE -> {
                body.xRot += 0.03f * envelope;
                body.yRot += 0.04f * Mth.sin(ageInTicks * 0.12f) * envelope;
                head.yRot += 0.08f * oscillation * envelope;
                head.zRot += 0.04f * Mth.sin(ageInTicks * 0.22f) * envelope;
                rightArm.zRot += 0.045f * oscillation * envelope;
                leftArm.zRot -= 0.045f * oscillation * envelope;
            }
            case WAVE_BACK -> {
                float waveLoop = Mth.sin(progress * Mth.PI * 5.0f);
                body.yRot -= 0.16f * envelope;
                head.yRot -= 0.12f * envelope;
                head.xRot -= 0.05f * envelope;
                rightArm.xRot = blend(rightArm.xRot, -1.85f + 0.10f * waveLoop, envelope);
                rightArm.yRot = blend(rightArm.yRot, -0.18f + 0.30f * waveLoop, envelope);
                rightArm.zRot = blend(rightArm.zRot, 0.38f + 0.28f * waveLoop, envelope);
                leftArm.xRot += 0.10f * envelope;
                leftArm.yRot += 0.08f * envelope;
            }
            case CAUTIOUS_ACK -> {
                float nod = Mth.sin(progress * Mth.PI * 2.0f) * 0.12f;
                head.xRot += (0.08f + nod) * envelope;
                body.xRot += 0.06f * envelope;
                body.yRot -= 0.06f * envelope;
                rightArm.xRot = blend(rightArm.xRot, -0.55f, envelope);
                rightArm.yRot = blend(rightArm.yRot, -0.18f, envelope);
                leftArm.zRot -= 0.05f * envelope;
            }
            case APPLAUD -> {
                float clap = Mth.sin(progress * Mth.PI * 6.0f);
                body.xRot += 0.04f * envelope;
                head.xRot = blend(head.xRot, -0.02f, envelope);
                head.yRot = blend(head.yRot, 0.0f, envelope * 0.75f);
                head.zRot = blend(head.zRot, 0.0f, envelope * 0.75f);
                rightArm.xRot = blend(rightArm.xRot, -1.10f, envelope);
                leftArm.xRot = blend(leftArm.xRot, -1.10f, envelope);
                rightArm.yRot = blend(rightArm.yRot, 0.06f + 0.18f * clap, envelope);
                leftArm.yRot = blend(leftArm.yRot, -0.06f - 0.18f * clap, envelope);
                rightArm.zRot = blend(rightArm.zRot, 0.08f, envelope);
                leftArm.zRot = blend(leftArm.zRot, -0.08f, envelope);
            }
            case BASHFUL -> {
                head.xRot = blend(head.xRot, 0.18f, envelope);
                head.yRot = blend(head.yRot, -0.14f, envelope);
                head.zRot = blend(head.zRot, -0.06f, envelope);
                body.xRot += 0.16f * envelope;
                body.yRot += 0.08f * envelope;
                rightArm.xRot = blend(rightArm.xRot, -0.35f, envelope);
                leftArm.xRot = blend(leftArm.xRot, -0.22f, envelope);
                rightArm.yRot = blend(rightArm.yRot, 0.65f, envelope);
                leftArm.yRot = blend(leftArm.yRot, -0.42f, envelope);
            }
            case COMFORT -> {
                body.xRot += 0.10f * envelope;
                body.yRot -= 0.08f * envelope;
                head.xRot += 0.08f * Mth.sin(progress * Mth.PI) * envelope;
                head.yRot -= 0.05f * envelope;
                rightArm.xRot = blend(rightArm.xRot, -1.05f + 0.06f * pulse, envelope);
                leftArm.xRot = blend(leftArm.xRot, -0.45f, envelope);
                rightArm.yRot = blend(rightArm.yRot, -0.36f, envelope);
                leftArm.yRot = blend(leftArm.yRot, 0.20f, envelope);
            }
            case AWKWARD -> {
                head.yRot += 0.30f * Mth.sin(ageInTicks * 0.18f) * envelope;
                head.zRot += 0.10f * Mth.sin(ageInTicks * 0.11f) * envelope;
                body.xRot += 0.11f * envelope;
                rightArm.xRot = blend(rightArm.xRot, -0.12f, envelope);
                leftArm.xRot = blend(leftArm.xRot, -0.08f, envelope);
                rightArm.zRot = blend(rightArm.zRot, 0.28f, envelope);
                leftArm.zRot = blend(leftArm.zRot, -0.18f, envelope);
                rightLeg.xRot += 0.03f * beat * envelope;
                leftLeg.xRot -= 0.03f * beat * envelope;
            }
            case ACKNOWLEDGE -> {
                head.xRot += 0.18f * Mth.sin(progress * Mth.PI) * envelope;
                body.yRot -= 0.04f * envelope;
                rightArm.xRot = blend(rightArm.xRot, -0.52f, envelope);
                rightArm.yRot = blend(rightArm.yRot, -0.14f, envelope);
                rightArm.zRot = blend(rightArm.zRot, 0.08f, envelope);
            }
            case PUZZLED -> {
                head.zRot += 0.28f * envelope;
                head.yRot += 0.14f * oscillation * envelope;
                body.yRot += 0.05f * oscillation * envelope;
                rightArm.xRot = blend(rightArm.xRot, -0.48f, envelope);
                rightArm.zRot = blend(rightArm.zRot, 0.38f, envelope);
                leftArm.xRot += 0.04f * settle * envelope;
            }
            case AMUSED -> {
                body.xRot += 0.08f * envelope;
                body.yRot -= 0.08f * envelope;
                head.zRot -= 0.08f * envelope;
                head.yRot -= 0.05f * envelope;
                rightArm.xRot = blend(rightArm.xRot, -0.62f + 0.08f * beat, envelope);
                leftArm.xRot = blend(leftArm.xRot, -0.28f, envelope);
                rightArm.zRot = blend(rightArm.zRot, 0.12f, envelope);
                rightLeg.xRot += 0.06f * pulse * envelope;
                leftLeg.xRot -= 0.06f * pulse * envelope;
            }
            case STARTLED -> {
                float snap = snap(progress);
                head.xRot -= 0.24f * envelope;
                body.xRot -= 0.12f * envelope;
                rightArm.xRot = blend(rightArm.xRot, -1.20f + 0.10f * snap, envelope);
                leftArm.xRot = blend(leftArm.xRot, -1.20f + 0.10f * snap, envelope);
                rightArm.zRot = blend(rightArm.zRot, 0.42f, envelope);
                leftArm.zRot = blend(leftArm.zRot, -0.42f, envelope);
                rightLeg.xRot -= 0.06f * snap * envelope;
                leftLeg.xRot -= 0.06f * snap * envelope;
            }
        }

        head.xRot = Mth.clamp(head.xRot, -0.6f, 0.45f);
        head.yRot = Mth.clamp(head.yRot, -0.75f, 0.75f);
        head.zRot = Mth.clamp(head.zRot, -0.4f, 0.4f);
    }

    private static float envelope(float progress) {
        float eased = Mth.sin(Mth.clamp(progress, 0.0f, 1.0f) * Mth.PI);
        return eased * eased;
    }

    private static float blend(float base, float target, float amount) {
        return Mth.lerp(Mth.clamp(amount, 0.0f, 1.0f), base, target);
    }

    private static float snap(float progress) {
        float early = 1.0f - Mth.clamp(progress / 0.25f, 0.0f, 1.0f);
        return early * early;
    }
}
