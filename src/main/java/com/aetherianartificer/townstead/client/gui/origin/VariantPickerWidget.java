package com.aetherianartificer.townstead.client.gui.origin;

import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * A native-styled {@code "< Label >"} cycler for an appearance-slot option, built on MCA's own
 * button so it matches the editor's other controls. Clicking the left half steps back, the right
 * half forward. The label is set by the caller (via {@link #setMessage}). This is the template
 * control for the appearance editor (tone now; face, hair, markings later).
 */
public class VariantPickerWidget extends ButtonWidget {

    private final IntConsumer onCycle;   // -1 = previous, +1 = next

    public VariantPickerWidget(int x, int y, int width, int height, Component message, IntConsumer onCycle) {
        super(x, y, width, height, message, b -> { });
        this.onCycle = onCycle;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onCycle.accept(mouseX < getX() + this.width / 2.0 ? -1 : 1);
    }
}
