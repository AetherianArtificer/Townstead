package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Debug-only adapter that proves the MCA hook, target map, and applier are
 * live without standing in for any real source integration.
 */
public final class DebugAnimationSourceAdapter implements AnimationSourceAdapter {
    private boolean logged;

    @Override
    public String id() {
        return "debug";
    }

    @Override
    public boolean isAvailable() {
        return TownsteadConfig.DEBUG_VILLAGER_AI.get();
    }

    @Override
    public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        if (!logged) {
            logged = true;
            Townstead.LOGGER.info("[AnimationBridge] source=debug available=true action=apply");
        }

        float wave = Mth.sin(context.animationProgress() * 0.2F) * 0.45F;
        return List.of(
                AnimationTransform.rotate("right_arm", wave, 0.0F, 0.0F, AnimationTransform.Operation.ADD)
        );
    }
}
