package com.aetherianartificer.townstead.client.gui.rebirth;

import com.aetherianartificer.townstead.client.rebirth.CharacterNameClient;
import com.aetherianartificer.townstead.rebirth.RebirthRequestC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Explains what rebirth keeps and loses before the player respawns as someone new. Destiny names them. */
public final class RebirthScreen extends Screen {
    private static final int TEXT_WIDTH = 260;

    private final Screen deathScreen;
    private MultiLineLabel body = MultiLineLabel.EMPTY;

    public RebirthScreen(Screen deathScreen) {
        super(Component.translatable("townstead.rebirth.title"));
        this.deathScreen = deathScreen;
    }

    @Override
    protected void init() {
        String current = minecraft.player == null ? ""
                : java.util.Objects.requireNonNullElse(CharacterNameClient.get(minecraft.player.getUUID()),
                        minecraft.player.getGameProfile().getName());
        body = MultiLineLabel.create(font, Component.translatable("townstead.rebirth.body", current), TEXT_WIDTH);
        int top = height / 2 - 50;
        addRenderableWidget(Button.builder(Component.translatable("townstead.rebirth.confirm"), b -> begin())
                .bounds(width / 2 - 100, top + 56, 200, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(width / 2 - 100, top + 80, 200, 20).build());
    }

    private void begin() {
        if (minecraft.player == null) return;
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new RebirthRequestC2SPayload(false));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(new RebirthRequestC2SPayload(false));
        *///?}
        // The same order as the death screen's own Respawn: back to it, then respawn.
        minecraft.setScreen(deathScreen);
        minecraft.player.respawn();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(deathScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x60500000, 0xA0803030);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawText(g);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x60500000, 0xA0803030);
        super.render(g, mouseX, mouseY, partialTick);
        drawText(g);
    }
    *///?}

    private void drawText(GuiGraphics g) {
        int top = height / 2 - 50;
        g.pose().pushPose();
        g.pose().scale(1.5f, 1.5f, 1.5f);
        g.drawCenteredString(font, title, (int) (width / 2 / 1.5f), (int) ((top - 30) / 1.5f), 0xFFFFFF);
        g.pose().popPose();
        body.renderCentered(g, width / 2, top);
    }
}
