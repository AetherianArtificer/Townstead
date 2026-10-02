package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.aetherianartificer.townstead.switchboard.WorldKeys;
import net.minecraft.resources.ResourceLocation;

/** Whether founders may be raised in a culture in this world, and how often, from the Switchboard. */
public final class CultureRules {
    private CultureRules() {}

    public static boolean enabled(String cultureId) {
        return (Boolean) Switchboard.content(WorldKeys.cultureOn(canonical(cultureId)));
    }

    public static double rate(String cultureId) {
        return (Double) Switchboard.content(WorldKeys.cultureRate(canonical(cultureId)));
    }

    private static String canonical(String cultureId) {
        ResourceLocation id = Cultures.canonicalId(ResourceLocation.tryParse(cultureId));
        return id == null ? cultureId : id.toString();
    }
}
