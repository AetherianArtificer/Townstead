package com.aetherianartificer.townstead.fatigue;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Data component for the energy restoration tooltip.
 * Carries the number of energy points to render as bolt icons.
 */
public record EnergyTooltipComponent(int amount) implements TooltipComponent {
}
