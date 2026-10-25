package com.aetherianartificer.townstead.client.gui.credits;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Thanks to the people in the community who help make Townstead, opened from the mod config screen. */
public final class CreditsScreen extends Screen {
    private record Group(String key, List<String> names) {}

    private static final List<Group> GROUPS = List.of(
            new Group("townstead.credits.visual_design", List.of("Wiggles Aranea")),
            new Group("townstead.credits.quality_assurance",
                    List.of("trustworthysource (S q u i d)", "Auk", "Anihacc", "Wiggles Aranea")),
            new Group("townstead.credits.community", List.of("Wiggles Aranea", "XainenVoltaire")));

    private final Screen parent;

    public CreditsScreen(Screen parent) {
        super(Component.translatable("townstead.credits.title"));
        this.parent = parent;
    }

    public static Button button(Screen parent, int x, int y, int width) {
        return Button.builder(Component.translatable("townstead.credits.open"),
                        b -> net.minecraft.client.Minecraft.getInstance().setScreen(new CreditsScreen(parent)))
                .bounds(x, y, width, 20).build();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //? if <1.21 {
        /*renderBackground(g);
        *///?}
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int y = 20;
        g.drawCenteredString(font, title, cx, y, 0xFFFFFF);
        y += 18;
        for (FormattedCharSequence line : font.split(Component.translatable("townstead.credits.intro"), Math.min(300, width - 32))) {
            g.drawCenteredString(font, line, cx, y, 0xCCCCCC);
            y += 10;
        }
        y += 10;
        for (Group group : GROUPS) {
            g.drawCenteredString(font, Component.translatable(group.key()), cx, y, 0xFFD966);
            y += 12;
            for (String name : group.names()) {
                g.drawCenteredString(font, name, cx, y, 0xFFFFFF);
                y += 10;
            }
            y += 8;
        }
    }
}
