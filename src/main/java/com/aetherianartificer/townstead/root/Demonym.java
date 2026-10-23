package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * What members of an ancestry, lineage, heritage, assignment profile, or culture are called. {@code adjective} is
 * optional (e.g. "Elven" vs the noun "Elf"); callers fall back to
 * {@code singular} when it is null.
 */
public record Demonym(Component singular, Component plural, @Nullable Component adjective) {
    public Component adjectiveOrSingular() {
        return adjective != null ? adjective : singular;
    }

    /** The owner's {@code demonym} object, or null when it has none. {@code plural} defaults to {@code singular}. */
    public static @Nullable Demonym parse(JsonObject owner, String context, Map<String, String> langIndex) {
        if (!owner.has("demonym") || !owner.get("demonym").isJsonObject()) return null;
        JsonObject d = owner.getAsJsonObject("demonym");
        Component singular = DataPackLang.parseComponent(d.get("singular"), context + ".demonym.singular", langIndex);
        Component plural = d.has("plural")
                ? DataPackLang.parseComponent(d.get("plural"), context + ".demonym.plural", langIndex)
                : singular;
        Component adjective = d.has("adjective")
                ? DataPackLang.parseComponent(d.get("adjective"), context + ".demonym.adjective", langIndex)
                : null;
        return new Demonym(singular, plural, adjective);
    }
}
