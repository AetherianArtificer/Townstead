package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.switchboard.WorldKeys;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.Function;

/** A vanilla slider that snaps to the spawn rate steps, with Normal in the middle. */
final class RateSlider extends AbstractSliderButton {
    private static final double[] STEPS = WorldKeys.RATES;
    private final Function<Component, Component> label;
    private final DoubleConsumer onChange;

    RateSlider(int width, double current, Function<Component, Component> label, DoubleConsumer onChange) {
        super(0, 0, width, 20, Component.empty(), position(nearest(current)));
        this.label = label;
        this.onChange = onChange;
        updateMessage();
    }

    static Component format(double rate) {
        return Math.abs(rate - 1.0) < 1e-9 ? Component.translatable("townstead.switchboard.rate.normal")
                : Component.literal("x" + (rate == Math.rint(rate) ? String.valueOf((long) rate) : String.valueOf(rate)));
    }

    private static int nearest(double rate) {
        int best = 0;
        for (int i = 1; i < STEPS.length; i++) {
            if (Math.abs(STEPS[i] - rate) < Math.abs(STEPS[best] - rate)) best = i;
        }
        return best;
    }

    private static double position(int index) {
        return index / (double) (STEPS.length - 1);
    }

    private int index() {
        return (int) Math.round(value * (STEPS.length - 1));
    }

    @Override
    protected void updateMessage() {
        setMessage(label.apply(format(STEPS[index()])));
    }

    @Override
    protected void applyValue() {
        value = position(index());
        onChange.accept(STEPS[index()]);
    }
}
