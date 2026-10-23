package com.aetherianartificer.townstead.client.gui.ability;

import com.aetherianartificer.townstead.assign.Assignable;
import net.minecraft.client.resources.language.I18n;

/** Client-side reading of an ability's flavour text as the server sent it. */
final class AbilityText {
    private AbilityText() {}

    /** The text to show: a marked translation key resolved here, so a mod's own language files apply. */
    static String description(String wire) {
        if (wire == null || wire.isEmpty()) return "";
        if (!wire.startsWith(Assignable.DESCRIPTION_KEY)) return wire;
        String key = wire.substring(Assignable.DESCRIPTION_KEY.length());
        return I18n.exists(key) ? I18n.get(key) : "";
    }
}
