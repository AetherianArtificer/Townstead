package com.aetherianartificer.townstead.livery;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * One way a culture's people look in their gear: samurai armour, plate, lamellar, bamboo. A style
 * is data; its art is a resource pack's, found by convention under
 * {@code assets/<ns>/textures/livery/<style>/}. Nothing about the gear itself changes.
 *
 * <p>{@code tint} marks art drawn in greys to be coloured by the wearer's primary colour.
 * {@code trims} maps an armour slot ({@code head}, {@code chest}, {@code legs}, {@code feet}) to the
 * vanilla trim drawn over it.</p>
 */
public record LiveryStyle(ResourceLocation id, Component name, boolean tint, int primary, int secondary,
                          Map<String, LiveryView.Trim> trims) {
    public LiveryStyle {
        trims = Map.copyOf(trims);
    }
}
