package com.aetherianartificer.townstead.culture;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A civic culture: what a community values and how it names its people.
 *
 * <p>Civic, never ethnic. An elf, a human and a dwarf in the same town share that town's culture,
 * and a culture is inherited from the people who raise you and the place you grow up, not from
 * your species. A Root only biases which culture a founder is born into, and never decides it for
 * anyone with parents or a home.</p>
 *
 * <p>This version carries identity and naming. Values, sacred and taboo things, preferences,
 * rituals, and how events, education and migration reshape them are Customs, and are meant to
 * arrive as further fields on this same record rather than as a second type.</p>
 *
 * <p>A culture declares nothing about which mod supplies its names. Its tradition references name
 * lists, and a reference carries its own namespace, so a culture built on another mod's names says
 * so exactly once and in the place the names are actually used.</p>
 */
public record Culture(ResourceLocation id,
                      Component displayName,
                      @Nullable ResourceLocation namingTradition) {

    public boolean hasNamingTradition() {
        return namingTradition != null;
    }
}
