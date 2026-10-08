package com.aetherianartificer.townstead.client.gui.switchboard;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Asks for a name before saving the current settings as a preset. */
final class PresetNameScreen extends MenuBackgroundScreen {
    private final Screen back;
    private final Consumer<String> onSave;
    private String name = "";
    private Button save;

    PresetNameScreen(Screen back, Consumer<String> onSave) {
        super(Component.translatable("townstead.switchboard.presets.name.title"));
        this.back = back;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        EditBox box = new EditBox(font, width / 2 - 100, height / 2 - 10, 200, 20,
                Component.translatable("townstead.switchboard.presets.name"));
        box.setMaxLength(64);
        box.setValue(name);
        box.setResponder(text -> {
            name = text;
            save.active = !name.isBlank();
        });
        addRenderableWidget(box);
        setInitialFocus(box);
        save = addRenderableWidget(Button.builder(Component.translatable("townstead.switchboard.presets.name.save"), b -> {
            minecraft.setScreen(back);
            onSave.accept(name.trim());
        }).bounds(width / 2 - 102, height / 2 + 20, 100, 20).build());
        save.active = !name.isBlank();
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 + 2, height / 2 + 20, 100, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(back);
    }

    //? if >=1.21 {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFF);
    }
    *///?}
}
