package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.state.*;
import net.minecraft.resources.ResourceLocation;

/** Naming changes the faction's public name, never its settlement, stable id, or kind. */
public final class CharterIdentityService {
    private CharterIdentityService() {}

    /** Compare the name reviewed by the player; unrelated record updates need not invalidate naming. */
    public static String rename(PoliticalSavedData data, ResourceLocation id, String expected, String requested) {
        var faction = data.faction(id);
        if (faction == null) return "unavailable";
        if (!faction.name().equals(expected)) return "stale";
        String name = normalize(requested);
        if (name == null) return "invalid";
        data.putFaction(faction.withName(name));
        data.putFactionName(faction.id(), com.aetherianartificer.townstead.culture.FactionNaming.Name.custom(name));
        return "saved";
    }

    public static String normalize(String raw) {
        if (raw == null || raw.length() > 48) return null;
        for (int i = 0; i < raw.length(); i++) if (Character.isISOControl(raw.charAt(i)) || raw.charAt(i) == '§') return null;
        String name = raw.strip().replaceAll("\\s+", " ");
        return name.length() < 2 ? null : name;
    }
}
