package com.aetherianartificer.townstead.client.gui.switchboard;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The presets to choose from, one per row with its name, where it comes from and a short description. */
final class PresetList extends ObjectSelectionList<PresetList.Row> {
    private static final int ROW_WIDTH = 310;

    //? if >=1.21 {
    PresetList(Minecraft mc, int width, int height, int y) {
        super(mc, width, height, y, 30);
    }
    //?} else {
    /*PresetList(Minecraft mc, int width, int height, int y) {
        super(mc, width, height, y, y + height, 30);
    }
    *///?}

    void set(List<PresetStore.Listed> presets) {
        clearEntries();
        for (PresetStore.Listed listed : presets) addEntry(new Row(this, listed));
        if (!children().isEmpty()) setSelected(children().get(0));
    }

    @Nullable PresetStore.Listed selected() {
        Row row = getSelected();
        return row == null ? null : row.listed;
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    @Override
    protected int getScrollbarPosition() {
        return getRowLeft() + ROW_WIDTH + 6;
    }

    static final class Row extends ObjectSelectionList.Entry<Row> {
        private final PresetList list;
        final PresetStore.Listed listed;

        Row(PresetList list, PresetStore.Listed listed) {
            this.list = list;
            this.listed = listed;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            Component tag = switch (listed.source()) {
                case MODPACK -> Component.translatable("townstead.switchboard.presets.source.modpack");
                case SAVED -> Component.translatable("townstead.switchboard.presets.source.saved");
                case BUILT_IN -> Component.empty();
            };
            g.drawString(font, listed.preset().name(), left + 3, top + 2, 0xFFFFFF, false);
            if (!tag.getString().isEmpty()) {
                g.drawString(font, tag.copy().withStyle(ChatFormatting.GRAY),
                        left + width - 4 - font.width(tag), top + 2, 0xFFFFFF, false);
            }
            Component description = listed.preset().description().isEmpty()
                    ? Component.translatable("townstead.switchboard.presets.count", listed.preset().values().size())
                    : Component.literal(listed.preset().description());
            List<FormattedCharSequence> lines = font.split(description, width - 6);
            if (!lines.isEmpty()) g.drawString(font, lines.get(0), left + 3, top + 14, 0x808080, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            list.setSelected(this);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(listed.preset().name());
        }
    }
}
