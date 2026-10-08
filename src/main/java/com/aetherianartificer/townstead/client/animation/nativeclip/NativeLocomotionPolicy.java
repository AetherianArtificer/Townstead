package com.aetherianartificer.townstead.client.animation.nativeclip;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Set;

/** Explicit mobile reactions; static keyframes can still be intentional full-body poses. */
public final class NativeLocomotionPolicy {
    private static final Set<String> MOBILE_REACTIONS = Set.of(
            "shiver", "sweat", "cry", "laugh", "laugh_demure");

    private NativeLocomotionPolicy() {}

    /** Preserve the standing pose, then smoothly release legs/root as the gait ramps up. */
    public static float lowerBodyWeight(ResourceLocation clip, float limbDistance) {
        if (!clip.getNamespace().equals("townstead_performance")
                || !MOBILE_REACTIONS.contains(clip.getPath())) return 1F;
        return 1F - Mth.clamp(limbDistance / .1F, 0F, 1F);
    }
}
