package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.switchboard.SwitchboardPreset;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Shows every value a preset would change before anything is applied. */
final class PresetReviewScreen extends MenuBackgroundScreen {
    private final SwitchboardModel model;
    private final Screen home;
    private final Screen back;
    private final SwitchboardPreset preset;
    private final Component source;
    private final SwitchboardModel.Preview preview;

    PresetReviewScreen(SwitchboardModel model, Screen home, Screen back, SwitchboardPreset preset, Component source) {
        super(Component.translatable("townstead.switchboard.review.title"));
        this.model = model;
        this.home = home;
        this.back = back;
        this.preset = preset;
        this.source = source;
        this.preview = model.preview(preset);
    }

    @Override
    protected void init() {
        SettingList list = new SettingList(minecraft, width, height - 36 - 44, 44);
        for (SwitchboardModel.Change change : preview.changes()) {
            list.add(new SettingList.Change(change.setting(), change.from(), change.to()));
        }
        if (preview.changes().isEmpty()) {
            list.add(new SettingList.Header(Component.translatable("townstead.switchboard.review.none")
                    .withStyle(ChatFormatting.GRAY)));
        }
        if (preview.unknown() > 0) {
            list.add(new SettingList.Header(Component.translatable("townstead.switchboard.review.skipped",
                    preview.unknown()).withStyle(ChatFormatting.GRAY)));
        }
        if (preview.locked() > 0) {
            list.add(new SettingList.Header(Component.translatable("townstead.switchboard.review.locked",
                    preview.locked()).withStyle(ChatFormatting.GRAY)));
        }
        addRenderableWidget(list);

        Component apply = preview.changes().isEmpty() ? CommonComponents.GUI_DONE
                : Component.translatable("townstead.switchboard.review.apply", preview.changes().size());
        addRenderableWidget(Button.builder(apply, b -> {
            if (!preview.changes().isEmpty()) model.apply(preset);
            minecraft.setScreen(home);
        }).bounds(width / 2 - 154, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(width / 2 + 4, height - 28, 150, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(back);
    }

    //? if >=1.21 {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawHeader(g);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        drawHeader(g);
    }
    *///?}

    private void drawHeader(GuiGraphics g) {
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, source.copy().withStyle(ChatFormatting.GRAY), width / 2, 26, 0xFFFFFF);
    }
}
