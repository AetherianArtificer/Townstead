package com.aetherianartificer.townstead.origin.disposition;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Answers how {@code viewer} regards {@code other}. This is the seam the future faction system plugs
 * into: register an authoritative source and it overrides the natural default for the same question,
 * without the targeting hooks (the friendly change-target filter, a later hostile ticker) changing.
 * Return {@code null} to abstain and let a lower-priority source decide.
 */
public interface DispositionSource {
    @Nullable
    Disposition between(LivingEntity viewer, LivingEntity other);
}
