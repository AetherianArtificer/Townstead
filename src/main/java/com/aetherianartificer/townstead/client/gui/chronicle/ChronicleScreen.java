package com.aetherianartificer.townstead.client.gui.chronicle;

import com.aetherianartificer.townstead.client.chronicle.ChronicleClientStore;
import com.aetherianartificer.townstead.chronicle.net.ChroniclePageS2CPayload;
import com.aetherianartificer.townstead.chronicle.net.ChronicleQueryC2SPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public, source-scoped village digest opened by a server-authorized archive item. */
public class ChronicleScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PAD = 10;
    private static final int ROW_H = 22;
    private static final byte PAGE_SIZE = 16;

    private final String initialVillageName;
    private final Screen parent;
    private final List<ChroniclePageS2CPayload.EntryView> entries = new ArrayList<>();
    private final Map<String, Integer> categoryCounts = new LinkedHashMap<>();

    private String resolvedTitle;
    private String sourceLabel = "townstead.chronicle.source.civil_registry";
    private byte status = ChroniclePageS2CPayload.STATUS_OK;
    private boolean hasMore;
    private boolean loading;
    private int pendingRequestId = -1;
    private long nextCursor;
    private double scroll;
    private int maxScroll;
    private Button loadMore;

    public ChronicleScreen(String villageName, Screen parent) {
        super(Component.translatable("townstead.chronicle.title"));
        this.initialVillageName = villageName == null ? "" : villageName;
        this.resolvedTitle = this.initialVillageName;
        this.parent = parent;
    }

    public static void openArchive(String villageName) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ChronicleScreen(villageName, mc.screen));
    }

    @Override
    protected void init() {
        super.init();
        entries.clear();
        categoryCounts.clear();
        nextCursor = 0L;
        scroll = 0;
        loadMore = addRenderableWidget(Button.builder(
                        Component.translatable("townstead.chronicle.load_more"), b -> requestPage(nextCursor))
                .bounds(width / 2 - 110, height - 25, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 + 10, height - 25, 100, 20).build());
        requestPage(0L);
    }

    private void requestPage(long cursor) {
        if (loading) return;
        loading = true;
        pendingRequestId = ChronicleClientStore.nextRequestId();
        sendC2S(new ChronicleQueryC2SPayload(pendingRequestId,
                ChronicleQueryC2SPayload.SCOPE_PUBLIC_ARCHIVE, new UUID(0L, 0L), cursor, PAGE_SIZE));
    }

    private static void sendC2S(Object payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                (net.minecraft.network.protocol.common.custom.CustomPacketPayload) payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    private int topY() { return Math.max(24, height / 2 - 120); }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //? if neoforge {
        super.render(g, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        *///?}

        receivePage();
        if (loadMore != null) loadMore.active = hasMore && !loading;

        Font font = this.font;
        int x0 = (width - PANEL_W) / 2;
        int y0 = topY();
        int panelBottom = height - 32;
        g.fill(x0, y0, x0 + PANEL_W, panelBottom, 0xE0101014);

        String titleText = resolvedTitle.isEmpty() ? initialVillageName : resolvedTitle;
        Component title = titleText.isEmpty() ? Component.translatable("townstead.chronicle.title")
                : Component.translatable("townstead.chronicle.title.of", titleText);
        g.drawCenteredString(font, title, width / 2, y0 - 14, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable(sourceLabel).withStyle(ChatFormatting.DARK_GRAY),
                width / 2, y0 + 7, 0xFF888888);

        int summaryY = y0 + 21;
        if (!categoryCounts.isEmpty()) {
            String summary = categoryCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .limit(4)
                    .map(e -> shortCategory(e.getKey()) + ": " + e.getValue())
                    .collect(java.util.stream.Collectors.joining("  "));
            g.drawCenteredString(font, summary, width / 2, summaryY, 0xFFB0A58D);
        }

        int viewTop = y0 + 37;
        int viewBottom = panelBottom - 6;
        int left = x0 + PAD;
        int innerW = PANEL_W - PAD * 2;

        if (loading && entries.isEmpty()) {
            drawCenteredStatus(g, viewTop, viewBottom, "townstead.chronicle.loading");
            return;
        }
        if (status != ChroniclePageS2CPayload.STATUS_OK || entries.isEmpty()) {
            drawCenteredStatus(g, viewTop, viewBottom, statusKey(status));
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
            if (y + ROW_H >= viewTop && y <= viewBottom) drawEntry(g, font, entry, left, y);
            y += rowFull;
        }
        g.disableScissor();
    }

    private void receivePage() {
        if (!loading || pendingRequestId <= 0) return;
        ChroniclePageS2CPayload page = ChronicleClientStore.take(pendingRequestId);
        if (page == null) return;
        status = page.status();
        hasMore = page.hasMore();
        nextCursor = page.nextCursor();
        if (!page.title().isEmpty()) resolvedTitle = page.title();
        if (!page.sourceLabel().isEmpty()) sourceLabel = page.sourceLabel();
        categoryCounts.clear();
        categoryCounts.putAll(page.categoryCounts());
        entries.addAll(page.entries());
        loading = false;
    }

    private void drawEntry(GuiGraphics g, Font font, ChroniclePageS2CPayload.EntryView entry,
                           int left, int top) {
        Component headline = entry.headlineLangKey().isEmpty()
                ? Component.literal(entry.headlineLiteral())
                : Component.translatableWithFallback(entry.headlineLangKey(),
                entry.headlineLiteral(), entry.args().toArray());
        g.drawString(font, headline, left, top, 0xFFE6E6E6, false);
        g.drawString(font, Component.literal(entry.dateLabel()).withStyle(ChatFormatting.DARK_GRAY),
                left, top + 11, 0xFF808080, false);
    }

    private void drawCenteredStatus(GuiGraphics g, int top, int bottom, String key) {
        g.drawCenteredString(font, Component.translatable(key), width / 2, (top + bottom) / 2, 0xFFAAAAAA);
    }

    private static String statusKey(byte status) {
        return switch (status) {
            case ChroniclePageS2CPayload.STATUS_UNAVAILABLE -> "townstead.chronicle.unavailable";
            case ChroniclePageS2CPayload.STATUS_FORBIDDEN -> "townstead.chronicle.forbidden";
            default -> "townstead.chronicle.empty";
        };
    }

    private static String shortCategory(String category) {
        int dot = category.lastIndexOf('.');
        String value = dot >= 0 ? category.substring(dot + 1) : category;
        return value.isEmpty() ? category : Character.toUpperCase(value.charAt(0)) + value.substring(1);
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

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
