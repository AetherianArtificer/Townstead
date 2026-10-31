package com.aetherianartificer.townstead.client.gui.quest;

import com.aetherianartificer.townstead.client.gui.common.FrameRenderer;
import com.aetherianartificer.townstead.quest.QuestAction;
import com.aetherianartificer.townstead.quest.QuestActionResult;
import com.aetherianartificer.townstead.quest.QuestCapability;
import com.aetherianartificer.townstead.quest.QuestEntry;
import com.aetherianartificer.townstead.quest.QuestFilter;
import com.aetherianartificer.townstead.quest.QuestLedgerService;
import com.aetherianartificer.townstead.quest.QuestObjective;
import com.aetherianartificer.townstead.quest.QuestProviderInfo;
import com.aetherianartificer.townstead.quest.QuestReward;
import com.aetherianartificer.townstead.quest.QuestState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Native Townstead presentation over normalized, independently optional quest providers. */
public final class QuestLedgerScreen extends Screen {
    private static final String STORY_PROVIDER = "townstead_stories";
    private static final int GUTTER = 10;
    private static final int FRAME = 6;
    private static final int TITLE_H = 20;
    private static final int HEADER_GAP = 6;
    private static final int ROW_H = 25;
    private static final int GROUP_H = 13;

    private final QuestLedgerService ledger;
    private QuestState view = QuestState.ACTIVE;
    /** Sources unchecked in the filter, kept while the game runs; a new source shows until unchecked. */
    private static final java.util.Set<String> HIDDEN = new java.util.HashSet<>();
    private List<String> providerCycle = List.of(QuestFilter.ALL_PROVIDERS);
    private List<QuestEntry> filtered = List.of();
    private final List<QuestRowButton> rowButtons = new ArrayList<>();
    private final Map<String, Integer> rowOffsets = new HashMap<>();
    private ViewTabStrip viewTabs;
    private Button sourcesButton;
    private final com.aetherianartificer.townstead.client.gui.common.CheckMenu sourceMenu = new com.aetherianartificer.townstead.client.gui.common.CheckMenu();

    private EditBox search;
    private Button trackButton;
    private Button pinButton;
    private Button openButton;
    private QuestEntry selected;
    private double listScroll;
    private double detailScroll;
    private int listContentHeight;
    private int detailContentHeight;
    private int leftX, leftY, leftW, bodyH, rightX, rightW, listTop, listBottom, actionY;
    private String statusMessage = "";
    private boolean suppressAutomaticBackground;

    public QuestLedgerScreen() {
        this(QuestProviders.createDefault());
    }

    QuestLedgerScreen(QuestLedgerService ledger) {
        super(Component.translatable("townstead.quest_ledger.title"));
        this.ledger = ledger;
    }

    @Override
    protected void init() {
        super.init();
        layout();
        String previousSearch = search == null ? "" : search.getValue();
        String previousSelection = selected == null ? "" : selected.key();
        viewTabs = null;
        sourceMenu.close();
        ledger.refresh();
        buildProviderCycle();

        addRenderableWidget(Button.builder(Component.translatable("townstead.quest_ledger.close"), b -> onClose())
                .bounds(width - GUTTER - 74, GUTTER, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("townstead.quest_ledger.refresh"), b -> refresh(true))
                .bounds(width - GUTTER - 164, GUTTER, 84, 20).build());

        search = new EditBox(font, leftX + 4, leftY + 4, leftW - 8, 18,
                Component.translatable("townstead.quest_ledger.search"));
        search.setMaxLength(80);
        search.setHint(Component.translatable("townstead.quest_ledger.search.hint"));
        search.setValue(previousSearch);
        search.setResponder(ignored -> applyFilter(true));
        addRenderableWidget(search);

        int tabY = leftY + 26;
        viewTabs = addRenderableWidget(new ViewTabStrip(leftX + 4, tabY, leftW - 8, 18,
                view, this::selectView));

        createProviderTabs(tabY + 22);

        int buttonGap = 4;
        int buttonW = Math.max(28, (rightW - 8 - buttonGap * 2) / 3);
        trackButton = addRenderableWidget(new QuestIconButton(rightX + 4, actionY, buttonW, 20,
                Component.translatable("townstead.quest_ledger.track"), b -> trackSelected(), QuestIcons.Icon.TRACKED));
        pinButton = addRenderableWidget(new QuestIconButton(rightX + 4 + buttonW + buttonGap, actionY, buttonW, 20,
                Component.translatable("townstead.quest_ledger.pin"), b -> pinSelected(), QuestIcons.Icon.PINNED));
        openButton = addRenderableWidget(new QuestIconButton(rightX + 4 + (buttonW + buttonGap) * 2, actionY, buttonW, 20,
                Component.translatable("townstead.quest_ledger.open_source"), b -> openSelected(), QuestIcons.Icon.DETAILS));

        applyFilter(false);
        if (!previousSelection.isBlank()) selectKey(previousSelection);
        updateActions();
    }

    private void layout() {
        int titleY = GUTTER + TITLE_H + HEADER_GAP;
        leftX = GUTTER + FRAME;
        leftY = titleY + FRAME;
        bodyH = Math.max(112, height - titleY - GUTTER - FRAME * 2);
        int usableW = Math.max(220, width - GUTTER * 2 - FRAME * 2);
        leftW = Math.max(104, Math.min(210, (int) (usableW * 0.42)));
        rightX = leftX + leftW + FRAME * 2 + GUTTER;
        rightW = Math.max(104, width - rightX - GUTTER - FRAME);
        listTop = leftY + 68;
        listBottom = leftY + bodyH - 4;
        actionY = leftY + bodyH - 24;
    }

    private void selectView(QuestState state) {
        if (state == view) return;
        view = state;
        listScroll = 0;
        detailScroll = 0;
        if (viewTabs != null) viewTabs.setSelected(state);
        applyFilter(false);
    }

    private void refresh(boolean announce) {
        String key = selected == null ? "" : selected.key();
        ledger.refresh();
        List<String> previousProviders = providerCycle;
        buildProviderCycle();
        if (!previousProviders.equals(providerCycle)) sourceMenu.close();
        updateProviderButtons();
        applyFilter(false);
        selectKey(key);
        if (announce) statusMessage = Component.translatable("townstead.quest_ledger.refreshed").getString();
    }

    @Override
    public void tick() {
        super.tick();
        if (ledger.hasExternalChanges()) refresh(false);
    }

    private void buildProviderCycle() {
        List<String> values = new ArrayList<>();
        for (QuestProviderInfo info : ledger.snapshot().providers()) {
            if (info.available() && info.error().isBlank()) values.add(info.id());
        }
        providerCycle = List.copyOf(values);
    }

    private void createProviderTabs(int y) {
        sourcesButton = addRenderableWidget(Button.builder(Component.empty(), b -> openSourceMenu())
                .bounds(leftX + 4, y, leftW - 8, 18).build());
        updateProviderButtons();
    }

    /** The source filter: one checkbox per quest source, and one for all of them. */
    private void openSourceMenu() {
        if (sourceMenu.open()) {
            sourceMenu.close();
            return;
        }
        List<Component> labels = providerCycle.stream().map(this::providerTabLabel).toList();
        sourceMenu.open(font, Component.translatable("townstead.quest_ledger.provider.tab.all"), labels,
                i -> !HIDDEN.contains(providerCycle.get(i)),
                (i, on) -> {
                    if (on) HIDDEN.remove(providerCycle.get(i));
                    else HIDDEN.add(providerCycle.get(i));
                    listScroll = 0;
                    updateProviderButtons();
                    applyFilter(true);
                },
                sourcesButton.getX(), sourcesButton.getY() + sourcesButton.getHeight(), sourcesButton.getWidth(),
                width, height);
    }

    private Component providerTabLabel(String id) {
        if (QuestFilter.ALL_PROVIDERS.equals(id)) {
            return Component.translatable("townstead.quest_ledger.provider.tab.all");
        }
        return Component.translatable("townstead.quest_ledger.provider.tab." + id);
    }

    private void updateProviderButtons() {
        if (sourcesButton == null) return;
        long shown = providerCycle.stream().filter(id -> !HIDDEN.contains(id)).count();
        sourcesButton.setMessage(shown == providerCycle.size()
                ? Component.translatable("townstead.quest_ledger.sources.all")
                : Component.translatable("townstead.quest_ledger.sources.some", shown, providerCycle.size()));
    }

    private void applyFilter(boolean preserveSelection) {
        String selectedKey = preserveSelection && selected != null ? selected.key() : "";
        for (QuestRowButton row : rowButtons) removeWidget(row);
        rowButtons.clear();
        rowOffsets.clear();
        filtered = ledger.filtered(QuestFilter.hiding(view, HIDDEN, search == null ? "" : search.getValue()));
        int offset = 0;
        String group = null;
        for (QuestEntry quest : filtered) {
            if (!quest.group().equals(group)) {
                group = quest.group();
                offset += GROUP_H;
            }
            rowOffsets.put(quest.key(), offset);
            QuestRowButton row = new QuestRowButton(leftX + 4, listTop + offset, leftW - 8, ROW_H, quest);
            rowButtons.add(row);
            addRenderableWidget(row);
            offset += ROW_H + 2;
        }
        listContentHeight = Math.max(0, offset - 2);
        clampListScroll();
        positionRows();
        if (!selectedKey.isBlank()) selectKey(selectedKey);
        if (selected == null || filtered.stream().noneMatch(q -> q.key().equals(selected.key()))) {
            selected = filtered.isEmpty() ? null : filtered.get(0);
            detailScroll = 0;
        }
        updateActions();
    }

    private void selectKey(String key) {
        if (key == null || key.isBlank()) return;
        filtered.stream().filter(q -> key.equals(q.key())).findFirst().ifPresent(q -> selected = q);
        updateActions();
    }

    private void positionRows() {
        for (QuestRowButton row : rowButtons) {
            int y = listTop + rowOffsets.getOrDefault(row.quest.key(), 0) - (int) listScroll;
            row.setY(y);
            row.visible = y >= listTop && y + ROW_H <= listBottom;
        }
    }

    private void select(QuestEntry quest) {
        selected = quest;
        detailScroll = 0;
        statusMessage = "";
        updateActions();
    }

    private void updateActions() {
        if (trackButton == null) return;
        boolean has = selected != null;
        trackButton.active = has && selected.capabilities().contains(QuestCapability.TRACK);
        pinButton.active = has;
        openButton.active = has && selected.capabilities().contains(QuestCapability.OPEN_SOURCE);
        trackButton.setMessage(Component.translatable(selected != null && selected.tracked()
                ? "townstead.quest_ledger.untrack" : "townstead.quest_ledger.track"));
        trackButton.setTooltip(has && !trackButton.active ? Tooltip.create(untrackable(selected)) : null);
        pinButton.setMessage(Component.translatable(selected != null && selected.pinned()
                ? "townstead.quest_ledger.unpin" : "townstead.quest_ledger.pin"));
    }

    /** Why a source's quests carry no Track: Bountiful and the advancement tree have no tracker. */
    private static Component untrackable(QuestEntry quest) {
        return Component.translatable("townstead.quest_ledger.track.unsupported", quest.providerName());
    }

    private void trackSelected() {
        if (selected == null || !selected.capabilities().contains(QuestCapability.TRACK)) return;
        QuestActionResult result = ledger.perform(QuestAction.TRACK, selected);
        actionResult(result);
        if (!result.success()) return;
        selected = selected.withTracked(!selected.tracked());
        ledger.updateEntry(selected);
        applyFilter(true);
    }

    private void pinSelected() {
        if (selected == null) return;
        if (selected.capabilities().contains(QuestCapability.PIN)) {
            QuestActionResult result = ledger.perform(QuestAction.PIN, selected);
            actionResult(result);
            if (!result.success()) return;
            selected = selected.withPinned(!selected.pinned());
            ledger.updateEntry(selected);
            applyFilter(true);
        } else {
            selected = ledger.toggleLocalPin(selected);
            statusMessage = Component.translatable(selected.pinned()
                    ? "townstead.quest_ledger.pinned_local" : "townstead.quest_ledger.unpinned_local").getString();
            applyFilter(true);
        }
    }

    private void openSelected() {
        if (selected == null || !selected.capabilities().contains(QuestCapability.OPEN_SOURCE)) return;
        actionResult(ledger.perform(QuestAction.OPEN_SOURCE, selected));
    }

    private void actionResult(QuestActionResult result) {
        statusMessage = result.message();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Render the backdrop explicitly, then panels, then registered vanilla widgets. This avoids
        // 1.21 Screen#render blurring panels while preserving the 1.20 behavior.
        //? if >=1.21 {
        renderBackground(g, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(g);
        *///?}
        drawChrome(g, mouseX, mouseY);
        suppressAutomaticBackground = true;
        try {
            super.render(g, mouseX, mouseY, partialTick);
        } finally {
            suppressAutomaticBackground = false;
        }
        drawListOverlay(g);
        drawDetail(g);
        sourceMenu.render(g, font, mouseX, mouseY);
    }

    // Screen#render performs its own background pass on 1.21. The ledger has already
    // drawn that pass before its panels, so suppress only the nested call; direct calls
    // still retain vanilla blur/dim behavior.
    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!suppressAutomaticBackground) super.renderBackground(g, mouseX, mouseY, partialTick);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics g) {
        if (!suppressAutomaticBackground) super.renderBackground(g);
    }
    *///?}

    private void drawChrome(GuiGraphics g, int mouseX, int mouseY) {
        FrameRenderer.drawWoodPanel(g, leftX, leftY, leftW, bodyH);
        FrameRenderer.drawWoodenFrame(g, leftX, leftY, leftW, bodyH, FRAME);
        FrameRenderer.drawPaperSheet(g, rightX, leftY, rightW, bodyH);
        FrameRenderer.drawWoodenFrame(g, rightX, leftY, rightW, bodyH, FRAME);
    }

    private void drawListOverlay(GuiGraphics g) {
        g.enableScissor(leftX + 2, listTop, leftX + leftW - 2, listBottom);
        String group = null;
        for (QuestEntry quest : filtered) {
            if (quest.group().equals(group)) continue;
            group = quest.group();
            int y = listTop + rowOffsets.getOrDefault(quest.key(), 0) - GROUP_H - (int) listScroll + 2;
            if (y >= listTop - 8 && y < listBottom) {
                g.drawString(font, group.toUpperCase(Locale.ROOT), leftX + 6, y, 0xFFBFA77C, false);
            }
        }
        g.disableScissor();
        if (filtered.isEmpty()) {
            Component empty = Component.translatable("townstead.quest_ledger.empty");
            int emptyY = listTop + 10;
            for (FormattedCharSequence line : font.split(empty, leftW - 20)) {
                g.drawString(font, line, leftX + (leftW - font.width(line)) / 2, emptyY, 0xFFAAAAAA, false);
                emptyY += 10;
            }
        }
        drawScrollbar(g, leftX + leftW - 3, listTop, listBottom, listScroll,
                Math.max(0, listContentHeight - (listBottom - listTop)));
    }

    private void drawDetail(GuiGraphics g) {
        int x = rightX + 10;
        int top = leftY + 8;
        int contentW = Math.max(20, rightW - 20);
        int bottom = actionY - 5;
        g.enableScissor(rightX + 2, leftY + 2, rightX + rightW - 2, bottom);
        if (selected == null) {
            Component message = Component.translatable("townstead.quest_ledger.select");
            g.drawString(font, message, rightX + (rightW - font.width(message)) / 2,
                    top + 12, 0xFF4A3822, false);
            g.disableScissor();
            return;
        }
        int y = top - (int) detailScroll;
        drawScaledString(g, selected.title(), x, y, 1.35f, 0xFF241509);
        y += 14;
        String source = selected.providerName() + " • " + selected.group();
        int tagLeft = drawTags(g, selected.tags(), x + contentW, y - 1);
        int sourceX = x;
        if (STORY_PROVIDER.equals(selected.providerId())) {
            QuestIcons.draw(g, QuestIcons.Icon.STORY, x, y - 1);
            sourceX += 12;
        }
        int sourceW = Math.max(20, (int) ((tagLeft - sourceX - 4) / 0.82f));
        drawScaledString(g, trim(source.toUpperCase(Locale.ROOT), sourceW), sourceX, y + 1,
                0.82f, 0xFF765D42);
        y += 14;
        g.fill(x, y, x + contentW, y + 1, 0x774F351D);
        y += 8;
        if (!selected.description().isBlank()) {
            float descriptionScale = 1.08f;
            int wrapW = Math.max(20, (int) (contentW / descriptionScale));
            for (FormattedCharSequence line : font.split(Component.literal(selected.description()), wrapW)) {
                drawScaledString(g, line, x, y, descriptionScale, 0xFF3A2817);
                y += 11;
            }
            y += 8;
        }
        if (!selected.objectives().isEmpty()) {
            String heading = Component.translatable("townstead.quest_ledger.objectives").getString()
                    .toUpperCase(Locale.ROOT);
            QuestIcons.draw(g, QuestIcons.Icon.OBJECTIVES, x, y - 1);
            drawScaledString(g, heading, x + 12, y, 0.86f, 0xFF85551F);
            y += 13;
            for (QuestObjective objective : selected.objectives()) {
                int color = objective.done() ? 0xFF26752B
                        : objective.status() == QuestObjective.Status.FAILED ? 0xFF9B2C2C : 0xFF302114;
                int accent = objective.done() ? 0xFF3D8743
                        : objective.status() == QuestObjective.Status.FAILED ? 0xFFA33B2F : 0xFFA36D24;
                QuestIcons.draw(g, QuestIcons.forObjective(objective), x, y - 1);
                String count = objective.total() > 0 ? objective.current() + " / " + objective.total() : "";
                float countScale = 1.16f;
                int countW = Math.round(font.width(count) * countScale);
                drawScaledString(g, trim(objective.label(), contentW - 17 - countW), x + 13, y,
                        1.05f, color);
                if (!count.isBlank()) {
                    drawScaledString(g, count, x + contentW - countW, y - 1, countScale, accent);
                }
                y += 13;
                if (objective.total() > 1) {
                    drawProgressBar(g, x + 13, y, contentW - 13, objective.current(), objective.total(), accent);
                    y += 8;
                }
            }
            y += 6;
        }
        if (!selected.rewards().isEmpty()) {
            String heading = Component.translatable("townstead.quest_ledger.rewards").getString()
                    .toUpperCase(Locale.ROOT);
            QuestIcons.draw(g, QuestIcons.Icon.REWARD, x, y - 1);
            drawScaledString(g, heading, x + 12, y, 0.86f, 0xFF85551F);
            y += 13;
            for (QuestReward reward : selected.rewards()) {
                if (reward.iconItemId().isBlank() || QuestIcons.named(reward.iconItemId()) != null) {
                    QuestIcons.draw(g, QuestIcons.Icon.REWARD, x, y - 1);
                } else {
                    // Item rewards show the item itself, at the size of a line icon.
                    g.pose().pushPose();
                    g.pose().translate(x - 1, y - 2, 0);
                    g.pose().scale(0.625f, 0.625f, 1f);
                    g.renderItem(icon(reward.iconItemId()), 0, 0);
                    g.pose().popPose();
                }
                g.drawString(font, trim(reward.label(), contentW - 13), x + 13, y, 0xFF302417, false);
                y += 11;
            }
            y += 4;
        }
        if (!selected.metadata().isBlank()) {
            for (FormattedCharSequence line : font.split(Component.literal(selected.metadata()), contentW)) {
                g.drawString(font, line, x, y, 0xFF66513B, false);
                y += 10;
            }
            y += 3;
        }
        if (!selected.capabilities().contains(QuestCapability.TRACK)) {
            for (FormattedCharSequence line : font.split(untrackable(selected), contentW)) {
                g.drawString(font, line, x, y, 0xFF8A6A4A, false);
                y += 10;
            }
            y += 3;
        }
        String note = ledger.snapshot().providers().stream().filter(p -> p.id().equals(selected.providerId()))
                .map(QuestProviderInfo::note).findFirst().orElse("");
        if (!note.isBlank()) {
            for (FormattedCharSequence line : font.split(Component.literal(note), contentW)) {
                g.drawString(font, line, x, y, 0xFF795F41, false);
                y += 10;
            }
        }
        detailContentHeight = Math.max(0, y + (int) detailScroll - top);
        g.disableScissor();
        int max = Math.max(0, detailContentHeight - (bottom - top));
        if (detailScroll > max) detailScroll = max;
        drawScrollbar(g, rightX + rightW - 3, top, bottom, detailScroll, max);
        if (!statusMessage.isBlank()) {
            String status = trim(statusMessage, rightW - 12);
            g.drawString(font, status, rightX + (rightW - font.width(status)) / 2, actionY - 12, 0xFF5A3C20, false);
        }
    }

    private void drawScaledString(GuiGraphics g, String text, float x, float y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void drawScaledString(GuiGraphics g, FormattedCharSequence text, float x, float y,
                                  float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void drawProgressBar(GuiGraphics g, int x, int y, int width, long current, long total, int color) {
        if (width <= 0 || total <= 0) return;
        double ratio = Math.max(0.0, Math.min(1.0, (double) current / total));
        int filled = (int) Math.round(width * ratio);
        g.fill(x, y, x + width, y + 4, 0x553A2717);
        if (filled > 0) g.fill(x, y, x + filled, y + 4, color);
        g.fill(x, y + 4, x + width, y + 5, 0x446A4A2B);
    }

    private void drawScrollbar(GuiGraphics g, int x, int top, int bottom, double scroll, int max) {
        if (max <= 0 || bottom <= top) return;
        int h = bottom - top;
        int thumb = Math.max(8, h * h / (h + max));
        int y = top + (int) Math.round((h - thumb) * scroll / max);
        g.fill(x, top, x + 2, bottom, 0x6620160D);
        g.fill(x, y, x + 2, y + thumb, 0xFFB89A6A);
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    private int drawTags(GuiGraphics g, List<String> tags, int right, int y) {
        int cursor = right;
        for (int index = tags.size() - 1; index >= 0; index--) {
            String id = tags.get(index).toLowerCase(Locale.ROOT);
            Component label = Component.translatable("townstead.quest_ledger.tag." + id);
            int w = font.width(label) + 8;
            cursor -= w;
            int fill = "challenge".equals(id) ? 0xFFD7B36A : 0xFFE2CCA0;
            int border = "challenge".equals(id) ? 0xFF7B4B20 : 0xFF9A7745;
            g.fill(cursor, y, cursor + w, y + 11, border);
            g.fill(cursor + 1, y + 1, cursor + w - 1, y + 10, fill);
            g.drawString(font, label, cursor + 4, y + 2, 0xFF3A2410, false);
            cursor -= 3;
        }
        return cursor;
    }

    private void clampListScroll() {
        int max = Math.max(0, listContentHeight - (listBottom - listTop));
        listScroll = Math.max(0, Math.min(listScroll, max));
    }

    private boolean scroll(double mouseX, double mouseY, double delta) {
        if (mouseX >= leftX && mouseX < leftX + leftW && mouseY >= listTop && mouseY < listBottom) {
            listScroll -= delta * ROW_H;
            clampListScroll();
            positionRows();
            return true;
        }
        if (mouseX >= rightX && mouseX < rightX + rightW && mouseY >= leftY && mouseY < actionY) {
            int max = Math.max(0, detailContentHeight - (actionY - 5 - (leftY + 8)));
            detailScroll = Math.max(0, Math.min(detailScroll - delta * 18, max));
            return true;
        }
        return false;
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return sourceMenu.scroll(mouseX, mouseY, scrollY) || scroll(mouseX, mouseY, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return sourceMenu.scroll(mouseX, mouseY, delta) || scroll(mouseX, mouseY, delta)
                || super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // The open source menu takes every click: on a row it toggles, outside it closes.
        if (sourceMenu.click(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (sourceMenu.key(keyCode)) return true;
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_F && search != null) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (search == null || !search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_F) { pinSelected(); return true; }
            if (keyCode == GLFW.GLFW_KEY_O) { openSelected(); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { trackSelected(); return true; }
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                moveSelection(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveSelection(int direction) {
        if (filtered.isEmpty()) return;
        int index = selected == null ? -1 : filtered.indexOf(selected);
        index = Math.max(0, Math.min(filtered.size() - 1, index + direction));
        selected = filtered.get(index);
        int offset = rowOffsets.getOrDefault(selected.key(), 0);
        int viewH = listBottom - listTop;
        if (offset < listScroll) listScroll = offset;
        if (offset + ROW_H > listScroll + viewH) listScroll = offset + ROW_H - viewH;
        clampListScroll();
        positionRows();
        updateActions();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class QuestRowButton extends AbstractButton {
        private final QuestEntry quest;

        private QuestRowButton(int x, int y, int width, int height, QuestEntry quest) {
            super(x, y, width, height, Component.translatable("townstead.quest_ledger.row.narration",
                    quest.title(), quest.providerName(), quest.state().name().toLowerCase(),
                    quest.completedObjectives(), quest.objectives().size()));
            this.quest = quest;
        }

        @Override public void onPress() { select(quest); }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean chosen = selected != null && selected.key().equals(quest.key());
            int border = chosen ? 0xFFFFD84A : 0xFF35281B;
            int fill = chosen ? 0xE03B3022 : isHoveredOrFocused() ? 0xD02D261D : 0xC0181511;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), border);
            g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, fill);
            QuestIcons.Icon named = QuestIcons.named(quest.iconItemId());
            if (named != null) QuestIcons.draw(g, named, getX() + 3, getY() + 4);
            else g.renderItem(icon(quest.iconItemId()), getX() + 3, getY() + 4);
            int textX = getX() + 22;
            int right = getX() + getWidth() - 4;
            String progress = quest.objectives().isEmpty() ? "" : quest.completedObjectives() + "/" + quest.objectives().size();
            g.drawString(font, trim(quest.title(), right - textX - font.width(progress) - 4), textX, getY() + 4,
                    0xFFFFFFFF, false);
            if (!progress.isBlank()) g.drawString(font, progress, right - font.width(progress), getY() + 4, 0xFFE6E6E6, false);
            int sourceX = textX;
            if (quest.pinned()) {
                QuestIcons.draw(g, QuestIcons.Icon.PINNED, sourceX, getY() + 13);
                sourceX += 11;
            }
            if (quest.tracked()) {
                QuestIcons.draw(g, QuestIcons.Icon.TRACKED, sourceX, getY() + 13);
                sourceX += 11;
            }
            g.drawString(font, trim(quest.providerName(), right - sourceX), sourceX, getY() + 14, 0xFFB8B8B8, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /**
     * Primary quest-state selector, matching the Field Plot's compact Seeds/Soil segmented control.
     * It is one continuous strip; the green body and bright top rule carry selection, so the three
     * mutually-exclusive states do not read as unrelated stone action buttons.
     */
    private static final class ViewTabStrip extends AbstractWidget {
        private static final QuestState[] STATES = {
                QuestState.ACTIVE, QuestState.AVAILABLE, QuestState.COMPLETE
        };
        private static final int ACTIVE = 0xFF5A8A2A;
        private static final int ACTIVE_EDGE = 0xFF88DD44;
        private static final int NORMAL = 0xFF3A3A3A;
        private static final int HOVER = 0xFF5A5A5A;
        private static final int DIVIDER = 0xFF222222;
        private final Consumer<QuestState> onSelect;
        private QuestState selected;

        private ViewTabStrip(int x, int y, int width, int height, QuestState selected,
                             Consumer<QuestState> onSelect) {
            super(x, y, width, height, Component.translatable("townstead.quest_ledger.view.current",
                    label(selected)));
            this.selected = selected;
            this.onSelect = onSelect;
        }

        private void setSelected(QuestState state) {
            selected = state;
            setMessage(Component.translatable("townstead.quest_ledger.view.current", label(state)));
        }

        private static Component label(QuestState state) {
            return Component.translatable("townstead.quest_ledger.view."
                    + state.name().toLowerCase(Locale.ROOT));
        }

        private int left(int index) {
            return getX() + getWidth() * index / STATES.length;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            for (int index = 0; index < STATES.length; index++) {
                int x0 = left(index);
                int x1 = left(index + 1);
                boolean chosen = STATES[index] == selected;
                boolean hovered = mouseX >= x0 && mouseX < x1
                        && mouseY >= getY() && mouseY < getY() + getHeight();
                g.fill(x0, getY(), x1, getY() + getHeight(),
                        chosen ? ACTIVE : hovered ? HOVER : NORMAL);
                g.fill(x0, getY(), x1, getY() + 1, chosen ? ACTIVE_EDGE : 0xFF555555);
                g.fill(x0, getY() + getHeight() - 1, x1, getY() + getHeight(), DIVIDER);
                if (index > 0) g.fill(x0, getY() + 1, x0 + 1, getY() + getHeight() - 1, DIVIDER);
                Component text = label(STATES[index]);
                int color = chosen ? 0xFFFFFFFF : hovered ? 0xFFDDDDDD : 0xFFAAAAAA;
                var font = Minecraft.getInstance().font;
                g.drawString(font, text, x0 + (x1 - x0 - font.width(text)) / 2,
                        getY() + (getHeight() - 8) / 2, color, false);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            int index = Math.max(0, Math.min(STATES.length - 1,
                    (int) ((mouseX - getX()) * STATES.length / Math.max(1, getWidth()))));
            choose(index);
        }

        private void choose(int index) {
            QuestState state = STATES[index];
            if (state == selected) return;
            setSelected(state);
            onSelect.accept(state);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!isFocused() || (keyCode != GLFW.GLFW_KEY_LEFT && keyCode != GLFW.GLFW_KEY_RIGHT)) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            int index = 0;
            while (index < STATES.length && STATES[index] != selected) index++;
            index = Math.max(0, Math.min(STATES.length - 1,
                    index + (keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1)));
            choose(index);
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static ItemStack icon(String id) {
        try {
            //? if >=1.21 {
            ResourceLocation location = ResourceLocation.parse(id);
            //?} else {
            /*ResourceLocation location = new ResourceLocation(id);
            *///?}
            Item item = BuiltInRegistries.ITEM.get(location);
            return item == Items.AIR ? new ItemStack(Items.PAPER) : new ItemStack(item);
        } catch (Throwable ignored) {
            return new ItemStack(Items.PAPER);
        }
    }
}
