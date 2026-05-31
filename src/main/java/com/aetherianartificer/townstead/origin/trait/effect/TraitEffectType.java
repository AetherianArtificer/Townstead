package com.aetherianartificer.townstead.origin.trait.effect;

import com.google.gson.JsonElement;

/**
 * A code-backed entry in the trait effect palette. A trait JSON lists effects as
 * {@code [{ "<key>": <config> }]}; the key names a registered effect type. Effects
 * are declarative capabilities the engine queries (Apoli-style) — e.g. the
 * immortality logic asks {@code TraitEffects.isImmortal}. New behaviors register a
 * new type here (and the code that queries it); data authors then compose traits
 * from the palette.
 *
 * <p>Register implementations once at startup via {@link TraitEffectTypes#register}.</p>
 */
public interface TraitEffectType {

    /** Wire key matched against an effect entry's property (e.g. {@code life.immortal}). */
    String key();

    /** Validate this effect's JSON config at load; return {@code false} to reject it. */
    default boolean validate(JsonElement config) {
        return true;
    }
}
