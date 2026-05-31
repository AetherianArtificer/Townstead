package com.aetherianartificer.townstead.client.gui.life;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * Replacement for MCA's editor Age slider on the General tab. A thin 0..1 slider:
 * the editor mixin decides what the position means (biological age for a mortal,
 * frozen stage index for an immortal). The current stage name is rendered on the
 * slider itself via {@code labelFn} (so there is no overlapping sibling widget),
 * and {@code onChange} drives the live model preview and the stored edit.
 */
public final class LifeAgeSlider extends AbstractSliderButton {

    private final DoubleFunction<Component> labelFn;
    private final DoubleConsumer onChange;

    public LifeAgeSlider(int x, int y, int width, int height, double value,
                         DoubleFunction<Component> labelFn, DoubleConsumer onChange) {
        super(x, y, width, height, labelFn.apply(clamp01(value)), clamp01(value));
        this.labelFn = labelFn;
        this.onChange = onChange;
    }

    /** Current normalized position in {@code [0, 1]}. */
    public double sliderValue() {
        return value;
    }

    /** Move the handle without firing {@code onChange} (used to mirror the DOB picker). */
    public void setNormalizedValue(double v) {
        this.value = clamp01(v);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(labelFn.apply(value));
    }

    @Override
    protected void applyValue() {
        updateMessage();
        if (onChange != null) onChange.accept(value);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
