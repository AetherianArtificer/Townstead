package com.aetherianartificer.townstead.client.gui.rebirth;

import com.aetherianartificer.townstead.client.rebirth.CharacterNameClient;
import com.aetherianartificer.townstead.rebirth.Rebirth;
import com.aetherianartificer.townstead.rebirth.RebirthRequestC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Names the new person before the player respawns as them. */
public final class RebirthScreen extends Screen {
    private static final int TEXT_WIDTH = 260;

    private final Screen deathScreen;
    private String name = "";
    private Button confirm;
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
        EditBox box = new EditBox(font, width / 2 - 100, top + 50, 200, 20, Component.translatable("townstead.rebirth.name"));
        box.setMaxLength(Rebirth.MAX_NAME_LENGTH);
        box.setHint(Component.translatable("townstead.rebirth.name"));
        box.setValue(name);
        box.setResponder(text -> {
            name = text;
            confirm.active = Rebirth.cleanName(name) != null;
        });
        addRenderableWidget(box);
        setInitialFocus(box);
        confirm = addRenderableWidget(Button.builder(Component.translatable("townstead.rebirth.confirm"), b -> begin())
                .bounds(width / 2 - 100, top + 80, 200, 20).build());
        confirm.active = Rebirth.cleanName(name) != null;
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(width / 2 - 100, top + 104, 200, 20).build());
    }

    private void begin() {
        String clean = Rebirth.cleanName(name);
        if (clean == null || minecraft.player == null) return;
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new RebirthRequestC2SPayload(clean));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(new RebirthRequestC2SPayload(clean));
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
