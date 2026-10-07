package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.root.Demonym;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

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
 *
 * <p>A culture with a {@code parent} is a subculture: the customs of one people inside the parent,
 * such as Rosaguarda's merchant houses. Players see only the parent. The loader fills every field a
 * subculture leaves unset from its parent, so {@code displayName} is always the root's name.
 * {@code spirit} weights the Community Spirit axes that draw people toward it, and {@code forms} are
 * the founding profiles its towns use.</p>
 */
public record Culture(ResourceLocation id,
                      Component displayName,
                      @Nullable ResourceLocation namingTradition,
                      @Nullable ResourceLocation settlementNames,
                      CultureClothing clothing,
                      @Nullable ResourceLocation factionNames,
                      @Nullable Demonym demonym,
                      @Nullable ResourceLocation parent,
                      Map<String, Float> spirit,
                      List<Form> forms) {

    /**
     * A founding profile this culture's towns use, and how often. A subculture lists the government
     * forms its towns found; the same form may appear under several subcultures.
     */
    public record Form(ResourceLocation profile, float weight) {}

    public Culture(ResourceLocation id, Component displayName, @Nullable ResourceLocation namingTradition,
                   @Nullable ResourceLocation settlementNames, CultureClothing clothing,
                   @Nullable ResourceLocation factionNames, @Nullable Demonym demonym) {
        this(id, displayName, namingTradition, settlementNames, clothing, factionNames, demonym, null, Map.of(), List.of());
    }

    public Culture(ResourceLocation id, Component displayName, @Nullable ResourceLocation namingTradition,
                   @Nullable ResourceLocation settlementNames, CultureClothing clothing,
                   @Nullable ResourceLocation factionNames) {
        this(id, displayName, namingTradition, settlementNames, clothing, factionNames, null);
    }

    public Culture(ResourceLocation id, Component displayName, ResourceLocation namingTradition,
                   ResourceLocation settlementNames, CultureClothing clothing) {
        this(id, displayName, namingTradition, settlementNames, clothing, null);
    }

    public Culture {
        if (clothing == null) clothing = CultureClothing.NONE;
        spirit = spirit == null ? Map.of() : Map.copyOf(spirit);
        forms = forms == null ? List.of() : List.copyOf(forms);
    }

    /** A subculture: a people inside {@link #parent}, sharing its identity and inheriting what it leaves unset. */
    public boolean isSubculture() {
        return parent != null;
    }

    public Culture(ResourceLocation id, Component displayName, @Nullable ResourceLocation namingTradition) {
        this(id, displayName, namingTradition, null, CultureClothing.NONE);
    }

    public Culture(ResourceLocation id, Component displayName, @Nullable ResourceLocation namingTradition,
                   CultureClothing clothing,
                      @Nullable ResourceLocation factionNames) {
        this(id, displayName, namingTradition, null, clothing);
    }

    public boolean hasNamingTradition() {
        return namingTradition != null;
    }
}
