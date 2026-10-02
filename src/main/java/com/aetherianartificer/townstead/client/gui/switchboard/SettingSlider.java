package com.aetherianartificer.townstead.client.gui.switchboard;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** A vanilla slider over a ranged number setting. */
final class SettingSlider extends AbstractSliderButton {
    private final double min;
    private final double max;
    private final boolean integer;
    private final Consumer<Object> onChange;

    SettingSlider(int width, double min, double max, boolean integer, double current, Consumer<Object> onChange) {
        super(0, 0, width, 20, Component.empty(), (current - min) / (max - min));
        this.min = min;
        this.max = max;
        this.integer = integer;
        this.onChange = onChange;
        updateMessage();
    }

    private double current() {
        double v = min + value * (max - min);
        return integer ? Math.round(v) : Math.round(v * 100) / 100.0;
    }

    @Override
    protected void updateMessage() {
        double v = current();
        setMessage(Component.literal(integer ? String.valueOf((long) v) : String.valueOf(v)));
    }

    @Override
    protected void applyValue() {
        double v = current();
        onChange.accept(integer ? (Object) (int) v : (Object) v);
    }
}
