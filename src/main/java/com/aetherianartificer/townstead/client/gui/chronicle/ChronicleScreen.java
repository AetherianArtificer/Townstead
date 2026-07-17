package com.aetherianartificer.townstead.client.gui.chronicle;

import com.aetherianartificer.townstead.client.chronicle.ChronicleClientStore;
import com.aetherianartificer.townstead.chronicle.net.ChroniclePageS2CPayload;
import com.aetherianartificer.townstead.chronicle.net.ChronicleQueryC2SPayload;
import com.aetherianartificer.townstead.chronicle.net.ChronicleShareNewsC2SPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The player-facing chronicle viewer: the village's remembered history, a
 * villager's knowledge (what THEY believe, with the channel labeled — never
 * the truth diff), and their memories. Pull-only paged queries; entries are
 * fully server-rendered.
 */
public class ChronicleScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PAD = 10;
    private static final int ROW_H = 20;
    private static final byte PAGE_SIZE = 16;

    private final @Nullable UUID villagerUuid;
    private final String villagerName;
    private final Screen parent;

    private byte mode = ChronicleQueryC2SPayload.MODE_VILLAGE;
    private final List<ChroniclePageS2CPayload.EntryView> entries = new ArrayList<>();
    private boolean hasMore;
    private boolean loading;
    private int pendingRequestId = -1;

    private double scroll;
    private int maxScroll;

    private Button villageTab;
    private Button knowsTab;
    private Button memoriesTab;
    private Button loadMore;
    private Button shareNews;

    public ChronicleScreen(@Nullable UUID villagerUuid, String villagerName, Screen parent) {
        super(Component.translatable("townstead.chronicle.title"));
        this.villagerUuid = villagerUuid;
        this.villagerName = villagerName;
        this.parent = parent;
    }

    public static void open(@Nullable UUID villagerUuid, String villagerName) {
        Minecraft.getInstance().setScreen(
                new ChronicleScreen(villagerUuid, villagerName, Minecraft.getInstance().screen));
    }

    @Override
    protected void init() {
        super.init();
        int x0 = (width - PANEL_W) / 2;
        int tabW = villagerUuid != null ? (PANEL_W - 8) / 3 : PANEL_W - 4;
        int tabY = topY() + 4;
        villageTab = addRenderableWidget(Button.builder(
                        Component.translatable("townstead.chronicle.tab.village"),
                        b -> switchMode(ChronicleQueryC2SPayload.MODE_VILLAGE))
                .bounds(x0 + 2, tabY, tabW, 16).build());
        if (villagerUuid != null) {
            knowsTab = addRenderableWidget(Button.builder(
                            Component.translatable("townstead.chronicle.tab.knows"),
                            b -> switchMode(ChronicleQueryC2SPayload.MODE_KNOWS))
                    .bounds(x0 + 4 + tabW, tabY, tabW, 16).build());
            memoriesTab = addRenderableWidget(Button.builder(
                            Component.translatable("townstead.chronicle.tab.memories"),
                            b -> switchMode(ChronicleQueryC2SPayload.MODE_MEMORIES))
                    .bounds(x0 + 6 + tabW * 2, tabY, tabW, 16).build());
        }
        loadMore = addRenderableWidget(Button.builder(
                        Component.translatable("townstead.chronicle.load_more"),
                        b -> requestPage(lastEventId()))
                .bounds(width / 2 - 110, height - 25, 100, 20).build());
        if (villagerUuid != null) {
            shareNews = addRenderableWidget(Button.builder(
                            Component.translatable("townstead.chronicle.share_news"),
                            b -> sendC2S(new ChronicleShareNewsC2SPayload(villagerUuid)))
                    .bounds(width / 2 + 10, height - 25, 100, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                    .bounds(width / 2 + 10, height - 25, 100, 20).build());
        }
        switchMode(mode);
    }

    private void switchMode(byte newMode) {
        mode = newMode;
        entries.clear();
        hasMore = false;
        scroll = 0;
        requestPage(0L);
        if (villageTab != null) villageTab.active = mode != ChronicleQueryC2SPayload.MODE_VILLAGE;
        if (knowsTab != null) knowsTab.active = mode != ChronicleQueryC2SPayload.MODE_KNOWS;
        if (memoriesTab != null) memoriesTab.active = mode != ChronicleQueryC2SPayload.MODE_MEMORIES;
    }

    private void requestPage(long before) {
        loading = true;
        pendingRequestId = ChronicleClientStore.nextRequestId();
        UUID subject = villagerUuid != null ? villagerUuid : new UUID(0L, 0L);
        sendC2S(new ChronicleQueryC2SPayload(pendingRequestId, mode, subject, before, PAGE_SIZE));
    }

    private long lastEventId() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).eventId() > 0) return entries.get(i).eventId();
        }
        return 0L;
    }

    private static void sendC2S(Object payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                (net.minecraft.network.protocol.common.custom.CustomPacketPayload) payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    private int topY() {
        return Math.max(18, height / 2 - 110);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //? if neoforge {
        super.render(g, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        *///?}

        if (loading && pendingRequestId > 0) {
            ChroniclePageS2CPayload page = ChronicleClientStore.take(pendingRequestId);
            if (page != null) {
                entries.addAll(page.entries());
                hasMore = page.hasMore();
                loading = false;
            }
        }
        if (loadMore != null) {
            loadMore.active = hasMore && !loading && mode == ChronicleQueryC2SPayload.MODE_VILLAGE;
        }

        Font font = this.font;
        int x0 = (width - PANEL_W) / 2;
        int y0 = topY();
        int panelBottom = height - 32;
        g.fill(x0, y0, x0 + PANEL_W, panelBottom, 0xE0101014);

        Component title = villagerUuid != null && mode != ChronicleQueryC2SPayload.MODE_VILLAGE
                ? Component.translatable("townstead.chronicle.title.of", villagerName)
                : Component.translatable("townstead.chronicle.title");
        g.drawCenteredString(font, title, width / 2, y0 - 12, 0xFFFFFFFF);

        int viewTop = y0 + 26;
        int viewBottom = panelBottom - 6;
        int left = x0 + PAD;
        int innerW = PANEL_W - PAD * 2;

        if (entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(
                            loading ? "townstead.chronicle.loading" : "townstead.chronicle.empty"),
                    width / 2, (viewTop + viewBottom) / 2, 0xFFAAAAAA);
            return;
        }

        int rowFull = ROW_H + 2;
        int contentH = entries.size() * rowFull;
        int viewH = viewBottom - viewTop;
        maxScroll = Math.max(0, contentH - viewH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        g.enableScissor(left, viewTop, left + innerW, viewBottom);
        int y = viewTop - (int) scroll;
        for (ChroniclePageS2CPayload.EntryView entry : entries) {
            if (y + ROW_H >= viewTop && y <= viewBottom) {
                drawEntry(g, font, entry, left, y, innerW);
            }
            y += rowFull;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int trackX = x0 + PANEL_W - 4;
            g.fill(trackX, viewTop, trackX + 2, viewBottom, 0xFF0A0A0A);
            int thumbH = Math.max(8, viewH * viewH / contentH);
            int thumbY = viewTop + (int) Math.round((viewH - thumbH) * (scroll / maxScroll));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF9A9A9A);
        }
    }

    private void drawEntry(GuiGraphics g, Font font, ChroniclePageS2CPayload.EntryView entry,
                           int left, int top, int innerW) {
        Component headline = entry.headlineLangKey().isEmpty()
                ? Component.literal(entry.headlineLiteral())
                : Component.translatableWithFallback(entry.headlineLangKey(),
                        entry.headlineLiteral(), entry.args().toArray());
        g.drawString(font, headline, left, top, 0xFFE6E6E6, false);

        // Second line: date, plus the epistemic frame for hearsay — the built-in
        // signal that a knowledge entry is belief, not verified fact.
        Component sub;
        if (!entry.channel().isEmpty() && !"townstead:witness".equals(entry.channel())) {
            sub = Component.literal(entry.dateLabel() + " - ")
                    .append(Component.translatable(channelKey(entry.channel())))
                    .withStyle(ChatFormatting.DARK_GRAY);
        } else {
            sub = Component.literal(entry.dateLabel()).withStyle(ChatFormatting.DARK_GRAY);
        }
        g.drawString(font, sub, left, top + 10, 0xFF808080, false);
    }

    private static String channelKey(String channel) {
        return switch (channel) {
            case "townstead:gossip" -> "townstead.chronicle.channel.gossip";
            case "townstead:village_digest" -> "townstead.chronicle.channel.digest";
            case "townstead:player_word" -> "townstead.chronicle.channel.player_word";
            default -> "townstead.chronicle.channel.other";
        };
    }

    //? if neoforge {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (doScroll(scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (doScroll(delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    private boolean doScroll(double delta) {
        if (maxScroll <= 0) return false;
        scroll = Math.max(0, Math.min(scroll - delta * (ROW_H + 2), maxScroll));
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
