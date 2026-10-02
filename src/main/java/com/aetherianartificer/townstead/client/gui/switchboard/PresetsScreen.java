package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.switchboard.SwitchboardPreset;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletionException;

/** Choose, save, share and import presets, laid out like the Superflat presets screen. */
final class PresetsScreen extends MenuBackgroundScreen {
    private final Screen home;
    private final SwitchboardModel model;
    private String field = "";
    @Nullable private Component status;
    private boolean statusIsError;
    private boolean loading;
    @Nullable private PresetList list;

    PresetsScreen(Screen home, SwitchboardModel model) {
        super(Component.translatable("townstead.switchboard.presets.title"));
        this.home = home;
        this.model = model;
    }

    @Override
    protected void init() {
        EditBox box = new EditBox(font, width / 2 - 155, 40, 246, 20,
                Component.translatable("townstead.switchboard.presets.field"));
        box.setMaxLength(65536);
        box.setHint(Component.translatable("townstead.switchboard.presets.field").withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(field);
        box.setResponder(text -> field = text);
        addRenderableWidget(box);
        Button importButton = Button.builder(Component.translatable("townstead.switchboard.presets.import"),
                b -> importField()).bounds(width / 2 + 95, 40, 60, 20).build();
        importButton.active = !loading;
        addRenderableWidget(importButton);

        list = new PresetList(minecraft, width, height - 64 - 80, 80);
        list.set(PresetStore.all(
                Component.translatable("townstead.switchboard.presets.defaults").getString(),
                Component.translatable("townstead.switchboard.presets.defaults.description").getString()));
        addRenderableWidget(list);

        int rowOne = height - 52;
        addRenderableWidget(Button.builder(Component.translatable("townstead.switchboard.presets.save"),
                b -> minecraft.setScreen(new PresetNameScreen(this, this::saveCurrent)))
                .bounds(width / 2 - 154, rowOne, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("townstead.switchboard.presets.copy"),
                b -> copyCode()).bounds(width / 2 - 50, rowOne, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("townstead.switchboard.presets.folder"),
                b -> openFolder()).bounds(width / 2 + 54, rowOne, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("townstead.switchboard.presets.use"),
                b -> useSelected()).bounds(width / 2 - 154, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(width / 2 + 4, height - 28, 150, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(home);
    }

    private void useSelected() {
        PresetStore.Listed listed = list == null ? null : list.selected();
        if (listed == null) return;
        review(listed.preset(), Component.translatable("townstead.switchboard.presets.from_preset", listed.preset().name()));
    }

    private void review(SwitchboardPreset preset, Component source) {
        minecraft.setScreen(new PresetReviewScreen(model, home, this, preset, source));
    }

    private void importField() {
        String text = field.trim();
        if (text.isEmpty() || loading) return;
        if (!PresetFetcher.looksLikeLink(text)) {
            try {
                review(SwitchboardPreset.parse(text, Component.translatable("townstead.switchboard.presets.imported").getString()),
                        Component.translatable("townstead.switchboard.presets.from_code"));
            } catch (IllegalArgumentException e) {
                showError("townstead.switchboard.presets.error.invalid");
            }
            return;
        }
        loading = true;
        setStatus(Component.translatable("townstead.switchboard.presets.loading"), false);
        PresetFetcher.fetch(text).whenComplete((preset, error) -> minecraft.execute(() -> {
            loading = false;
            if (minecraft.screen != this) return;
            if (preset != null) {
                status = null;
                review(preset, Component.translatable("townstead.switchboard.presets.from_link", host(text)));
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            PresetFetcher.Failure failure = cause instanceof PresetFetcher.FetchException fe
                    ? fe.failure : PresetFetcher.Failure.UNREACHABLE;
            showError(switch (failure) {
                case NOT_HTTPS -> "townstead.switchboard.presets.error.https";
                case NOT_A_PRESET -> "townstead.switchboard.presets.error.invalid";
                case UNREACHABLE -> "townstead.switchboard.presets.error.unreachable";
            });
        }));
    }

    private static String host(String link) {
        try {
            String host = java.net.URI.create(link.trim()).getHost();
            return host == null ? link : host;
        } catch (IllegalArgumentException e) {
            return link;
        }
    }

    private void saveCurrent(String name) {
        try {
            PresetStore.save(model.snapshot(name));
            setStatus(Component.translatable("townstead.switchboard.presets.saved", name), false);
        } catch (IOException e) {
            Townstead.LOGGER.warn("[Switchboard] Could not save preset", e);
            showError("townstead.switchboard.presets.error.save");
        }
    }

    private void copyCode() {
        minecraft.keyboardHandler.setClipboard(model.snapshot("").toShareCode());
        setStatus(Component.translatable("townstead.switchboard.presets.copied"), false);
    }

    private void openFolder() {
        try {
            Files.createDirectories(PresetStore.savedDirectory());
            Util.getPlatform().openFile(PresetStore.savedDirectory().toFile());
        } catch (IOException e) {
            Townstead.LOGGER.warn("[Switchboard] Could not open the presets folder", e);
        }
    }

    private void showError(String key) {
        setStatus(Component.translatable(key), true);
    }

    private void setStatus(Component text, boolean error) {
        status = text;
        statusIsError = error;
        rebuildWidgets();
    }

    //? if >=1.21 {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawText(g);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        drawText(g);
    }
    *///?}

    private void drawText(GuiGraphics g) {
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawString(font, Component.translatable("townstead.switchboard.presets.field"),
                width / 2 - 155, 29, 0xA0A0A0, false);
        if (status != null) {
            g.drawCenteredString(font, status, width / 2, 66, statusIsError ? 0xFF5555 : 0x55FF55);
        }
    }
}
