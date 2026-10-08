package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Shared client-facing names for vanilla, MCA, and data-defined villager professions. */
public final class ProfessionDisplayNames {
    private ProfessionDisplayNames() {}

    public static Component component(ResourceLocation id) {
        if (id == null) return Component.empty();

        // Villager professions use Minecraft's entity translation domain even when the
        // profession itself belongs to another namespace.
        String standardKey = "entity.minecraft.villager."
                + id.getNamespace() + "." + id.getPath();
        Component standard = Component.translatable(standardKey);
        if (!standard.getString().equals(standardKey)) return standard;

        // MCA and a few older integrations use their own entity namespace.
        String legacyKey = "entity." + id.getNamespace() + ".villager." + id.getPath();
        Component legacy = Component.translatable(legacyKey);
        if (!legacy.getString().equals(legacyKey)) return legacy;

        ProfessionDef def = ProfessionDefs.byId(id);
        if (def != null && def.displayName() != null) return def.displayName();
        return Component.literal(fallback(id.getPath()));
    }

    static String fallback(String path) {
        if (path == null || path.isBlank()) return "";
        String spaced = path.replace('_', ' ').replace('-', ' ').trim();
        StringBuilder out = new StringBuilder(spaced.length());
        boolean wordStart = true;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            if (Character.isWhitespace(c)) {
                out.append(c);
                wordStart = true;
            } else {
                out.append(wordStart ? Character.toUpperCase(c) : Character.toLowerCase(c));
                wordStart = false;
            }
        }
        return out.toString();
    }
}
