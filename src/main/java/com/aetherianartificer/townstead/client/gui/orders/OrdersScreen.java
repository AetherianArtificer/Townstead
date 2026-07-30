package com.aetherianartificer.townstead.client.gui.orders;

import com.aetherianartificer.townstead.client.gui.common.Controls;
import com.aetherianartificer.townstead.client.gui.common.Controls.Rect;
import com.aetherianartificer.townstead.client.gui.common.FrameRenderer;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.client.gui.common.PaletteList;
import com.aetherianartificer.townstead.client.gui.common.PaperField;
import com.aetherianartificer.townstead.client.gui.common.ParchmentButton;
import com.aetherianartificer.townstead.work.order.Order;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Row;
import com.aetherianartificer.townstead.work.order.net.OrderEditC2SPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A worksite's production orders: what it can make on the left, what it has been told to make on
 * the right, and a composer along the bottom while you are drafting something new.
 *
 * <p>Built to answer the question a player actually arrives with, which is never "what could this
 * place make" but <em>"why is that not happening?"</em> — so every line carries a status and a
 * stalled one says why beneath itself.</p>
 *
 * <p>Laid out on the Field Post's terms and measured against it: fills the window on a
 * {@link #SPACING} gutter, one wooden frame per region, panels filled to the player's own chat
 * opacity, rows transparent until hovered, and a scrollbar whose absence means you are seeing
 * everything. Every horizontal position in an order row comes from one column template.</p>
 */
public class OrdersScreen extends Screen {

    // ── Layout, on the Field Post's numbers ──
    private static final int SPACING = 10;
    private static final int FRAME = 6;
    private static final int TITLE_H = 16;
    private static final int CATALOGUE_W = 136;
    private static final int STRIP_H = 22;
    private static final int SEARCH_H = 16;
    private static final int INSET = 3;

    /** The order row's columns. Both of a row's lines are placed against these. */
    private static final int COL_MOVE = 11;
    private static final int COL_METER = 54;
    private static final int COL_COUNT = 42;
    private static final int COL_STATE = 56;
    private static final int COL_MODE = 92;
    private static final int MARK_GAP = 3;
    private static final int COL_MARKS = Controls.MARK * 3 + MARK_GAP * 2 + 7;
    private static final int COL_GAP = 6;

    private static final int ROW_H = 30;
    private static final int REASON_H = 10;
    private static final int COMPOSER_H = 44;

    private static final String[] MODE_LABELS = {"Make", "Keep in stock", "Per villager", "Standing"};
    private static final String[] SCOPE_LABELS = {"Here", "The village"};
    private static final String[] LIST_LABELS = {"Work freely", "Stand down"};

    private static @Nullable OrdersScreen open;

    private OrdersSnapshotS2CPayload data;
    private @Nullable EditBox search;
    private @Nullable PaletteList catalogue;
    private @Nullable Button makeableButton;
    private @Nullable PaperField rename;

    private boolean makeableOnly;
    private int orderScroll;

    /** Drawn last, over everything, so a tooltip is never clipped by the panel it started in. */
    private @Nullable String tooltip;

    /** The order being composed, or null. Nothing reaches the villagers until it is added. */
    private @Nullable Draft draft;
    /** The row whose details are open, or -1. */
    private int detailsFor = -1;

    private static final class Draft {
        final ResourceLocation output;
        Order.Mode mode = Order.Mode.KEEP_STOCKED;
        int target = 10;
        Order.CountScope scope = Order.CountScope.HERE;

        Draft(ResourceLocation output) {
            this.output = output;
        }
    }

    public OrdersScreen(OrdersSnapshotS2CPayload data) {
        super(Component.literal("Orders"));
        this.data = data;
    }

    /** Opens the screen, or refreshes it in place when the server sends a new snapshot. */
    public static void openOrUpdate(OrdersSnapshotS2CPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (open != null && mc.screen == open) {
            open.data = payload;
            if (open.detailsFor >= payload.rows().size()) open.detailsFor = -1;
            if (open.rename != null) open.closeRename();
            return;
        }
        open = new OrdersScreen(payload);
        mc.setScreen(open);
    }

    // ── Layout ──

    private int contentTop() { return SPACING + TITLE_H + FRAME; }
    private int contentBottom() { return this.height - SPACING - FRAME; }
    private int catalogueLeft() { return SPACING + FRAME; }
    private int ordersLeft() { return catalogueLeft() + CATALOGUE_W + FRAME + SPACING + FRAME; }
    private int ordersWidth() { return this.width - ordersLeft() - SPACING - FRAME; }
    private int listTop() { return contentTop() + STRIP_H; }
    private int composerTop() { return contentBottom() - COMPOSER_H; }
    private int orderListBottom() { return draft == null ? contentBottom() : composerTop(); }
    private int catalogueListTop() { return contentTop() + SEARCH_H + 4; }

    @Override
    protected void init() {
        String previous = search == null ? "" : search.getValue();
        int toggle = 14;
        search = new EditBox(this.font, catalogueLeft() + INSET + 1, contentTop() + INSET,
                CATALOGUE_W - INSET * 2 - toggle - 4, SEARCH_H - 4, Component.literal("Search"));
        search.setMaxLength(64);
        search.setBordered(true);
        search.setHint(Component.literal("Search"));
        search.setValue(previous);
        search.setResponder(v -> refreshCatalogue());
        addRenderableWidget(search);

        makeableButton = Button.builder(Component.literal(""), b -> {
                    makeableOnly = !makeableOnly;
                    updateMakeableLabel();
                    refreshCatalogue();
                })
                .bounds(catalogueLeft() + CATALOGUE_W - INSET - toggle, contentTop() + INSET,
                        toggle, SEARCH_H - 4)
                .tooltip(Tooltip.create(Component.translatable("townstead.orders.filter.makeable")))
                .build();
        updateMakeableLabel();
        addRenderableWidget(makeableButton);

        int closeW = this.font.width("Close") + 18;
        addRenderableWidget(new ParchmentButton(
                this.width - SPACING - closeW, SPACING - 2, closeW, 14,
                Component.literal("Close"), b -> onClose()));

        if (rename != null) {
            String typed = rename.getValue();
            rename = null;
            openRename();
            if (rename != null) rename.setValue(typed);
        }
        catalogue = new PaletteList(this.minecraft, catalogueLeft(), CATALOGUE_W,
                contentBottom() - catalogueListTop(), catalogueListTop(), this::pickFromCatalogue);
        catalogue.setOnHeaderClick(key -> {
            catalogue.toggleCategory(key);
            refreshCatalogue();
        });
        addRenderableWidget(catalogue);
        refreshCatalogue();

        orderScroll = clamp(orderScroll, data.rows().size());
    }

    /** A tick or a blank, on a vanilla button — the Field Post's own filter toggle, verbatim. */
    private void updateMakeableLabel() {
        if (makeableButton == null) return;
        makeableButton.setMessage(Component.literal(makeableOnly ? "✔" : " "));
    }

    /** Rebuilds the palette's rows from the snapshot, the search box and the makeable filter. */
    private void refreshCatalogue() {
        if (catalogue == null) return;
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        Map<String, List<Option>> byMod = new LinkedHashMap<>();
        for (Option option : data.options()) {
            if (makeableOnly && !option.available()) continue;
            if (!query.isEmpty()
                    && !itemName(option.output()).toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            byMod.computeIfAbsent(ModNames.of(option.output().getNamespace()), k -> new ArrayList<>())
                    .add(option);
        }
        List<PaletteList.ToolEntry> entries = new ArrayList<>();
        for (Map.Entry<String, List<Option>> group : byMod.entrySet()) {
            entries.add(PaletteList.ToolEntry.header(group.getKey(), group.getValue().size()));
            if (catalogue.isCategoryCollapsed(group.getKey())) continue;
            for (Option option : group.getValue()) {
                PaletteList.ToolEntry row = new PaletteList.ToolEntry(
                        option.output().toString(), itemName(option.output()),
                        new ItemStack(BuiltInRegistries.ITEM.get(option.output())), group.getKey());
                row.dim = !option.available();
                row.tooltip = itemName(option.output())
                        + (option.stationLabel().isEmpty() ? "" : " · " + option.stationLabel())
                        + (option.available() ? "" : " — " + option.blocker());
                entries.add(row);
            }
        }
        catalogue.replaceEntries(entries);
    }

    /** Clicking a palette row drafts it. The villagers hear nothing until Add to list. */
    private void pickFromCatalogue(@Nullable PaletteList.ToolEntry entry) {
        if (entry == null || entry.isHeader) return;
        ResourceLocation id = tryParse(entry.toolId);
        if (id != null) draft = new Draft(id);
    }

    private static @Nullable ResourceLocation tryParse(String raw) {
        //? if >=1.21 {
        return ResourceLocation.tryParse(raw);
        //?} else {
        /*try {
            return new ResourceLocation(raw);
        } catch (Exception e) {
            return null;
        }
        *///?}
    }

    // ── Render ──

    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        drawFurniture(g);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics g) {
        drawFurniture(g);
    }
    *///?}

    /**
     * Frames and panel fills, before the widgets so the search field is not painted over. The
     * world is left showing through: no vanilla dimming, because the panels do their own.
     */
    private void drawFurniture(GuiGraphics g) {
        int top = contentTop();
        int h = contentBottom() - top;
        FrameRenderer.drawWoodenFrame(g, catalogueLeft(), top, CATALOGUE_W, h, FRAME);
        FrameRenderer.drawChatPanel(g, catalogueLeft(), top, CATALOGUE_W, h);
        FrameRenderer.drawWoodenFrame(g, ordersLeft(), top, ordersWidth(), h, FRAME);
        FrameRenderer.drawChatPanel(g, ordersLeft(), top, ordersWidth(), h);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        tooltip = null;
        super.render(g, mouseX, mouseY, partial);

        drawCatalogue(g, mouseX, mouseY);
        drawOrders(g, mouseX, mouseY);
        if (draft != null) drawComposer(g, mouseX, mouseY);
        if (detailsFor >= 0) drawDetails(g, mouseX, mouseY);
        if (tooltip != null) {
            g.renderTooltip(this.font, Component.literal(tooltip), mouseX, mouseY);
        }
    }

    /** Records a tooltip for the end of the frame, if the cursor is over the given box. */
    private void tip(Rect r, int mouseX, int mouseY, String text) {
        if (r.contains(mouseX, mouseY)) tooltip = text;
    }

    // ── Catalogue ──

    /**
     * The chrome around the palette: the search field's companion filter, and the rule beneath.
     * The list itself is a {@link PaletteList} widget, so its headers, collapse, scrollbar and
     * hover are the Field Post's own and not a second version of them.
     */
    private void drawCatalogue(GuiGraphics g, int mouseX, int mouseY) {
        int x = catalogueLeft();
        int ruleY = catalogueListTop() - 2;
        g.fill(x + INSET, ruleY, x + CATALOGUE_W - INSET, ruleY + 1, FrameRenderer.FRAME_HIGHLIGHT);

        String empty = emptyCatalogueReason();
        if (empty != null) {
            int lineY = catalogueListTop() + 4;
            for (var line : this.font.split(Component.literal(empty), CATALOGUE_W - INSET * 2 - 4)) {
                g.drawString(this.font, line, x + INSET + 2, lineY, Palette.LABEL_DIM, false);
                lineY += this.font.lineHeight + 1;
            }
        }
        // The palette renders itself as a widget; it only hands back what the cursor was over.
        if (catalogue != null) {
            String hovered = catalogue.takeTooltip();
            if (hovered != null) tooltip = hovered;
        }
    }

    /**
     * Why the palette is blank, or null when it is not. An empty column with no explanation is how
     * a player concludes the kitchen is broken, when in fact they have a filter on.
     */
    private @Nullable String emptyCatalogueReason() {
        if (catalogue != null && catalogue.children().size() > 0) return null;
        if (data.options().isEmpty()) return "Nothing is made here. This place has no workstation.";
        if (makeableOnly) return "Nothing here can be made from what is stored right now. Untick the filter to see everything.";
        return "Nothing matches that search.";
    }

    // ── Orders ──

    /**
     * The strip along the top of the order frame: which place this is on the left, and the one
     * setting that governs the whole list on the right. The Field Post's toolbar, in other words —
     * and it is why nothing sits at the top-left of the screen, where the HUD already lives.
     */
    private void drawOrdersStrip(GuiGraphics g, int mouseX, int mouseY) {
        int x = ordersLeft();
        int y = contentTop();
        int textY = y + (STRIP_H - this.font.lineHeight) / 2;

        if (rename == null) {
            g.drawString(this.font, data.worksiteName(), x + INSET + 2, textY, Palette.CARD, false);
            Rect pencil = renamePencil();
            g.fill(pencil.x(), pencil.y(), pencil.right(), pencil.bottom(), Palette.DESK_DEEP);
            Palette.drawOutline(g, pencil.x(), pencil.y(), pencil.right(), pencil.bottom(),
                    Palette.BRASS_DEEP);
            int px = pencil.x() + 3;
            int py = pencil.y() + 3;
            int ink = pencil.contains(mouseX, mouseY) ? Palette.BRASS_HOT : Palette.BRASS;
            g.fill(px + 2, py, px + 5, py + 1, ink);
            g.fill(px + 1, py + 1, px + 4, py + 2, ink);
            g.fill(px, py + 2, px + 3, py + 3, ink);
            g.fill(px, py + 3, px + 1, py + 4, ink);
            tip(pencil, mouseX, mouseY, "Rename this worksite");

            g.drawString(this.font, data.worksiteDetail().toUpperCase(Locale.ROOT),
                    pencil.right() + 6, textY, Palette.LABEL_DIM, false);
        }

        Rect[] listSeg = listSegments();
        String label = "WHEN DONE";
        g.drawString(this.font, label, listSeg[0].x() - this.font.width(label) - 6, textY,
                Palette.INK_DIM, false);
        Controls.drawSegments(g, this.font, listSeg, LIST_LABELS, data.listOnly() ? 1 : 0,
                Controls.segmentAt(listSeg, mouseX, mouseY));
        tip(new Rect(listSeg[0].x(), listSeg[0].y(),
                        listSeg[listSeg.length - 1].right() - listSeg[0].x(), Controls.SEG_H),
                mouseX, mouseY, "Stand down: work only this list, and rest once it is done");

        g.fill(x + INSET, y + STRIP_H - 1, x + ordersWidth() - INSET, y + STRIP_H,
                FrameRenderer.FRAME_HIGHLIGHT);
    }

    private Rect renamePencil() {
        int textY = contentTop() + (STRIP_H - this.font.lineHeight) / 2;
        return new Rect(ordersLeft() + INSET + 2 + this.font.width(data.worksiteName()) + 5,
                textY - 1, 12, 12);
    }

    private Rect[] listSegments() {
        Rect[] probe = Controls.segmentLayout(this.font, 0, 0, LIST_LABELS);
        int total = probe[probe.length - 1].right();
        return Controls.segmentLayout(this.font,
                ordersLeft() + ordersWidth() - INSET - 2 - total,
                contentTop() + (STRIP_H - Controls.SEG_H) / 2, LIST_LABELS);
    }

    private void drawOrders(GuiGraphics g, int mouseX, int mouseY) {
        drawOrdersStrip(g, mouseX, mouseY);

        int x = ordersLeft();
        int w = ordersWidth();
        List<Row> rows = data.rows();
        int bottom = orderListBottom() - INSET;
        if (rows.isEmpty()) {
            g.drawString(this.font, "No orders yet. Villagers work as they see fit.",
                    x + INSET + 2, listTop() + 6, Palette.LABEL_DIM, false);
            g.drawString(this.font, "Pick something on the left to start a list.",
                    x + INSET + 2, listTop() + 17, Palette.INK_DIM, false);
            return;
        }

        int shown = 0;
        g.enableScissor(x + INSET, listTop(), x + w - INSET, bottom);
        int rowY = listTop() + 2;
        for (int i = orderScroll; i < rows.size() && rowY < bottom; i++) {
            rowY += drawRow(g, rows.get(i), i, x, rowY, w, mouseX, mouseY);
            shown++;
        }
        g.disableScissor();
        Controls.drawScrollbar(g, x + w - INSET - Controls.SCROLLBAR_W, listTop(),
                bottom - listTop(), orderScroll, shown, rows.size());
    }

    /** Draws one line and returns the height it used, which grows when it has to explain itself. */
    private int drawRow(GuiGraphics g, Row row, int index, int x, int rowY, int w,
                        int mouseX, int mouseY) {
        boolean showReason = !row.reason().isEmpty();
        int height = ROW_H + (showReason ? REASON_H : 0);
        boolean satisfied = row.status() == OrdersSnapshotS2CPayload.Status.SATISFIED;
        Rect body = rowBody(x, rowY, w, height);
        boolean hover = body.contains(mouseX, mouseY);

        if (hover || satisfied) {
            g.fill(body.x(), body.y(), body.right(), body.bottom(),
                    satisfied ? Palette.ALCOVE : Palette.DESK);
        }
        g.fill(body.x(), body.bottom(), body.right(), body.bottom() + 1, Palette.WELL_EDGE);

        Columns c = columns(body);
        Rect[] carets = caretRects(body);
        boolean canUp = index > 0;
        boolean canDown = index < data.rows().size() - 1;
        drawCaret(g, carets[0], true, canUp && carets[0].contains(mouseX, mouseY), canUp);
        drawCaret(g, carets[1], false, canDown && carets[1].contains(mouseX, mouseY), canDown);
        if (canUp) tip(carets[0], mouseX, mouseY, "Work this sooner");
        if (canDown) tip(carets[1], mouseX, mouseY, "Work this later");

        Controls.drawSlot(g, c.item, body.y() + 3);
        drawItem(g, row.output(), c.item + 1, body.y() + 4);

        int nameInk = satisfied ? Palette.LABEL_MID : Palette.CARD;
        g.drawString(this.font, trim(itemName(row.output()), c.mainW), c.main, body.y() + 5,
                nameInk, false);

        if (row.want() > 0) {
            Controls.drawBar(g, c.meter, body.y() + 7, COL_METER,
                    row.have() / (float) row.want(), row.have() >= row.want());
        }
        String count = row.want() > 0 ? row.have() + "/" + row.want() : String.valueOf(row.have());
        g.drawString(this.font, count, c.count + COL_COUNT - this.font.width(count), body.y() + 5,
                satisfied ? Palette.LABEL_DIM : Palette.LABEL_LIGHT, false);

        String status = statusLabel(row.status());
        Controls.drawChip(g, this.font, chipStyle(row.status()), status, c.state, body.y() + 4);

        // Line two: mode, stepper and details, all inside the flexible column.
        Rect mode = modeButton(c, body);
        boolean modeHot = mode.contains(mouseX, mouseY);
        g.fill(mode.x(), mode.y(), mode.right(), mode.bottom(),
                modeHot ? Palette.DESK_LIP : Palette.ROW);
        Palette.drawOutline(g, mode.x(), mode.y(), mode.right(), mode.bottom(), Palette.DESK_LIP);
        g.drawString(this.font, trim(MODE_LABELS[row.mode().ordinal()], mode.w() - 8),
                mode.x() + 4, mode.y() + 3, modeHot ? Palette.WARM_GLOW : Palette.LABEL_LIGHT, false);
        tip(mode, mouseX, mouseY, modeTip(row.mode()));

        if (row.mode().hasTarget()) {
            Rect[] stepper = rowStepper(c, body, row);
            Controls.drawStepper(g, this.font, stepper, String.valueOf(row.target()), true,
                    hitIndex(stepper, mouseX, mouseY));
        }

        Rect details = detailsLink(c, body);
        boolean detailsHot = details.contains(mouseX, mouseY);
        g.drawString(this.font, "Details…", details.x(), details.y() + 3,
                detailsHot ? Palette.WARM_GLOW : Palette.INK_ACCENT, false);
        tip(details, mouseX, mouseY, "Everything else about this line");

        // The ruled margin, and the marks against it.
        Controls.drawMargin(g, c.marks - 6, body.y(), body.right() - c.marks + 6, body.h());
        Rect hold = holdMark(c, body);
        Rect copy = copyMark(c, body);
        Rect strike = strikeMark(c, body);
        Controls.drawHoldMark(g, hold, row.paused(), hold.contains(mouseX, mouseY));
        Controls.drawCopyMark(g, copy, copy.contains(mouseX, mouseY));
        Controls.drawStrikeMark(g, strike, strike.contains(mouseX, mouseY));
        tip(hold, mouseX, mouseY, row.paused() ? "Resume this order" : "Hold this order");
        tip(copy, mouseX, mouseY, "Copy this order below");
        tip(strike, mouseX, mouseY, "Strike this order out");

        if (showReason) {
            g.drawString(this.font, trim(row.reason(), body.right() - 8 - c.main), c.main,
                    body.y() + ROW_H - 5, Palette.LABEL_WARM, false);
        }
        // Striking previews itself: the name is ruled through before anything is committed.
        if (strike.contains(mouseX, mouseY)) {
            int nameW = Math.min(this.font.width(itemName(row.output())), c.mainW);
            g.fill(c.main, body.y() + 9, c.main + nameW, body.y() + 10, Controls.Chip.BLOCKED.ink);
        }
        return height;
    }

    private Rect rowBody(int x, int rowY, int w, int height) {
        return new Rect(x + INSET, rowY, w - INSET * 2 - Controls.SCROLLBAR_W, height - 3);
    }

    private static String modeTip(Order.Mode mode) {
        return switch (mode) {
            case MAKE -> "Make that many, then this line is finished";
            case KEEP_STOCKED -> "Work whenever the stores hold fewer than that";
            case PER_VILLAGER -> "That many for every villager, counted the same way";
            case STANDING -> "No target: make this when there is nothing more pressing";
        };
    }

    /** Where each column starts for a row of this width. One source for drawing and for clicking. */
    private record Columns(int item, int main, int mainW, int meter, int count, int state, int marks) {}

    private Columns columns(Rect body) {
        int marks = body.right() - 4 - COL_MARKS;
        int state = marks - COL_GAP - COL_STATE;
        int count = state - COL_GAP - COL_COUNT;
        int meter = count - COL_GAP - COL_METER;
        int item = body.x() + INSET + COL_MOVE + COL_GAP;
        int main = item + 16 + COL_GAP;
        return new Columns(item, main, Math.max(24, meter - COL_GAP - main),
                meter, count, state, marks);
    }

    private Rect[] caretRects(Rect body) {
        int mid = body.y() + body.h() / 2;
        return new Rect[]{
                new Rect(body.x() + INSET, mid - 9, COL_MOVE, 9),
                new Rect(body.x() + INSET, mid, COL_MOVE, 9)
        };
    }

    private static void drawCaret(GuiGraphics g, Rect r, boolean up, boolean hot, boolean usable) {
        int ink = !usable ? Palette.COLD_DEEP : hot ? Palette.WARM_GLOW : Palette.INK_DIM;
        int cx = r.x() + r.w() / 2;
        int cy = r.y() + 2;
        for (int i = 0; i < 4; i++) {
            int half = up ? i : 3 - i;
            g.fill(cx - half, cy + i, cx + half + 1, cy + i + 1, ink);
        }
    }

    private Rect modeButton(Columns c, Rect body) {
        return new Rect(c.main, body.y() + 16, Math.min(COL_MODE, c.mainW), Controls.SEG_H);
    }

    private Rect[] rowStepper(Columns c, Rect body, Row row) {
        Rect mode = modeButton(c, body);
        return Controls.stepperLayout(this.font, mode.right() + 5, body.y() + 16,
                String.valueOf(row.target()));
    }

    private Rect detailsLink(Columns c, Rect body) {
        Rect mode = modeButton(c, body);
        // Past the widest stepper the five parts can reach, so it never shifts with the number.
        return new Rect(mode.right() + 5 + 84, body.y() + 16,
                this.font.width("Details…"), Controls.SEG_H);
    }

    private Rect holdMark(Columns c, Rect body) {
        return new Rect(c.marks, body.y() + (body.h() - Controls.MARK) / 2,
                Controls.MARK, Controls.MARK);
    }

    private Rect copyMark(Columns c, Rect body) {
        Rect hold = holdMark(c, body);
        return new Rect(hold.right() + MARK_GAP, hold.y(), Controls.MARK, Controls.MARK);
    }

    private Rect strikeMark(Columns c, Rect body) {
        Rect hold = holdMark(c, body);
        return new Rect(hold.x() + (Controls.MARK + MARK_GAP) * 2, hold.y(),
                Controls.MARK, Controls.MARK);
    }

    // ── Composer ──

    private void drawComposer(GuiGraphics g, int mouseX, int mouseY) {
        Draft d = draft;
        if (d == null) return;
        int x = ordersLeft();
        int y = composerTop();
        int w = ordersWidth();
        g.fill(x + INSET, y, x + w - INSET, y + 1, Palette.DESK_LIP);
        g.fill(x + INSET, y + 1, x + w - INSET, y + COMPOSER_H - INSET, 0x24FFB347);

        Controls.drawSlot(g, x + INSET + 3, y + 5);
        drawItem(g, d.output, x + INSET + 4, y + 6);
        g.drawString(this.font, "NEW ORDER — " + itemName(d.output).toUpperCase(Locale.ROOT),
                x + INSET + 26, y + 9, Palette.BAR_FILL, false);

        Rect[] modes = composerModes();
        Controls.drawSegments(g, this.font, modes, MODE_LABELS, d.mode.ordinal(),
                Controls.segmentAt(modes, mouseX, mouseY));
        if (d.mode.hasTarget()) {
            Rect[] stepper = composerStepper();
            Controls.drawStepper(g, this.font, stepper, String.valueOf(d.target), true,
                    hitIndex(stepper, mouseX, mouseY));
            Rect[] scope = composerScope();
            Controls.drawSegments(g, this.font, scope, SCOPE_LABELS, d.scope.ordinal(),
                    Controls.segmentAt(scope, mouseX, mouseY));
        }

        Rect discard = composerDiscard();
        Controls.drawPill(g, this.font, discard, "Discard", true,
                discard.contains(mouseX, mouseY), Palette.LABEL_LIGHT);
        Rect add = composerAdd();
        g.fill(add.x(), add.y(), add.right(), add.bottom(),
                add.contains(mouseX, mouseY) ? Controls.CHROME_HOVER : Controls.CHROME_ON);
        Palette.drawOutline(g, add.x(), add.y(), add.right(), add.bottom(), Controls.CHROME_ACCENT);
        g.drawString(this.font, "Add to list", add.x() + 6, add.y() + 3, 0xFFFFFFFF, false);
    }

    private int composerRowY() {
        return composerTop() + 24;
    }

    private Rect[] composerModes() {
        return Controls.segmentLayout(this.font, ordersLeft() + INSET + 3, composerRowY(), MODE_LABELS);
    }

    private Rect[] composerStepper() {
        Rect[] modes = composerModes();
        return Controls.stepperLayout(this.font, modes[modes.length - 1].right() + 10,
                composerRowY(), String.valueOf(draft == null ? 0 : draft.target));
    }

    private Rect[] composerScope() {
        Rect[] stepper = composerStepper();
        return Controls.segmentLayout(this.font, stepper[2].right() + 10, composerRowY(), SCOPE_LABELS);
    }

    private Rect composerAdd() {
        int w = this.font.width("Add to list") + 12;
        return new Rect(ordersLeft() + ordersWidth() - INSET - 3 - w, composerRowY(), w, Controls.SEG_H);
    }

    private Rect composerDiscard() {
        Rect add = composerAdd();
        Rect probe = Controls.pillLayout(this.font, 0, 0, "Discard");
        return new Rect(add.x() - 6 - probe.w(), composerRowY(), probe.w(), Controls.PILL_H);
    }

    // ── Details ──

    private void drawDetails(GuiGraphics g, int mouseX, int mouseY) {
        Row row = detailsRow();
        if (row == null) return;
        g.fill(0, 0, this.width, this.height, 0xA80A0705);

        Rect win = detailsWindow();
        FrameRenderer.drawWoodenFrame(g, win.x(), win.y(), win.w(), win.h(), FRAME);
        FrameRenderer.drawInnerPanel(g, win.x(), win.y(), win.w(), win.h());

        // Its own title strip, inside the frame. An earlier build floated it above the frame, over
        // the list it was supposed to be sitting on top of.
        int textY = win.y() + (STRIP_H - this.font.lineHeight) / 2;
        Controls.drawSlot(g, win.x() + INSET + 2, win.y() + 3);
        drawItem(g, row.output(), win.x() + INSET + 3, win.y() + 4);
        g.drawString(this.font, itemName(row.output()), win.x() + INSET + 24, textY,
                Palette.CARD, false);
        Rect back = detailsBack();
        Controls.drawPill(g, this.font, back, "Back", true, back.contains(mouseX, mouseY),
                Palette.LABEL_LIGHT);
        g.fill(win.x() + INSET, win.y() + STRIP_H - 1, win.right() - INSET, win.y() + STRIP_H,
                FrameRenderer.FRAME_HIGHLIGHT);

        int x = win.x() + INSET + 5;
        int top = detailsFieldsTop();
        Controls.fieldLabel(g, this.font, "Repeat", x, top);
        Rect[] modes = detailsModes();
        Controls.drawSegments(g, this.font, modes, MODE_LABELS, row.mode().ordinal(),
                Controls.segmentAt(modes, mouseX, mouseY));

        Controls.fieldLabel(g, this.font, "Target", x, top + 26);
        Rect[] stepper = detailsStepper(row);
        Controls.drawStepper(g, this.font, stepper, String.valueOf(row.target()),
                row.mode().hasTarget(), hitIndex(stepper, mouseX, mouseY));

        Controls.fieldLabel(g, this.font, "Counted across", x, top + 52);
        Rect[] scope = detailsScope();
        Controls.drawSegments(g, this.font, scope, SCOPE_LABELS,
                row.mode().hasTarget() ? row.scope().ordinal() : -1,
                row.mode().hasTarget() ? Controls.segmentAt(scope, mouseX, mouseY) : -1);

        Controls.fieldLabel(g, this.font, "Who may work it", x, top + 78);
        // Read-only: the field exists on the order and the engine honours it, but nothing yet sets
        // it, and a control that cannot change anything is worse than a plain statement.
        g.drawString(this.font, row.whoLabel(), x, top + 89, Palette.LABEL_MID, false);
    }

    private Rect detailsWindow() {
        Rect[] modes = Controls.segmentLayout(this.font, 0, 0, MODE_LABELS);
        int w = Math.min(this.width - SPACING * 2 - FRAME * 2,
                modes[modes.length - 1].right() + (INSET + 5) * 2);
        int h = STRIP_H + 6 + 102;
        return new Rect((this.width - w) / 2, (this.height - h) / 2, w, h);
    }

    private Rect detailsBack() {
        Rect win = detailsWindow();
        Rect probe = Controls.pillLayout(this.font, 0, 0, "Back");
        return new Rect(win.right() - INSET - 2 - probe.w(),
                win.y() + (STRIP_H - Controls.PILL_H) / 2, probe.w(), Controls.PILL_H);
    }

    private int detailsFieldsTop() {
        return detailsWindow().y() + STRIP_H + 6;
    }

    private Rect[] detailsModes() {
        return Controls.segmentLayout(this.font, detailsWindow().x() + INSET + 5,
                detailsFieldsTop() + 10, MODE_LABELS);
    }

    private Rect[] detailsStepper(Row row) {
        return Controls.stepperLayout(this.font, detailsWindow().x() + INSET + 5,
                detailsFieldsTop() + 36, String.valueOf(row.target()));
    }

    private Rect[] detailsScope() {
        return Controls.segmentLayout(this.font, detailsWindow().x() + INSET + 5,
                detailsFieldsTop() + 62, SCOPE_LABELS);
    }

    @Nullable
    private Row detailsRow() {
        return detailsFor < 0 || detailsFor >= data.rows().size() ? null : data.rows().get(detailsFor);
    }

    // ── Interaction ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        long site = data.worksiteId();

        if (detailsFor >= 0) return clickDetails(mouseX, mouseY, site);
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (rename == null && renamePencil().contains(mouseX, mouseY)) {
            openRename();
            return true;
        }
        int listChoice = Controls.segmentAt(listSegments(), mouseX, mouseY);
        if (listChoice >= 0) {
            send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_LIST_ONLY, 0, listChoice));
            return true;
        }
        if (draft != null && clickComposer(mouseX, mouseY, site)) return true;
        return clickOrders(mouseX, mouseY, site);
    }

    private boolean clickOrders(double mx, double my, long site) {
        List<Row> rows = data.rows();
        int bottom = orderListBottom() - INSET;
        int rowY = listTop() + 2;
        int x = ordersLeft();
        int w = ordersWidth();
        for (int i = orderScroll; i < rows.size() && rowY < bottom; i++) {
            Row row = rows.get(i);
            int height = ROW_H + (row.reason().isEmpty() ? 0 : REASON_H);
            Rect body = rowBody(x, rowY, w, height);
            Columns c = columns(body);
            Rect[] carets = caretRects(body);

            if (carets[0].contains(mx, my) && i > 0) {
                send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.MOVE, i, i - 1));
                return true;
            }
            if (carets[1].contains(mx, my) && i < rows.size() - 1) {
                send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.MOVE, i, i + 1));
                return true;
            }
            if (holdMark(c, body).contains(mx, my)) {
                send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.TOGGLE_PAUSE, i));
                return true;
            }
            if (copyMark(c, body).contains(mx, my)) {
                send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.COPY, i));
                return true;
            }
            if (strikeMark(c, body).contains(mx, my)) {
                send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.REMOVE, i));
                return true;
            }
            if (detailsLink(c, body).contains(mx, my)) {
                detailsFor = i;
                return true;
            }
            if (modeButton(c, body).contains(mx, my)) {
                send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_MODE, i,
                        nextMode(row.mode()).name()));
                return true;
            }
            if (row.mode().hasTarget()) {
                int arrow = hitIndex(rowStepper(c, body, row), mx, my);
                if (arrow >= 0 && arrow != 2) {
                    send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_TARGET, i,
                            Math.max(0, row.target() + stepFor(arrow))));
                    return true;
                }
            }
            rowY += height;
        }
        return false;
    }

    private boolean clickComposer(double mx, double my, long site) {
        Draft d = draft;
        if (d == null) return false;
        int mode = Controls.segmentAt(composerModes(), mx, my);
        if (mode >= 0) {
            d.mode = Order.Mode.values()[mode];
            return true;
        }
        if (d.mode.hasTarget()) {
            int arrow = hitIndex(composerStepper(), mx, my);
            if (arrow >= 0 && arrow != 2) {
                d.target = Math.max(0, d.target + stepFor(arrow));
                return true;
            }
            int scope = Controls.segmentAt(composerScope(), mx, my);
            if (scope >= 0) {
                d.scope = Order.CountScope.values()[scope];
                return true;
            }
        }
        if (composerDiscard().contains(mx, my)) {
            draft = null;
            return true;
        }
        if (composerAdd().contains(mx, my)) {
            commitDraft(site, d);
            return true;
        }
        return false;
    }

    /**
     * Adds the drafted line, then sends the settings it was drafted with. The server appends with
     * its own defaults, so the follow-ups are what make the draft's choices real; they address the
     * bottom of the list, which is where ADD puts a new line.
     */
    private void commitDraft(long site, Draft d) {
        int index = data.rows().size();
        send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.ADD, 0, d.output.toString()));
        send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_MODE, index, d.mode.name()));
        if (d.mode.hasTarget()) {
            send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_TARGET, index, d.target));
            send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_SCOPE, index, d.scope.name()));
        }
        draft = null;
    }

    private boolean clickDetails(double mx, double my, long site) {
        Row row = detailsRow();
        if (row == null || detailsBack().contains(mx, my)) {
            detailsFor = -1;
            return true;
        }
        int mode = Controls.segmentAt(detailsModes(), mx, my);
        if (mode >= 0) {
            send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_MODE, detailsFor,
                    Order.Mode.values()[mode].name()));
            return true;
        }
        if (row.mode().hasTarget()) {
            int arrow = hitIndex(detailsStepper(row), mx, my);
            if (arrow >= 0 && arrow != 2) {
                send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_TARGET, detailsFor,
                        Math.max(0, row.target() + stepFor(arrow))));
                return true;
            }
            int scope = Controls.segmentAt(detailsScope(), mx, my);
            if (scope >= 0) {
                send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_SCOPE, detailsFor,
                        Order.CountScope.values()[scope].name()));
                return true;
            }
        }
        // A click anywhere else inside a modal is absorbed, never passed to the screen behind it.
        return true;
    }

    /** The stepper's five parts are −10, −1, the value, +1, +10. */
    private static int stepFor(int part) {
        return switch (part) {
            case 0 -> -10;
            case 1 -> -1;
            case 3 -> 1;
            default -> 10;
        };
    }

    private static Order.Mode nextMode(Order.Mode mode) {
        Order.Mode[] all = Order.Mode.values();
        return all[(mode.ordinal() + 1) % all.length];
    }

    private static int hitIndex(Rect[] rects, double mx, double my) {
        for (int i = 0; i < rects.length; i++) {
            if (rects[i].contains(mx, my)) return i;
        }
        return -1;
    }

    // ── Renaming ──

    private void openRename() {
        int textY = contentTop() + (STRIP_H - this.font.lineHeight) / 2;
        rename = new PaperField(this.font, ordersLeft() + INSET + 5, textY, 150, 10,
                Component.literal("Name this place"));
        rename.setMaxLength(48);
        rename.setValue(data.worksiteName());
        addRenderableWidget(rename);
        setFocused(rename);
    }

    private void closeRename() {
        if (rename == null) return;
        removeWidget(rename);
        rename = null;
    }

    private void commitRename() {
        if (rename == null) return;
        String value = rename.getValue();
        closeRename();
        send(OrderEditC2SPayload.of(data.worksiteId(), OrderEditC2SPayload.Action.RENAME, 0, value));
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (rename != null) {
            if (key == 257 || key == 335) {
                commitRename();
                return true;
            }
            if (key == 256) {
                closeRename();
                return true;
            }
        }
        if (key == 256) {
            // Escape unwinds one layer at a time: the modal, then the draft, then the screen.
            if (detailsFor >= 0) {
                detailsFor = -1;
                return true;
            }
            if (draft != null) {
                draft = null;
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dy) {
    *///?}
        // The palette scrolls itself; only the order list is ours to move.
        if (mouseX >= ordersLeft()) {
            orderScroll = clamp(orderScroll + (dy > 0 ? -1 : 1), data.rows().size());
            return true;
        }
        //? if >=1.21 {
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
        //?} else {
        /*return super.mouseScrolled(mouseX, mouseY, dy);
        *///?}
    }

    @Override
    public void onClose() {
        open = null;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Helpers ──

    private static void send(OrderEditC2SPayload payload) {
        OrdersScreenNetwork.send(payload);
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(value, Math.max(0, size - 1)));
    }

    private static String trim(String text, int maxWidth) {
        Minecraft mc = Minecraft.getInstance();
        if (maxWidth <= 0) return "";
        if (mc.font.width(text) <= maxWidth) return text;
        String out = text;
        while (!out.isEmpty() && mc.font.width(out + "…") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    private static String itemName(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
    }

    private static void drawItem(GuiGraphics g, ResourceLocation id, int x, int y) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        if (!stack.isEmpty()) g.renderItem(stack, x, y);
    }

    private static String statusLabel(OrdersSnapshotS2CPayload.Status status) {
        return switch (status) {
            case WORKING -> "WORKING";
            case WAITING -> "WAITING";
            case BLOCKED -> "BLOCKED";
            case PAUSED -> "HELD";
            case SATISFIED -> "DONE";
        };
    }

    private static Controls.Chip chipStyle(OrdersSnapshotS2CPayload.Status status) {
        return switch (status) {
            case WORKING -> Controls.Chip.WORKING;
            case WAITING -> Controls.Chip.WAITING;
            case BLOCKED -> Controls.Chip.BLOCKED;
            case PAUSED -> Controls.Chip.PAUSED;
            case SATISFIED -> Controls.Chip.SATISFIED;
        };
    }
}
