package com.aetherianartificer.townstead.client.animation;

import java.util.List;

/**
 * Source-specific animation readers implement this contract. The bridge stays
 * model-agnostic; adapters only emit named target transforms.
 */
public interface AnimationSourceAdapter {
    String id();

    boolean isAvailable();

    List<AnimationTransform> collectTransforms(AnimationSourceContext context);
}
