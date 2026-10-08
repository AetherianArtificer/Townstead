package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.TownsteadConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Focused, live-preview editor for the global resource HUD presentation. */
public final class ResourceHudConfigScreen extends Screen {
    private static final int GAP = 6;
    private static final int ROW_H = 20;

    private final Screen parent;
    private final Values openedWith;

    private ResourceHudConfigScreen(Screen parent, Values openedWith) {
        super(Component.translatable("townstead.resource_hud.config.title"));
        this.parent = parent;
        this.openedWith = openedWith;
    }

    public static ResourceHudConfigScreen create(Screen parent) {
        return new ResourceHudConfigScreen(parent, Values.read());
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(440, width - 24);
        int left = (width - panelWidth) / 2;
        int columnWidth = (panelWidth - GAP) / 2;
        int previewTop = 31;
        int previewHeight = height >= 285 ? 78 : 54;
        int controlsTop = previewTop + previewHeight + 8;
        int rowStep = ROW_H + 3;

        addRenderableWidget(enumButton(left, controlsTop, columnWidth,
                "townstead.resource_hud.config.anchor", ResourceHudConfig.anchor(),
                TownsteadConfig.ResourceHudAnchor.values(), TownsteadConfig.RESOURCE_HUD_ANCHOR::set));
        addRenderableWidget(enumButton(left + columnWidth + GAP, controlsTop, columnWidth,
                "townstead.resource_hud.config.visibility", ResourceHudConfig.visibility(),
                TownsteadConfig.ResourceHudVisibility.values(), TownsteadConfig.RESOURCE_HUD_VISIBILITY::set));

        addRenderableWidget(enumButton(left, controlsTop + rowStep, columnWidth,
                "townstead.resource_hud.config.stack", ResourceHudConfig.stack(),
                TownsteadConfig.ResourceHudStack.values(), TownsteadConfig.RESOURCE_HUD_STACK::set));
        addRenderableWidget(toggleButton(left + columnWidth + GAP, controlsTop + rowStep, columnWidth,
                "townstead.resource_hud.config.values", ResourceHudConfig.showValues(),
                TownsteadConfig.RESOURCE_HUD_SHOW_VALUES::set));

        addRenderableWidget(slider(left, controlsTop + rowStep * 2, columnWidth,
                ResourceHudConfig.offsetX(), -256, 256,
                value -> Component.translatable("townstead.resource_hud.config.offset_x", Math.round(value)),
                value -> TownsteadConfig.RESOURCE_HUD_OFFSET_X.set((int) Math.round(value))));
        addRenderableWidget(slider(left + columnWidth + GAP, controlsTop + rowStep * 2, columnWidth,
                ResourceHudConfig.offsetY(), -256, 256,
                value -> Component.translatable("townstead.resource_hud.config.offset_y", Math.round(value)),
                value -> TownsteadConfig.RESOURCE_HUD_OFFSET_Y.set((int) Math.round(value))));

        addRenderableWidget(slider(left, controlsTop + rowStep * 3, columnWidth,
                ResourceHudConfig.scale(), 0.5, 3.0,
                value -> Component.translatable("townstead.resource_hud.config.scale", String.format("%.2f", value)),
                value -> TownsteadConfig.RESOURCE_HUD_SCALE.set(round(value, 0.05))));
        addRenderableWidget(slider(left + columnWidth + GAP, controlsTop + rowStep * 3, columnWidth,
                ResourceHudConfig.holdTicks(), 0, 1200,
                value -> Component.translatable("townstead.resource_hud.config.hold", ticksToSeconds(value)),
                value -> TownsteadConfig.RESOURCE_HUD_HOLD_TICKS.set((int) Math.round(value / 20d) * 20)));

        addRenderableWidget(slider(left, controlsTop + rowStep * 4, columnWidth,
                ResourceHudConfig.fadeTicks(), 0, 200,
                value -> Component.translatable("townstead.resource_hud.config.fade", ticksToSeconds(value)),
                value -> TownsteadConfig.RESOURCE_HUD_FADE_TICKS.set((int) Math.round(value / 5d) * 5)));
        addRenderableWidget(enumButton(left + columnWidth + GAP, controlsTop + rowStep * 4, columnWidth,
                "townstead.resource_hud.config.exit_style", ResourceHudConfig.exitStyle(),
                TownsteadConfig.ResourceHudExitStyle.values(), TownsteadConfig.RESOURCE_HUD_EXIT_STYLE::set));

        int bottomY = height - 27;
        int buttonCount = 3;
        int bottomGap = 4;
        int bottomWidth = Math.min(panelWidth, buttonCount * 96 + (buttonCount - 1) * bottomGap);
        int buttonWidth = (bottomWidth - (buttonCount - 1) * bottomGap) / buttonCount;
        int buttonX = (width - bottomWidth) / 2;

        addRenderableWidget(Button.builder(Component.translatable("townstead.resource_hud.config.reset"), b -> {
            Values.defaults().apply();
            refreshWidgets();
        }).bounds(buttonX, bottomY, buttonWidth, ROW_H).build());
        buttonX += buttonWidth + bottomGap;

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> cancel())
                .bounds(buttonX, bottomY, buttonWidth, ROW_H).build());
        buttonX += buttonWidth + bottomGap;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> done())
                .bounds(buttonX, bottomY, buttonWidth, ROW_H).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.21's Screen.render() calls renderBackground() itself. Render the superclass first so
        // its blur is complete before the sharp preview is composited; calling renderBackground()
        // here and super.render() below would run a second blur over the preview.
        //? if neoforge {
        super.render(graphics, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        *///?}
        int panelWidth = Math.min(440, width - 24);
        int left = (width - panelWidth) / 2;
        int previewTop = 31;
        int previewHeight = height >= 285 ? 78 : 54;

        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("townstead.resource_hud.config.preview_samples"),
                width / 2, 22, 0xFFADB5C2);
        graphics.fill(left, previewTop, left + panelWidth, previewTop + previewHeight, 0xB010131A);
        graphics.fill(left, previewTop, left + panelWidth, previewTop + 1, 0xFF566273);
        graphics.fill(left, previewTop + previewHeight - 1, left + panelWidth, previewTop + previewHeight, 0xFF252B35);
        ResourceHudOverlay.renderPreview(graphics, left + 3, previewTop + 3,
                panelWidth - 6, previewHeight - 6);
        if (ResourceHudConfig.visibility() == TownsteadConfig.ResourceHudVisibility.NEVER) {
            graphics.drawCenteredString(font, Component.translatable("townstead.resource_hud.config.preview_hidden"),
                    width / 2, previewTop + previewHeight / 2 - 4, 0xFFFFC56D);
        }
    }

    @Override
    public void onClose() {
        cancel();
    }

    private void done() {
        save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private void cancel() {
        openedWith.apply();
        save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private static void save() {
        try { TownsteadConfig.CLIENT_SPEC.save(); }
        catch (Exception ignored) { /* An unloaded test config has nowhere to save. */ }
    }

    private void refreshWidgets() {
        clearWidgets();
        init();
    }

    private static <E extends Enum<E>> Button enumButton(int x, int y, int width, String labelKey,
                                                          E initial, E[] values,
                                                          java.util.function.Consumer<E> setter) {
        final Object[] current = {initial};
        return Button.builder(enumLabel(labelKey, initial), button -> {
            @SuppressWarnings("unchecked") E old = (E) current[0];
            E next = values[(old.ordinal() + 1) % values.length];
            current[0] = next;
            setter.accept(next);
            button.setMessage(enumLabel(labelKey, next));
        }).bounds(x, y, width, ROW_H).build();
    }

    private static Button toggleButton(int x, int y, int width, String labelKey, boolean initial,
                                       java.util.function.Consumer<Boolean> setter) {
        final boolean[] current = {initial};
        return Button.builder(toggleLabel(labelKey, initial), button -> {
            current[0] = !current[0];
            setter.accept(current[0]);
            button.setMessage(toggleLabel(labelKey, current[0]));
        }).bounds(x, y, width, ROW_H).build();
    }

    private static ConfigSlider slider(int x, int y, int width, double initial, double min, double max,
                                       DoubleFunction<Component> label,
                                       DoubleConsumer setter) {
        return new ConfigSlider(x, y, width, initial, min, max, label, setter);
    }

    private static Component enumLabel(String labelKey, Enum<?> value) {
        String valueKey = "townstead.resource_hud.config.value." + value.name().toLowerCase(java.util.Locale.ROOT);
        return Component.translatable(labelKey, Component.translatable(valueKey));
    }

    private static Component toggleLabel(String labelKey, boolean value) {
        return Component.translatable(labelKey,
                Component.translatable(value ? "options.on" : "options.off"));
    }

    private static String ticksToSeconds(double ticks) {
        return String.format("%.1fs", ticks / 20d);
    }

    private static double round(double value, double step) {
        return Math.round(value / step) * step;
    }

    private static final class ConfigSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final DoubleFunction<Component> label;
        private final DoubleConsumer setter;

        private ConfigSlider(int x, int y, int width, double initial, double min, double max,
                             DoubleFunction<Component> label, DoubleConsumer setter) {
            super(x, y, width, ROW_H, Component.empty(), normalize(initial, min, max));
            this.min = min;
            this.max = max;
            this.label = label;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (label != null) setMessage(label.apply(actual()));
        }

        @Override
        protected void applyValue() {
            if (setter != null) setter.accept(actual());
            updateMessage();
        }

        private double actual() { return min + value * (max - min); }

        private static double normalize(double value, double min, double max) {
            return Math.max(0d, Math.min(1d, (value - min) / (max - min)));
        }
    }

    private record Values(TownsteadConfig.ResourceHudAnchor anchor,
                          TownsteadConfig.ResourceHudVisibility visibility,
                          TownsteadConfig.ResourceHudStack stack,
                          TownsteadConfig.ResourceHudExitStyle exitStyle,
                           int offsetX, int offsetY, double scale, int holdTicks, int fadeTicks,
                           boolean showValues) {
        static Values read() {
            return new Values(ResourceHudConfig.anchor(), ResourceHudConfig.visibility(), ResourceHudConfig.stack(),
                    ResourceHudConfig.exitStyle(),
                    ResourceHudConfig.offsetX(), ResourceHudConfig.offsetY(), ResourceHudConfig.scale(),
                    ResourceHudConfig.holdTicks(), ResourceHudConfig.fadeTicks(),
                    ResourceHudConfig.showValues());
        }

        static Values defaults() {
            return new Values(TownsteadConfig.ResourceHudAnchor.PACK_DECIDED,
                    TownsteadConfig.ResourceHudVisibility.CONTEXTUAL, TownsteadConfig.ResourceHudStack.DOWN,
                    TownsteadConfig.ResourceHudExitStyle.FADE,
                    4, 4, 1d, 60, 10, true);
        }

        void apply() {
            TownsteadConfig.RESOURCE_HUD_ANCHOR.set(anchor);
            TownsteadConfig.RESOURCE_HUD_VISIBILITY.set(visibility);
            TownsteadConfig.RESOURCE_HUD_STACK.set(stack);
            TownsteadConfig.RESOURCE_HUD_EXIT_STYLE.set(exitStyle);
            TownsteadConfig.RESOURCE_HUD_OFFSET_X.set(offsetX);
            TownsteadConfig.RESOURCE_HUD_OFFSET_Y.set(offsetY);
            TownsteadConfig.RESOURCE_HUD_SCALE.set(scale);
            TownsteadConfig.RESOURCE_HUD_HOLD_TICKS.set(holdTicks);
            TownsteadConfig.RESOURCE_HUD_FADE_TICKS.set(fadeTicks);
            TownsteadConfig.RESOURCE_HUD_SHOW_VALUES.set(showValues);
        }
    }
}
