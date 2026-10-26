package com.aetherianartificer.townstead.client.devlink;

import com.aetherianartificer.townstead.devlink.BenchLinkActionC2SPayload;
import com.aetherianartificer.townstead.devlink.BenchLinkStatusS2CPayload;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The Bench Link item's screen. Phase 0 shows the Server section (start/stop, port, sessions,
 * copy the token or a full connection blob for a hand-made connection) and the preview subject.
 * The game keeps running while it is open, so a connected Blockbench sees live state.
 */
public class BenchLinkScreen extends Screen {

    private static final int WIDTH = 260;
    private static final int MUTED = 0xA0A0A0;
    private static final int TEXT = 0xFFFFFF;

    private BenchLinkStatusS2CPayload status;

    public BenchLinkScreen(BenchLinkStatusS2CPayload status) {
        super(Component.translatable("screen.townstead.bench_link.title"));
        this.status = status;
    }

    public void update(BenchLinkStatusS2CPayload next) {
        this.status = next;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = height / 2 - 70;
        if (status.running()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.townstead.bench_link.stop"),
                    b -> BenchLinkClient.send(BenchLinkActionC2SPayload.STOP)).bounds(left, top + 44, 84, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.townstead.bench_link.copy_token"),
                    b -> copy(status.token())).bounds(left + 88, top + 44, 84, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.townstead.bench_link.copy_connection"),
                    b -> copy(connection())).bounds(left + 176, top + 44, 84, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("screen.townstead.bench_link.start"),
                    b -> BenchLinkClient.send(BenchLinkActionC2SPayload.START)).bounds(left, top + 44, 120, 20).build());
        }
        if (status.subjectId() >= 0) {
            addRenderableWidget(Button.builder(Component.translatable("screen.townstead.bench_link.clear_subject"),
                    b -> BenchLinkClient.send(BenchLinkActionC2SPayload.CLEAR_SUBJECT)).bounds(left + 176, top + 92, 84, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(left + WIDTH / 2 - 50, top + 150, 100, 20).build());
    }

    private String connection() {
        JsonObject json = new JsonObject();
        json.addProperty("port", status.port());
        json.addProperty("token", status.token());
        return json.toString();
    }

    private void copy(String text) {
        if (minecraft == null) return;
        minecraft.keyboardHandler.setClipboard(text);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("screen.townstead.bench_link.copied"), true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if neoforge {
        super.render(graphics, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        *///?}
        int left = (width - WIDTH) / 2;
        int top = height / 2 - 70;
        graphics.drawString(font, title, left, top, TEXT);
        graphics.drawString(font, Component.translatable("screen.townstead.bench_link.server"), left, top + 18, MUTED);
        Component state = status.running()
                ? Component.translatable("screen.townstead.bench_link.running", status.port(), status.sessions())
                : Component.translatable("screen.townstead.bench_link.stopped");
        graphics.drawString(font, state, left, top + 30, TEXT);

        graphics.drawString(font, Component.translatable("screen.townstead.bench_link.subject"), left, top + 78, MUTED);
        Component subject = status.subjectId() >= 0
                ? Component.literal(status.subjectName().isEmpty() ? "#" + status.subjectId() : status.subjectName())
                : Component.translatable("screen.townstead.bench_link.no_subject");
        graphics.drawString(font, subject, left, top + 98, TEXT);

        graphics.drawString(font, Component.translatable("screen.townstead.bench_link.gizmo_hint"), left, top + 124, MUTED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
