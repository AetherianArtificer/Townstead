package com.aetherianartificer.townstead.client.gui.ability;

import net.minecraft.client.gui.Font;

/**
 * Trimming text to the room it actually has.
 *
 * <p>Its own class because a catalogue is nothing but labels in boxes that are always slightly too
 * small, and a source name, an ability name and a detail line all want the same treatment. Cutting
 * without an ellipsis at these sizes: three dots cost as much room as the two characters they would
 * be standing in for.</p>
 */
final class RecordTrim {

    private RecordTrim() {}

    static String fit(Font font, String text, int room) {
        if (text == null || text.isEmpty() || room <= 0) return "";
        if (font.width(text) <= room) return text;
        String cut = text;
        while (cut.length() > 1 && font.width(cut) > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }
}
