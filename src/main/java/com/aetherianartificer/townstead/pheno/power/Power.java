package com.aetherianartificer.townstead.pheno.power;

import net.minecraft.resources.ResourceLocation;

/**
 * A behavior granted to an entity: its {@link PowerComponent} plus the id it is keyed
 * by (for toggles, cooldowns and resources). For the genetics source the id is the
 * gene id; for the professions source it will be the skill/power id.
 */
public record Power(ResourceLocation id, PowerComponent component) {}
