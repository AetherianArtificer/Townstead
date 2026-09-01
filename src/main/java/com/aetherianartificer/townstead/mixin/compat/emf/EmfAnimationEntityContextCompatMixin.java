package com.aetherianartificer.townstead.mixin.compat.emf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Conditional anchor for the EMF 3.3 / Emotecraft 2.4 binary bridge installed by
 * {@code TownsteadMixinPlugin}. It deliberately carries no direct EMF dependency.
 */
@Pseudo
@Mixin(targets = "traben.entity_model_features.models.animation.EMFAnimationEntityContext",
        remap = false)
public abstract class EmfAnimationEntityContextCompatMixin {
}
