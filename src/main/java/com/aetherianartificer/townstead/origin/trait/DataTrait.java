package com.aetherianartificer.townstead.origin.trait;

import com.google.gson.JsonElement;
import net.minecraft.util.GsonHelper;

import java.util.Map;

/**
 * A data-pack-loaded trait (from {@code data/<ns>/trait/<path>.json}) that is
 * bridged into MCA's own {@code Traits} registry — so it shows in MCA's editor,
 * inherits, randomizes, syncs, and persists exactly like a built-in MCA trait.
 * Townstead only adds the declarative, Apoli-style {@code effects} (a capability
 * map the engine queries, e.g. {@code life.immortal}); MCA owns membership/storage.
 *
 * <p>{@link #chance}/{@link #inherit}/{@link #usableOnPlayer} are the MCA trait
 * registration params; {@code chance 0} means it never random-rolls (origin/potion
 * grant it instead), {@code inherit 1.0} means it always passes to children.</p>
 */
public record DataTrait(
        String id,
        float chance,
        float inherit,
        boolean usableOnPlayer,
        boolean hidden,
        Map<String, JsonElement> effects
) {
    /** Read a boolean capability from this trait's effects (e.g. {@code life.immortal}). */
    public boolean flag(String key, boolean fallback) {
        JsonElement v = effects.get(key);
        return v == null ? fallback : GsonHelper.convertToBoolean(v, key);
    }

    public boolean has(String key) {
        return effects.containsKey(key);
    }
}
