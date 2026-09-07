package com.aetherianartificer.townstead.api.v1.client;

/**
 * One visible row of the dialogue choice panel, in screen coordinates. {@code index} is the
 * row's position among the panel's entries and is what {@code selectChoice} takes.
 */
public record ChoiceRow(
        int index,
        int x,
        int y,
        int width,
        int height,
        String label,
        boolean selected,
        boolean back,
        boolean subMenu
) {
}
