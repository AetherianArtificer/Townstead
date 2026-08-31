package com.aetherianartificer.townstead.client.gui.orders;

import com.aetherianartificer.townstead.client.gui.common.Controls;
import com.aetherianartificer.townstead.client.gui.common.Controls.Rect;
import com.aetherianartificer.townstead.client.gui.common.FrameRenderer;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.client.gui.common.PaletteList;
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
    private static final int MIN_CATALOGUE_W = 96;
    private static final int COMPACT_STRIP_H = 38;

    /** The order row's columns. Both of a row's lines are placed against these. */
    private static final int COL_MOVE = 11;
    private static final int COL_METER = 54;
    private static final int COL_COUNT = 42;
    private static final int COL_STATE = 56;
    private static final int COL_MODE = 92;
    private static final int MARK_GAP = 3;
    /** The three marks exactly, flush against the row's right edge. */
    private static final int COL_MARKS = Controls.MARK * 3 + MARK_GAP * 2;
    /** The ruled margin line sits this far left of the first mark. */
    private static final int MARGIN_GAP = 8;
    private static final int COL_GAP = 6;

    // A row's two lines and their bands. Line two used to start inside line one's slot and run
    // past the row's own bottom edge, which is what the controls overlapping the divider was.
    private static final int ROW_H = 42;
    private static final int COMPACT_ROW_H = 64;
    private static final int LINE1_Y = 3;
    private static final int LINE2_Y = 22;
    private static final int COMPACT_LINE2_Y = 22;
    private static final int COMPACT_LINE3_Y = 42;
    private static final int BAND_Y = 12;
    private static final int REASON_H = 11;
    private static final int COMPOSER_H = 64;
    /** Header actions share one footprint so Assignment and Back read as a matched pair. */
    private static final int DETAIL_ACTION_W = 76;

    private static final String[] MODE_LABELS = {"Make", "Keep in stock", "Per villager", "Standing"};
    private static final String[] SCOPE_LABELS = {"Here", "The village"};
    private static final String[] LIST_LABELS = {"Work freely", "Stand down"};
    private static final String[] TAB_LABELS = {"Produce", "Jobs"};

    private static @Nullable OrdersScreen open;

    private OrdersSnapshotS2CPayload data;
    private @Nullable EditBox search;
    private @Nullable PaletteList catalogue;
    private @Nullable Button makeableButton;

    private boolean makeableOnly;
    /** Which of the catalogue's two shelves is showing: things to make, or jobs to order. */
    private boolean jobsTab;
    private int orderScroll;

    /** Drawn last, over everything, so a tooltip is never clipped by the panel it started in. */
    private @Nullable String tooltip;

    /** The order being composed, or null. Nothing reaches the villagers until it is added. */
    private @Nullable Draft draft;
    /** The row whose details are open, or -1. */
    private int detailsFor = -1;
    /** Details' focused worker/operator chooser. */
    private boolean workPicker;
    /** How far the details window's item list — members or needs — is scrolled. */
    private int setScroll;
    /** Which scrollbar the mouse is riding, if any. Cleared on release. */
    private boolean dragDetailScroll;
    private boolean dragAssignmentScroll;
    private boolean dragOrderScroll;

    private static final class Draft {
        final ResourceLocation output;
        final ResourceLocation product;
        /** A job has no item, so it carries its own name and icon rather than resolving one. */
        final boolean activity;
        /** A category names a tag, so it commits with a marker and borrows a member's sprite. */
        final boolean tag;
        final String label;
        final ResourceLocation icon;
        Order.Mode mode = Order.Mode.KEEP_STOCKED;
        int target = 10;
        Order.CountScope scope = Order.CountScope.HERE;

        Draft(Option option) {
            this.output = option.output();
            this.product = option.product();
            this.activity = option.activity();
            this.tag = option.tag();
            this.label = option.label();
            this.icon = option.activity() ? option.stationIcon()
                    : option.tag() ? tagIcon(option.output())
                    : option.product();
            if (this.activity) this.mode = Order.Mode.STANDING;
        }

        String name() {
            return (activity || tag) && !label.isEmpty() ? label : itemName(output);
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
            open.update(payload);
            return;
        }
        open = new OrdersScreen(payload);
        mc.setScreen(open);
    }

    /**
     * Takes a fresh snapshot without disturbing what the player is doing.
     *
     * <p>The palette is only rebuilt when the set of things this place can make has actually
     * changed, because rebuilding it throws away the scroll position — and a snapshot arrives every
     * second, so a list that reset itself that often would be unusable.</p>
     */
    private void update(OrdersSnapshotS2CPayload payload) {
        String before = catalogueSignature();
        data = payload;
        if (detailsFor >= payload.rows().size()) detailsFor = -1;
        if (!before.equals(catalogueSignature())) refreshCatalogue();
    }

    /** What the palette is built from. Cheap to compare, and ignores anything it does not draw. */
    private String catalogueSignature() {
        StringBuilder out = new StringBuilder();
        for (Option option : data.options()) {
            out.append(option.product()).append(option.available() ? '+' : '-');
        }
        return out.toString();
    }


    // ── Layout ──

    private int contentTop() { return SPACING + TITLE_H + FRAME; }
    private int contentBottom() { return this.height - SPACING - FRAME; }
    private int catalogueLeft() { return SPACING + FRAME; }
    /** Gives the order pane first claim on narrow screens; the palette remains searchable. */
    private int catalogueWidth() {
        int widthForMinimumOrderPane = this.width - 54 - 260;
        return Math.max(MIN_CATALOGUE_W, Math.min(CATALOGUE_W, widthForMinimumOrderPane));
    }
    private int ordersLeft() { return catalogueLeft() + catalogueWidth() + FRAME + SPACING + FRAME; }
    private int ordersWidth() { return this.width - ordersLeft() - SPACING - FRAME; }
    private boolean compactRows() { return ordersWidth() < 320; }
    private int stripHeight() { return compactHeader() ? COMPACT_STRIP_H : STRIP_H; }

    /** The header only wraps when its own contents do not fit; row density is a separate choice. */
    private boolean compactHeader() {
        Rect[] probe = Controls.segmentLayout(this.font, 0, 0, LIST_LABELS);
        int controls = probe[probe.length - 1].right();
        int required = INSET * 2 + Controls.SCROLLBAR_W + this.font.width(data.worksiteName())
                + this.font.width("WHEN DONE") + controls + 28;
        return ordersWidth() < required;
    }
    private int listTop() { return contentTop() + stripHeight(); }
    private int composerTop() { return contentBottom() - composerHeight(); }
    private int orderListBottom() { return draft == null ? contentBottom() : composerTop(); }
    // The Field Post's palette column, measure for measure: search at the top, the shared tab
    // bar directly beneath it, then the list.
    private int searchTop() { return contentTop() + INSET; }
    private int catalogueListTop() { return contentTop() + SEARCH_H + Controls.TAB_BAR_H; }

    @Override
    protected void init() {
        String previous = search == null ? "" : search.getValue();
        int toggle = 14;
        search = new EditBox(this.font, catalogueLeft() + INSET + 1, searchTop(),
                catalogueWidth() - INSET * 2 - toggle - 4, SEARCH_H - 4, Component.literal("Search"));
        search.setMaxLength(64);
        search.setBordered(true);
        search.setHint(Component.literal("Search..."));
        search.setValue(previous);
        search.setResponder(v -> refreshCatalogue());
        addRenderableWidget(search);

        makeableButton = Button.builder(Component.literal(""), b -> {
                    makeableOnly = !makeableOnly;
                    updateMakeableLabel();
                    refreshCatalogue();
                })
                .bounds(catalogueLeft() + catalogueWidth() - INSET - toggle, searchTop(),
                        toggle, SEARCH_H - 4)
                .tooltip(Tooltip.create(Component.translatable("townstead.orders.filter.makeable")))
                .build();
        makeableButton.visible = !jobsTab;
        updateMakeableLabel();
        addRenderableWidget(makeableButton);

        // A plain Minecraft button, because it does the plainest thing on the screen.
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(this.width - SPACING - 60, contentTop() - FRAME - 23, 60, 20)
                .build());

        catalogue = new PaletteList(this.minecraft, catalogueLeft(), catalogueWidth(),
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

    /** Rebuilds the palette's rows from the snapshot, the tab, the search box and the filter. */
    private void refreshCatalogue() {
        if (catalogue == null) return;
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        Map<String, List<Option>> byMod = new LinkedHashMap<>();
        for (Option option : data.options()) {
            // The tab is the first cut: jobs on one shelf, everything makeable on the other.
            if (option.activity() != jobsTab) continue;
            if (makeableOnly && !option.available()) continue;
            if (!query.isEmpty()
                    && !nameOf(option.activity() || option.tag(), option.label(), option.output())
                            .toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            String group = option.activity() ? "Jobs"
                    : option.tag() ? "Kinds"
                    : ModNames.of(option.output().getNamespace());
            byMod.computeIfAbsent(group, k -> new ArrayList<>()).add(option);
        }
        // Kinds lead: an order for "cooked meats" is the usual ask, the single items are the
        // long tail beneath it.
        List<Option> kinds = byMod.remove("Kinds");
        Map<String, List<Option>> ordered = new LinkedHashMap<>();
        if (kinds != null) ordered.put("Kinds", kinds);
        ordered.putAll(byMod);

        List<PaletteList.ToolEntry> entries = new ArrayList<>();
        for (Map.Entry<String, List<Option>> group : ordered.entrySet()) {
            // The jobs tab holds one flat list; a "Jobs (4)" header under a Jobs tab says nothing.
            if (!jobsTab) {
                entries.add(PaletteList.ToolEntry.header(group.getKey(), group.getValue().size()));
                if (catalogue.isCategoryCollapsed(group.getKey())) continue;
            }
            for (Option option : group.getValue()) {
                String shown = nameOf(option.activity() || option.tag(), option.label(), option.output());
                // A thing is shown as itself; a job has no item, so it borrows an icon; a category
                // borrows one member's. Using the station icon for all of them is what stopped
                // every food sprite from rendering.
                ResourceLocation sprite = option.activity() ? option.stationIcon()
                        : option.tag() ? tagIcon(option.output())
                        : option.product();
                PaletteList.ToolEntry row = new PaletteList.ToolEntry(
                        option.product().toString(), shown,
                        displayStack(sprite), group.getKey());
                row.dim = !option.available();
                row.tooltip = shown
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
        if (id == null) return;
        Option option = optionFor(id);
        if (option != null) draft = new Draft(option);
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
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics g) {
    }
    *///?}

    /**
     * Frames and panel fills, before the widgets so the search field is not painted over. The
     * world is left showing through: no vanilla dimming, because the panels do their own.
     */
    private void drawFurniture(GuiGraphics g) {
        // The Field Post dims the whole screen first and then fills its panels; skipping the dim
        // is why this screen read as see-through and the debug overlay showed through the list.
        double opacity = this.minecraft == null ? 0.5
                : this.minecraft.options.textBackgroundOpacity().get();
        int alpha = 0x40 + (int) (opacity * (0xC0 - 0x40));
        g.fill(0, 0, this.width, this.height, (alpha << 24) | 0x0A0705);

        int top = contentTop();
        int h = contentBottom() - top;
        FrameRenderer.drawWoodenFrame(g, catalogueLeft(), top, catalogueWidth(), h, FRAME);
        FrameRenderer.drawChatPanel(g, catalogueLeft(), top, catalogueWidth(), h);
        FrameRenderer.drawWoodenFrame(g, ordersLeft(), top, ordersWidth(), h, FRAME);
        FrameRenderer.drawChatPanel(g, ordersLeft(), top, ordersWidth(), h);
        // The palette's rows each carry their own plate, which reads as a darker column; the
        // order panel is rows-transparent-until-hover, so it gets the difference painted in.
        g.fill(ordersLeft(), top, ordersLeft() + ordersWidth(), top + h, 0x60000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        tooltip = null;
        // Screen.render() owns background timing in 1.21 but not 1.20. Draw the Order Sheet's
        // furniture explicitly on every version, while the empty overrides above suppress the
        // vanilla background in versions that would otherwise add it automatically.
        drawFurniture(g);
        super.render(g, mouseX, mouseY, partial);

        drawCatalogue(g, mouseX, mouseY);
        drawOrders(g, mouseX, mouseY);
        if (draft != null) drawComposer(g, mouseX, mouseY);
        if (detailsFor >= 0) {
            // Background rows have already reported their hover. A modal owns both hit testing and
            // help text, so nothing underneath it may leak a tooltip into this frame.
            tooltip = null;
            // Item sprites are 3D and drawn at z=150, so a flat panel painted afterwards still
            // ends up behind them — which is why the catalogue's icons punched through the modal.
            // Flush what has been batched, then lift the whole window above them.
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            drawDetails(g, mouseX, mouseY);
            g.pose().popPose();
        }
        if (tooltip != null) {
            // Keep help compact at large GUI scales as well as physically inside the window.
            // A 220px logical tooltip becomes an enormous banner at GUI scale 3; 176 leaves the
            // order underneath readable and gives the renderer ample room to clamp either side.
            int tooltipWidth = Math.max(112, Math.min(176, this.width - SPACING * 4));
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 800);
            g.renderTooltip(this.font, this.font.split(Component.literal(tooltip), tooltipWidth),
                    mouseX, mouseY);
            g.pose().popPose();
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
        Rect[] tabs = catalogueTabs();
        Controls.drawTabs(g, this.font, tabs, TAB_LABELS, jobsTab ? 1 : 0,
                Controls.segmentAt(tabs, mouseX, mouseY));

        String empty = emptyCatalogueReason();
        if (empty != null) {
            int lineY = catalogueListTop() + 4;
            for (var line : this.font.split(Component.literal(empty), catalogueWidth() - INSET * 2 - 4)) {
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
        if (jobsTab) {
            return data.options().stream().anyMatch(Option::activity)
                    ? "Nothing matches that search."
                    : "No jobs are worked at this place.";
        }
        // Was "no trade works here", which is a lie whenever a trade does work here and simply has
        // no catalogue behind it yet. The screen cannot tell those apart, so it says both.
        if (data.options().isEmpty()) return "Nothing can be ordered here. Either no trade works this place, or the trade that does has nothing it can be asked for.";
        if (makeableOnly) return "Nothing here can be made from what is stored right now. Untick the filter to see everything.";
        return "Nothing matches that search.";
    }

    /** The two shelves the catalogue is split across, sitting where the Field Post puts its own. */
    private Rect[] catalogueTabs() {
        return Controls.tabLayout(catalogueLeft(), contentTop() + SEARCH_H + 4,
                catalogueWidth(), TAB_LABELS.length);
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
        boolean compact = compactHeader();
        int textY = compact ? y + 5 : y + (STRIP_H - this.font.lineHeight) / 2;

        Rect[] listSeg = listSegments();
        String label = "WHEN DONE";
        int nameRoom = compact
                ? x + ordersWidth() - INSET - Controls.SCROLLBAR_W - (x + INSET + 2)
                : listSeg[0].x() - this.font.width(label) - 10 - (x + INSET + 2) - 8;
        String name = trim(data.worksiteName(), nameRoom);
        g.drawString(this.font, name, x + INSET + 2, textY, Palette.CARD, false);
        // What kind of place the record is bound to lives in a tooltip; spelling "ROOM" out
        // beside the name made the strip read like a form.
        tip(new Rect(x + INSET + 2, textY - 1, this.font.width(name), this.font.lineHeight + 2),
                mouseX, mouseY,
                "post".equals(data.worksiteDetail()) ? "Bound to a post" : "Bound to this room");

        // At compact widths the place name gets its own line. The policy drops beneath it; its
        // caption is shown only when the remaining room can hold it instead of crushing the name.
        int labelX = listSeg[0].x() - this.font.width(label) - 10;
        if (!compact || labelX >= x + INSET + 2) {
            g.drawString(this.font, label, labelX,
                    listSeg[0].y() + (Controls.SEG_H - this.font.lineHeight) / 2 + 1,
                    Palette.LABEL_DIM, false);
        }
        Controls.drawSegments(g, this.font, listSeg, LIST_LABELS, data.listOnly() ? 1 : 0,
                Controls.segmentAt(listSeg, mouseX, mouseY));
        tip(new Rect(listSeg[0].x(), listSeg[0].y(),
                        listSeg[listSeg.length - 1].right() - listSeg[0].x(), Controls.SEG_H),
                mouseX, mouseY, "Stand down: work only this list, and rest once it is done");

        g.fill(x + INSET, y + stripHeight() - 1, x + ordersWidth() - INSET, y + stripHeight(),
                FrameRenderer.FRAME_HIGHLIGHT);
    }

    private Rect[] listSegments() {
        Rect[] probe = Controls.segmentLayout(this.font, 0, 0, LIST_LABELS);
        int total = probe[probe.length - 1].right();
        // Right edge on the same line as the rows' margin marks: content right, which stops
        // short of the frame by the scrollbar's lane.
        return Controls.segmentLayout(this.font,
                ordersLeft() + ordersWidth() - INSET - Controls.SCROLLBAR_W - total,
                compactHeader() ? contentTop() + 20
                        : contentTop() + (STRIP_H - Controls.SEG_H) / 2,
                LIST_LABELS);
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
        String reason = displayedReason(row);
        boolean showReason = !reason.isEmpty();
        boolean compact = compactRows();
        int height = rowBaseHeight() + (showReason ? REASON_H : 0);
        boolean satisfied = row.status() == OrdersSnapshotS2CPayload.Status.SATISFIED;
        Rect body = rowBody(x, rowY, w, height);
        boolean hover = body.contains(mouseX, mouseY);

        // The highlight and the divider run to the strip separator's own inset — full-bleed to
        // the frame read wider than every other rule on the panel — and up into the seam above,
        // so a lit row is one solid band with no hairline across its top.
        if (hover || satisfied) {
            g.fill(x + INSET, body.y() - 2, x + w - INSET, body.bottom(),
                    satisfied ? Palette.ALCOVE : Palette.DESK);
        }
        g.fill(x + INSET, body.bottom(), x + w - INSET, body.bottom() + 1, Palette.WELL_EDGE);

        Columns c = columns(body);
        Rect[] carets = caretRects(body);
        boolean canUp = index > 0;
        boolean canDown = index < data.rows().size() - 1;
        drawCaret(g, carets[0], true, canUp && carets[0].contains(mouseX, mouseY), canUp);
        drawCaret(g, carets[1], false, canDown && carets[1].contains(mouseX, mouseY), canDown);
        if (canUp) tip(carets[0], mouseX, mouseY, "Work this sooner");
        if (canDown) tip(carets[1], mouseX, mouseY, "Work this later");

        Controls.drawSlot(g, c.item, body.y() + LINE1_Y);
        drawItem(g, iconFor(row), c.item + 1, body.y() + LINE1_Y + 1);

        int nameInk = satisfied ? Palette.LABEL_MID : Palette.CARD;
        g.drawString(this.font, trim(nameOf(row.activity() || row.tag(), row.label(), row.output()), c.mainW), c.main, body.y() + 8,
                nameInk, false);

        if (!compact && row.want() > 0 && c.showMeter && !row.activity()) {
            Controls.drawBar(g, c.meter, body.y() + 9, COL_METER,
                    row.have() / (float) row.want(), row.have() >= row.want());
        }
        String count = row.activity() ? ""
                : row.want() > 0 ? row.have() + "/" + row.want() : String.valueOf(row.have());
        if (compact) {
            Rect details = detailsLink(c, body, row);
            String shownCount = trim(count, Math.max(0, details.x() - COL_GAP - c.main));
            g.drawString(this.font, shownCount, c.main, body.y() + COMPACT_LINE2_Y + 3,
                    satisfied ? Palette.LABEL_DIM : Palette.LABEL_LIGHT, false);
        } else {
            g.drawString(this.font, count, c.count + COL_COUNT - this.font.width(count), body.y() + 8,
                    satisfied ? Palette.LABEL_DIM : Palette.LABEL_LIGHT, false);
        }

        // The chip rides line one and the Details link line two, both right-aligned against the
        // ruled margin with the same gap the marks keep on its far side — one straight edge.
        String status = statusLabel(row.status());
        Controls.drawChip(g, this.font, chipStyle(row.status()), status,
                c.state + COL_STATE - Controls.chipWidth(this.font, status),
                body.y() + (compact ? COMPACT_LINE2_Y : 5));

        // Line two: mode, stepper and details, all inside the flexible column.
        Rect mode = modeButton(c, body, row);
        if (row.activity()) {
            // Nothing to cycle: a job is on the list or it is not, and held or not.
            g.drawString(this.font, trim(row.modeLabel(), compact ? mode.w() : mode.w() + 40),
                    mode.x(), mode.y() + 3,
                    Palette.LABEL_DIM, false);
        } else {
            Controls.drawButton(g, this.font, mode, MODE_LABELS[row.mode().ordinal()],
                    false, mode.contains(mouseX, mouseY), true);
            tip(mode, mouseX, mouseY, modeTip(row.mode()));
        }

        if (row.mode().hasTarget() && !row.activity()) {
            Rect[] stepper = rowStepper(c, body, row);
            Controls.drawStepper(g, this.font, stepper, String.valueOf(row.target()), true,
                    hitIndex(stepper, mouseX, mouseY));
        }

        Rect details = detailsLink(c, body, row);
        Controls.drawButton(g, this.font, details, "Details", false,
                details.contains(mouseX, mouseY), true);
        tip(details, mouseX, mouseY, "Everything else about this line");

        // The ruled margin, and the marks against it. Each row's stretch reaches up into the
        // seam above and down through its own divider, so the rule reads as one line down the
        // page instead of a dash per row.
        Controls.drawMargin(g, c.marks - MARGIN_GAP, body.y() - 2,
                (x + w - INSET) - (c.marks - MARGIN_GAP), body.h() + 3);
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
            g.drawString(this.font, trim(reason, body.right() - 8 - c.main), c.main,
                    body.y() + (compact ? COMPACT_LINE3_Y : LINE2_Y)
                            + Controls.SEG_H + 3, Palette.LABEL_WARM, false);
        }
        // Striking previews itself: the name is ruled through before anything is committed.
        if (strike.contains(mouseX, mouseY)) {
            int nameW = Math.min(this.font.width(
                    nameOf(row.activity() || row.tag(), row.label(), row.output())), c.mainW);
            g.fill(c.main, body.y() + 12, c.main + nameW, body.y() + 13, Controls.Chip.BLOCKED.ink);
        }
        return height;
    }

    /**
     * A tagged requirement is a set of real choices, not a thing called "matching item". Use the
     * same phase as the details icons so the queued row names the concrete item currently shown.
     */
    private String displayedReason(Row row) {
        if (row.status() != OrdersSnapshotS2CPayload.Status.WAITING) return row.reason();
        Option option = optionFor(row.product());
        if (option == null || option.missing().isEmpty()) return row.reason();
        long phase = net.minecraft.Util.getMillis() / 1200L;
        StringBuilder out = new StringBuilder("Missing: ");
        int shown = Math.min(2, option.missing().size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) out.append(", ");
            var need = option.missing().get(i);
            if (need.count() > 1) out.append(need.count()).append(' ');
            if (need.items().isEmpty()) {
                out.append(need.label().isBlank() ? "item" : need.label());
                continue;
            }
            int choice = (int) Math.floorMod(phase, need.items().size());
            out.append(itemName(need.items().get(choice)));
        }
        if (option.missing().size() > shown) {
            out.append(" +").append(option.missing().size() - shown).append(" more");
        }
        return out.toString();
    }

    private Rect rowBody(int x, int rowY, int w, int height) {
        return new Rect(x + INSET, rowY, w - INSET * 2 - Controls.SCROLLBAR_W, height - 3);
    }

    private int rowBaseHeight() { return compactRows() ? COMPACT_ROW_H : ROW_H; }

    private static String modeTip(Order.Mode mode) {
        return switch (mode) {
            case MAKE -> "Make that many, then this line is finished";
            case KEEP_STOCKED -> "Work whenever the stores hold fewer than that";
            case PER_VILLAGER -> "That many for every villager, counted the same way";
            case STANDING -> "No target: make this when there is nothing more pressing";
        };
    }

    /** The narrowest a name may be squeezed before the meter is dropped to give it room. */
    private static final int MIN_NAME_W = 64;

    /** Where each column starts for a row of this width. One source for drawing and for clicking. */
    private record Columns(int item, int main, int mainW, int meter, int count, int state, int marks,
                           boolean showMeter) {}

    /**
     * The fixed columns are anchored to the right edge and the name takes what is left — but at a
     * large GUI scale there is not much left, and the name was collapsing to five characters while
     * a progress bar nobody needed kept its full width. The meter is the first thing to go.
     */
    private Columns columns(Rect body) {
        if (compactRows()) {
            // The three row actions turn into a vertical rail. That returns two button-widths to
            // the actual order, then the order uses three calm lines instead of making every
            // control fight for the old two-line grid.
            int marks = body.right() - Controls.MARK;
            int contentRight = marks - MARGIN_GAP - 7;
            int item = body.x() + INSET + COL_MOVE + COL_GAP;
            int main = item + 16 + COL_GAP;
            int state = contentRight - COL_STATE;
            return new Columns(item, main, Math.max(24, contentRight - main),
                    main, main, state, marks, false);
        }
        // Right-aligned so the last mark shares its right edge with the strip's picker above.
        int marks = body.right() - COL_MARKS;
        // The chip/Details stack ends the same distance left of the rule as the marks sit right
        // of it, so the margin reads symmetric. state is the stack's left edge; its right is
        // state + COL_STATE.
        int state = (marks - MARGIN_GAP) - 7 - COL_STATE;
        int count = state - COL_GAP - COL_COUNT;
        int meter = count - COL_GAP - COL_METER;
        int item = body.x() + INSET + COL_MOVE + COL_GAP;
        int main = item + 16 + COL_GAP;

        boolean showMeter = meter - COL_GAP - main >= MIN_NAME_W;
        int mainRight = showMeter ? meter : count;
        return new Columns(item, main, Math.max(24, mainRight - COL_GAP - main),
                meter, count, state, marks, showMeter);
    }

    private Rect[] caretRects(Rect body) {
        if (compactRows()) {
            return new Rect[]{
                    new Rect(body.x() + INSET, body.y() + 2, COL_MOVE, 9),
                    new Rect(body.x() + INSET, body.y() + 11, COL_MOVE, 9)
            };
        }
        // Anchored to the two-line block rather than the body, so a row that grew a reason line
        // does not pull its carets down away from the name they move.
        int mid = body.y() + (LINE1_Y + LINE2_Y + Controls.SEG_H) / 2;
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

    /**
     * Line two shares the row's flexible column with the stepper and the Details link, so the mode
     * button takes what is left rather than a fixed width. Everything after it is placed from its
     * right edge, which means the number changing width never shifts the link.
     */
    private Rect modeButton(Columns c, Rect body, Row row) {
        if (compactRows()) {
            Rect[] stepper = compactRowStepper(c, body, row);
            int right = row.mode().hasTarget() ? stepper[0].x() - 5 : compactContentRight(c);
            // The third line has no item icon. Reusing line one's post-icon indent threw away
            // enough width to clip "Keep in stock" at large GUI scales.
            int left = body.x() + INSET + COL_MOVE + COL_GAP;
            return new Rect(left, body.y() + COMPACT_LINE3_Y,
                    Math.max(24, Math.min(COL_MODE, right - left)), Controls.SEG_H);
        }
        int stepper = row.mode().hasTarget()
                ? Controls.stepperWidth(this.font, String.valueOf(row.target())) + 5 : 0;
        int details = this.font.width("Details") + 12 + 5;
        int room = (c.state + COL_STATE) - c.main - stepper - details;
        return new Rect(c.main, body.y() + LINE2_Y, Math.max(38, Math.min(COL_MODE, room)),
                Controls.SEG_H);
    }

    private Rect[] rowStepper(Columns c, Rect body, Row row) {
        if (compactRows()) return compactRowStepper(c, body, row);
        return Controls.stepperLayout(this.font, modeButton(c, body, row).right() + 5,
                body.y() + LINE2_Y, String.valueOf(row.target()));
    }

    private Rect[] compactRowStepper(Columns c, Rect body, Row row) {
        int width = Controls.stepperWidth(this.font, String.valueOf(row.target()));
        return Controls.stepperLayout(this.font, compactContentRight(c) - width,
                body.y() + COMPACT_LINE3_Y, String.valueOf(row.target()));
    }

    private static int compactContentRight(Columns c) {
        return c.marks - MARGIN_GAP - 7;
    }

    /** Right-aligned under the chip, so every row's Details sits on the same vertical line. */
    private Rect detailsLink(Columns c, Rect body, Row row) {
        int w = this.font.width("Details") + 12;
        if (compactRows()) {
            return new Rect(c.state - 5 - w, body.y() + COMPACT_LINE2_Y,
                    w, Controls.SEG_H);
        }
        return new Rect(c.state + COL_STATE - w, body.y() + LINE2_Y, w, Controls.SEG_H);
    }

    private Rect holdMark(Columns c, Rect body) {
        return new Rect(c.marks, body.y() + (compactRows() ? 5 : BAND_Y),
                Controls.MARK, Controls.MARK);
    }

    private Rect copyMark(Columns c, Rect body) {
        Rect hold = holdMark(c, body);
        if (compactRows()) {
            return new Rect(hold.x(), hold.bottom() + MARK_GAP, Controls.MARK, Controls.MARK);
        }
        return new Rect(hold.right() + MARK_GAP, hold.y(), Controls.MARK, Controls.MARK);
    }

    private Rect strikeMark(Columns c, Rect body) {
        Rect hold = holdMark(c, body);
        if (compactRows()) {
            return new Rect(hold.x(), hold.y() + (Controls.MARK + MARK_GAP) * 2,
                    Controls.MARK, Controls.MARK);
        }
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
        g.fill(x + INSET, y + 1, x + w - INSET, y + composerHeight() - INSET, 0x24FFB347);

        Controls.drawSlot(g, x + INSET + 3, y + 4);
        drawItem(g, d.icon, x + INSET + 4, y + 5);
        String draftTitle = (d.activity ? "NEW JOB — " : "NEW ORDER — ")
                + d.name().toUpperCase(Locale.ROOT);
        g.drawString(this.font, trim(draftTitle, w - INSET * 2 - 32),
                x + INSET + 26, y + 9, Palette.BAR_FILL, false);

        if (d.activity) {
            // A job has no number and no scope. Saying so is more use than four dead buttons.
            g.drawString(this.font, "Worked whenever there is any, in list order.",
                    x + INSET + 3, composerModeY() + 3, Palette.LABEL_DIM, false);
        } else {
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
        }

        Rect discard = composerDiscard();
        Controls.drawPill(g, this.font, discard, "Discard", true,
                discard.contains(mouseX, mouseY), Palette.LABEL_LIGHT);
        Rect add = composerAdd();
        Controls.drawButton(g, this.font, add, addLabel(), true,
                add.contains(mouseX, mouseY), true);
    }

    /** The commit button doubles as the hand-over hint when the draft is a commission. */
    private String addLabel() {
        Draft d = draft;
        if (d != null) {
            Option option = optionFor(d.product);
            if (option != null && option.commission()) return "Add held item";
        }
        return "Add to list";
    }

    // Three rows, because the mode picker alone is about two hundred pixels and everything used to
    // be laid on one line from both ends until it collided in the middle. The title row gets the
    // slot's full 18 before the mode row starts, which is what stops them shingling.
    private int composerModeY() { return composerTop() + 26; }
    private int composerActionY() { return composerTop() + 44 + (composerModesWrapped() ? 16 : 0); }

    /** Two rows of modes when four fully labelled segments do not fit the order pane. */
    private boolean composerModesWrapped() {
        Draft d = draft;
        if (d == null || d.activity) return false;
        Rect[] probe = Controls.segmentLayout(this.font, 0, 0, MODE_LABELS);
        return probe[probe.length - 1].right() > ordersWidth() - (INSET + 3) * 2;
    }

    /**
     * Whether the stepper-and-scope run from the left and the Discard-and-Add pair from the
     * right would meet in the middle of the action row. The same both-ends layout that already
     * collided once, and it still can on a narrow window — measured, not assumed.
     */
    private boolean composerCramped() {
        Draft d = draft;
        if (d == null || d.activity || !d.mode.hasTarget()) return false;
        Rect[] scope = Controls.segmentLayout(this.font, 0, 0, SCOPE_LABELS);
        int left = INSET + 3 + Controls.stepperWidth(this.font, String.valueOf(d.target))
                + 8 + scope[scope.length - 1].right();
        int right = this.font.width("Add to list") + 12 + 6
                + this.font.width("Discard") + 12 + INSET + 3;
        return left + 10 + right > ordersWidth();
    }

    /** A cramped composer grows a fourth row and the buttons take it, instead of colliding. */
    private int composerHeight() {
        return COMPOSER_H + (composerModesWrapped() ? 16 : 0) + (composerCramped() ? 18 : 0);
    }

    /** Where Discard and Add to list sit: the action row, or their own row beneath it. */
    private int composerButtonsY() { return composerCramped() ? composerActionY() + 18 : composerActionY(); }

    private Rect[] composerModes() {
        if (composerModesWrapped()) {
            int x = ordersLeft() + INSET + 3;
            int available = ordersWidth() - (INSET + 3) * 2;
            int each = available / 2;
            return new Rect[]{
                    new Rect(x, composerModeY(), each, Controls.SEG_H),
                    new Rect(x + each, composerModeY(), available - each, Controls.SEG_H),
                    new Rect(x, composerModeY() + 16, each, Controls.SEG_H),
                    new Rect(x + each, composerModeY() + 16, available - each, Controls.SEG_H)
            };
        }
        return Controls.segmentLayout(this.font, ordersLeft() + INSET + 3, composerModeY(), MODE_LABELS);
    }

    private Rect[] composerStepper() {
        return Controls.stepperLayout(this.font, ordersLeft() + INSET + 3, composerActionY(),
                String.valueOf(draft == null ? 0 : draft.target));
    }

    private Rect[] composerScope() {
        Rect[] stepper = composerStepper();
        return Controls.segmentLayout(this.font,
                stepper[Controls.STEPPER_PARTS - 1].right() + 8, composerActionY(), SCOPE_LABELS);
    }

    private Rect composerAdd() {
        int w = this.font.width(addLabel()) + 12;
        return new Rect(ordersLeft() + ordersWidth() - INSET - 3 - w, composerButtonsY(),
                w, Controls.SEG_H);
    }

    private Rect composerDiscard() {
        Rect add = composerAdd();
        Rect probe = Controls.pillLayout(this.font, 0, 0, "Discard");
        return new Rect(add.x() - 6 - probe.w(), composerButtonsY(), probe.w(), Controls.PILL_H);
    }

    // ── Details ──

    /** How wide the facts column is, and where the settings column starts after it. */
    private static final int FACTS_W = 150;
    private static final int DETAIL_PAD = 6;
    /**
     * The modal's own title strip, taller than the list strip's {@link #STRIP_H}: the slot gets
     * the same air above and below it, and the Back pill centres on the same band. The old strip
     * left the icon one pixel off its separator, which read as it resting on the floor.
     */
    private static final int DETAIL_STRIP_H = 26;

    private void drawDetails(GuiGraphics g, int mouseX, int mouseY) {
        Row row = detailsRow();
        if (row == null) return;
        g.fill(0, 0, this.width, this.height, 0xC00A0705);

        Rect win = detailsWindow();
        FrameRenderer.drawWoodenFrame(g, win.x(), win.y(), win.w(), win.h(), FRAME);
        FrameRenderer.drawInnerPanel(g, win.x(), win.y(), win.w(), win.h());

        // Its own title strip, inside the frame. Assignment is a dropdown in the order's header,
        // not a third full-width form section competing with the recipe and quantity controls.
        Controls.drawSlot(g, win.x() + DETAIL_PAD, win.y() + 4);
        drawItem(g, iconFor(row), win.x() + DETAIL_PAD + 1, win.y() + 5);
        Rect back = detailsBack();
        Rect assignment = detailsAssignment(row);
        String title = nameOf(row.activity() || row.tag(), row.label(), row.output());
        int titleLeft = win.x() + DETAIL_PAD + Controls.SLOT + 5;
        int titleRight = (workPicker ? back.x() : assignment.x()) - 4;
        String shown = trim(title, Math.max(20, titleRight - titleLeft));
        g.drawString(this.font, shown,
                titleLeft + Math.max(0, (titleRight - titleLeft - this.font.width(shown)) / 2),
                 win.y() + (DETAIL_STRIP_H - this.font.lineHeight) / 2 + 1, Palette.CARD, false);
        if (!workPicker) {
            String assignmentText = trim(assignmentSummary(row) + " ▾", assignment.w() - 10);
            Controls.drawPill(g, this.font, assignment, assignmentText, true,
                    assignment.contains(mouseX, mouseY), Palette.LABEL_LIGHT);
            tip(assignment, mouseX, mouseY, "Assignment");
        }
        Controls.drawPill(g, this.font, back, "Back", true, back.contains(mouseX, mouseY),
                Palette.LABEL_LIGHT);
        g.fill(win.x() + INSET, win.y() + DETAIL_STRIP_H - 1, win.right() - INSET,
                win.y() + DETAIL_STRIP_H, FrameRenderer.FRAME_HIGHLIGHT);

        if (workPicker) {
            drawWorkPicker(g, row, mouseX, mouseY);
            return;
        }
        if (row.activity()) {
            drawJobDetails(g, row, win, detailsFieldsTop());
            return;
        }
        if (row.tag()) {
            drawSetColumn(g, row, win.x() + DETAIL_PAD, detailsFieldsTop(), detailsFactsBottom());
        } else {
            drawFactsColumn(g, row, win.x() + DETAIL_PAD, detailsFieldsTop(), detailsFactsBottom());
        }
        drawSettingsColumn(g, row, mouseX, mouseY, detailsSettingsTop());
    }

    /** One member row per this many pixels: a 16px sprite plus its breathing room. */
    private static final int SET_ROW_H = 18;

    /**
     * A category's facts are its members. Reading it off the recipe column showed "Makes 1" and
     * "Needs Nothing", which are answers to questions a set was never asked. Every member is
     * drawn as itself — sprite and name — and the list scrolls by wheel or by its bar.
     */
    private void drawSetColumn(GuiGraphics g, Row row, int x, int top, int bottom) {
        Rect box = new Rect(x, top, factsWidth(), bottom - top);
        Controls.drawBox(g, this.font, box, "Counts as");
        List<ItemStack> members = setMembers(row.output());
        if (members.isEmpty()) {
            g.drawString(this.font, "Nothing counts yet.", x + 6, top + 8, Palette.LABEL_DIM, false);
            return;
        }
        drawStackRows(g, members, detailListArea(row));
    }

    /**
     * Icon-and-name rows with a scrollbar: the one way this window shows any list of items, so a
     * recipe's needs and a set's members read as the same furniture. Counts ride the sprite as a
     * vanilla stack badge, which is where a player's eye already looks for a quantity.
     */
    private void drawStackRows(GuiGraphics g, List<ItemStack> stacks, Rect area) {
        int visible = Math.max(1, area.h() / SET_ROW_H);
        setScroll = Math.max(0, Math.min(setScroll, stacks.size() - visible));
        g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        int y = area.y();
        for (int i = setScroll; i < stacks.size() && y < area.bottom(); i++) {
            ItemStack stack = stacks.get(i);
            g.renderItem(stack, area.x() + 4, y);
            g.renderItemDecorations(this.font, stack, area.x() + 4, y);
            g.drawString(this.font, trim(stack.getHoverName().getString(), area.w() - 31),
                    area.x() + 24, y + 4, Palette.LABEL_MID, false);
            y += SET_ROW_H;
        }
        g.disableScissor();
        Controls.drawScrollbar(g, area.right() - Controls.SCROLLBAR_W, area.y(), area.h(),
                setScroll, visible, stacks.size());
    }

    /**
     * Where the open modal's item rows sit: a set's members fill the box, a recipe's needs start
     * beneath its facts. One measure for drawing, the wheel and the bar, so the list a click
     * scrolls is exactly the list being looked at.
     */
    private Rect detailListArea(Row row) {
        Rect win = detailsWindow();
        int x = win.x() + DETAIL_PAD + 1;
        int top = detailsFieldsTop() + (row.tag() ? 6 : NEEDS_LIST_Y);
        int bottom = detailsFactsBottom() - 4;
        return new Rect(x, top, factsWidth() - 2, Math.max(1, bottom - top));
    }

    /**
     * The stack list the open modal is showing, or null when it shows none: members for a set,
     * needs for a thing, nothing for a job.
     */
    private @Nullable List<ItemStack> detailStacks(Row row) {
        if (row.activity()) return null;
        if (row.tag()) {
            List<ItemStack> members = setMembers(row.output());
            return members.isEmpty() ? null : members;
        }
        Option option = optionFor(row.product());
        List<OrdersSnapshotS2CPayload.Need> shown = shownNeeds(row, option);
        return shown.isEmpty() ? null : needStacks(shown);
    }

    /** A recipe's needs as stacks, count and all, for the shared row renderer. */
    private static List<ItemStack> needStacks(List<OrdersSnapshotS2CPayload.Need> needs) {
        List<ItemStack> out = new ArrayList<>(needs.size());
        long phase = net.minecraft.Util.getMillis() / 1200L;
        for (var need : needs) {
            if (need.items().isEmpty()) continue;
            int index = (int) Math.floorMod(phase, need.items().size());
            ItemStack stack = displayStack(need.items().get(index));
            if (stack.isEmpty()) continue;
            stack.setCount(Math.max(1, need.count()));
            out.add(stack);
        }
        return out;
    }

    /** Waiting orders foreground only what is absent; every other state shows the full recipe. */
    private static List<OrdersSnapshotS2CPayload.Need> shownNeeds(Row row, @Nullable Option option) {
        if (option == null) return List.of();
        return row.status() == OrdersSnapshotS2CPayload.Status.WAITING
                && !option.missing().isEmpty()
                ? option.missing() : option.needs();
    }

    /** The set's members as stacks, in tag order — what the counting side resolves too. */
    private static List<ItemStack> setMembers(ResourceLocation tagId) {
        var tag = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, tagId);
        List<ItemStack> out = new ArrayList<>();
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            out.add(new ItemStack(holder.value()));
        }
        return out;
    }

    /** Where a recipe's need rows begin inside the facts box: two fact lines and the header. */
    private static final int NEEDS_LIST_Y = 44;

    /**
     * What this order actually involves: how many a batch makes, where it is made, and what it
     * eats. Read off the catalogue entry for the same item, so it answers "why is this blocked"
     * before it blocks. Needs are drawn as the things themselves, exactly as a set's members are.
     */
    private void drawFactsColumn(GuiGraphics g, Row row, int x, int top, int bottom) {
        Rect box = new Rect(x, top, factsWidth(), bottom - top);
        Controls.drawBox(g, this.font, box, "This recipe");
        Option option = optionFor(row.product());
        if (option == null) {
            g.drawString(this.font, "Nothing here makes this.", x + 6, top + 9,
                    Palette.LABEL_DIM, false);
            return;
        }
        int y = top + 8;
        y = fact(g, x, y, box.w(), "Makes", String.valueOf(option.makes()));
        y = fact(g, x, y, box.w(), "Station", option.stationLabel());
        boolean showingMissing = row.status() == OrdersSnapshotS2CPayload.Status.WAITING
                && !option.missing().isEmpty();
        List<OrdersSnapshotS2CPayload.Need> shown = shownNeeds(row, option);
        g.drawString(this.font, showingMissing ? "MISSING" : "NEEDS", x + 6, y + 4,
                showingMissing ? Palette.LABEL_WARM : Palette.INK_DIM, false);
        if (shown.isEmpty()) {
            g.drawString(this.font, "Nothing", x + 6, y + 15, Palette.LABEL_DIM, false);
            return;
        }
        drawStackRows(g, needStacks(shown), detailListArea(row));
    }

    /**
     * A job's details. There is no recipe, no target and no scope, so the boxes that ask about
     * those are not drawn at all — showing "Makes 0" and an inert stepper was worse than showing
     * nothing, because it invited a player to change a number that does not exist.
     */
    private void drawJobDetails(GuiGraphics g, Row row, Rect win, int top) {
        Rect box = new Rect(win.x() + DETAIL_PAD, top,
                win.w() - DETAIL_PAD * 2, win.bottom() - DETAIL_PAD - top);
        Controls.drawBox(g, this.font, box, "This job");
        int x = box.x() + 6;
        int y = top + 9;
        for (var line : this.font.split(Component.literal(
                row.paused()
                        ? "Held. Nobody here will take this on until it is resumed."
                        : "Worked whenever there is any of it to do. Lines above this one are "
                                + "taken first."), box.w() - 12)) {
            g.drawString(this.font, line, x, y, Palette.LABEL_MID, false);
            y += this.font.lineHeight + 1;
        }
    }

    /** One label-and-value line, returning where the next one goes. */
    private int fact(GuiGraphics g, int x, int y, int w, String label, String value) {
        g.drawString(this.font, label, x + 6, y, Palette.LABEL_DIM, false);
        String shown = trim(value, w - 12 - this.font.width(label) - 6);
        g.drawString(this.font, shown, x + w - 6 - this.font.width(shown), y,
                Palette.LABEL_WARM, false);
        return y + 11;
    }

    private void drawSettingsColumn(GuiGraphics g, Row row, int mouseX, int mouseY, int top) {
        Rect win = detailsWindow();
        int x = settingsLeft();
        int w = win.right() - DETAIL_PAD - x;
        boolean hasTarget = row.mode().hasTarget();

        Rect howMuch = new Rect(x, top, w, 50);
        Controls.drawBox(g, this.font, howMuch, "How much");
        Rect[] modes = detailsModes();
        Controls.drawSegments(g, this.font, modes, MODE_LABELS, row.mode().ordinal(),
                Controls.segmentAt(modes, mouseX, mouseY));
        Rect[] stepper = detailsStepper(row);
        Controls.drawStepper(g, this.font, stepper, String.valueOf(row.target()), hasTarget,
                hitIndex(stepper, mouseX, mouseY));
        String have = "have " + row.have();
        g.drawString(this.font, have, howMuch.right() - 6 - this.font.width(have),
                stepper[0].y() + 3, Palette.LABEL_DIM, false);

        // Was headed "Ingredients", which it never was about: the scope says where the ORDER's
        // stock is counted, and with the stores pass it finally means what it says.
        Rect where = new Rect(x, top + 58, w, 30);
        Controls.drawBox(g, this.font, where, "Counting");
        Rect[] scope = detailsScope();
        g.drawString(this.font, "Counted across", where.x() + 6, scope[0].y() + 3,
                Palette.LABEL_DIM, false);
        Controls.drawSegments(g, this.font, scope, SCOPE_LABELS,
                hasTarget ? row.scope().ordinal() : -1,
                hasTarget ? Controls.segmentAt(scope, mouseX, mouseY) : -1);

    }

    private String assignmentSummary(Row row) {
        return row.workLabel().isEmpty() ? "Automatic" : row.workLabel();
    }

    private Rect detailsAssignment(Row row) {
        Rect win = detailsWindow();
        Rect back = detailsBack();
        return new Rect(back.x() - 4 - DETAIL_ACTION_W,
                win.y() + (DETAIL_STRIP_H - Controls.PILL_H) / 2,
                DETAIL_ACTION_W, Controls.PILL_H);
    }

    /** Focused assignment picker; ordinary details retain only the one-line summary above. */
    private void drawWorkPicker(GuiGraphics g, Row row, int mouseX, int mouseY) {
        Rect win = detailsWindow();
        int top = detailsFieldsTop();
        int gap = 6;
        int width = (win.w() - DETAIL_PAD * 2 - gap) / 2;
        int left = win.x() + DETAIL_PAD;
        int right = left + width + gap;
        Rect workerBox = new Rect(left, top, width, win.bottom() - DETAIL_PAD - top);
        Controls.drawBox(g, this.font, workerBox, "Worker");
        int visible = assignmentVisibleRows();
        int total = assignmentChoiceCount(row);
        setScroll = Math.max(0, Math.min(setScroll, Math.max(0, total - visible)));
        for (int shown = 0; shown < visible; shown++) {
            int actual = setScroll + shown;
            if (actual >= 1 + row.workers().size()) break;
            if (actual == 0) {
                drawWorkChoice(g, workerChoiceRect(shown), "Automatic", "Anyone suitable",
                        row.worker().isEmpty(), mouseX, mouseY);
            } else {
                var choice = row.workers().get(actual - 1);
                drawWorkChoice(g, workerChoiceRect(shown), choice.name(), choice.detail(),
                        choice.value().equals(row.worker()), mouseX, mouseY);
            }
        }

        Rect operatorBox = new Rect(right, top, width, win.bottom() - DETAIL_PAD - top);
        Controls.drawBox(g, this.font, operatorBox, "Operated by");
        if (!row.operated()) {
            g.drawString(this.font, "This station needs no operator.", right + 6, top + 10,
                    Palette.LABEL_DIM, false);
            if (total > visible) {
                Rect area = assignmentScrollArea();
                Controls.drawScrollbar(g, area.x(), area.y(), area.h(), setScroll, visible, total);
            }
            return;
        }
        int operatorCount = operatorChoiceCount(row);
        for (int shown = 0; shown < visible; shown++) {
            int actual = setScroll + shown;
            if (actual >= operatorCount) break;
            if (actual == 0) {
                drawWorkChoice(g, operatorChoiceRect(shown), "Automatic", "Station preference",
                        row.operation() == Order.Operation.AUTOMATIC, mouseX, mouseY);
            } else if (row.workerFallback() && actual == 1) {
                drawWorkChoice(g, operatorChoiceRect(shown), "Assigned worker",
                        "Operates it themselves", row.operation() == Order.Operation.WORKER,
                        mouseX, mouseY);
            } else {
                int index = actual - 1 - (row.workerFallback() ? 1 : 0);
                var choice = row.operators().get(index);
                drawWorkChoice(g, operatorChoiceRect(shown), choice.name(), "Eligible animal",
                        row.operation() == Order.Operation.ENTITY
                                && choice.uuid().equals(row.operator()), mouseX, mouseY);
            }
        }
        if (total > visible) {
            Rect area = assignmentScrollArea();
            Controls.drawScrollbar(g, area.x(), area.y(), area.h(), setScroll, visible, total);
        }
    }

    private int operatorChoiceCount(Row row) {
        return row.operated() ? 1 + (row.workerFallback() ? 1 : 0) + row.operators().size() : 0;
    }

    private int assignmentChoiceCount(Row row) {
        return Math.max(1 + row.workers().size(), operatorChoiceCount(row));
    }

    private int assignmentVisibleRows() {
        Rect win = detailsWindow();
        return Math.max(1, (win.bottom() - DETAIL_PAD - (detailsFieldsTop() + 7)) / 17);
    }

    private Rect assignmentScrollArea() {
        Rect win = detailsWindow();
        return new Rect(win.right() - DETAIL_PAD - Controls.SCROLLBAR_W,
                detailsFieldsTop() + 7, Controls.SCROLLBAR_W,
                Math.max(1, win.bottom() - DETAIL_PAD - (detailsFieldsTop() + 7)));
    }

    private void drawWorkChoice(GuiGraphics g, Rect rect, String name, String detail,
                                boolean selected, int mouseX, int mouseY) {
        boolean hover = rect.contains(mouseX, mouseY);
        g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                selected ? Palette.TAB_IDLE : hover ? Palette.ROW : Palette.WELL);
        if (selected || hover) Palette.drawOutline(g, rect.x(), rect.y(), rect.right(), rect.bottom(),
                selected ? Palette.BRASS_DEEP : Palette.WELL_EDGE);
        int mark = rect.x() + 5;
        if (selected) g.fill(mark, rect.y() + 4, mark + 2, rect.bottom() - 4, Palette.BRASS);
        int x = rect.x() + 11;
        int detailW = detail.isEmpty() ? 0 : this.font.width(detail);
        g.drawString(this.font, trim(name, Math.max(12, rect.w() - 17 - detailW)), x,
                rect.y() + 4, selected ? Palette.CARD : Palette.LABEL_LIGHT, false);
        if (!detail.isEmpty() && rect.w() > detailW + 45) {
            g.drawString(this.font, detail, rect.right() - 5 - detailW, rect.y() + 4,
                    Palette.LABEL_DIM, false);
        }
    }

    private Rect workerChoiceRect(int index) {
        Rect win = detailsWindow();
        int width = (win.w() - DETAIL_PAD * 2 - 6) / 2;
        return new Rect(win.x() + DETAIL_PAD + 3, detailsFieldsTop() + 7 + index * 17,
                width - 6, 16);
    }

    private Rect operatorChoiceRect(int index) {
        Rect win = detailsWindow();
        int width = (win.w() - DETAIL_PAD * 2 - 6) / 2;
        return new Rect(win.x() + DETAIL_PAD + width + 9, detailsFieldsTop() + 7 + index * 17,
                width - 8 - Controls.SCROLLBAR_W, 16);
    }

    /** What sprite a line shows: itself for a thing, a member's for a set, a job's own icon. */
    private ResourceLocation iconFor(Row row) {
        if (row.tag()) return tagIcon(row.output());
        if (!row.activity()) return row.product();
        Option option = optionFor(row.product());
        return option == null ? row.output() : option.stationIcon();
    }

    /**
     * A tag has no sprite of its own, so it borrows its first member's. The client's tags are
     * synced from the server, so this resolves the same set the order counts.
     */
    private static ResourceLocation tagIcon(ResourceLocation tagId) {
        var tag = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, tagId);
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            return BuiltInRegistries.ITEM.getKey(holder.value());
        }
        return BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.AIR);
    }

    /** The catalogue entry for this output, which is where the recipe's own facts live. */
    private @Nullable Option optionFor(ResourceLocation product) {
        for (Option option : data.options()) {
            if (option.product().equals(product)) return option;
        }
        return null;
    }

    private int settingsLeft() {
        return detailsStacked()
                ? detailsWindow().x() + DETAIL_PAD
                : detailsWindow().x() + DETAIL_PAD + FACTS_W + DETAIL_PAD;
    }

    private boolean detailsStacked() { return this.width < 470; }

    private int factsWidth() {
        return detailsStacked() ? detailsWindow().w() - DETAIL_PAD * 2 : FACTS_W;
    }

    private Rect detailsWindow() {
        // A job's window holds two lines and a name; giving it the recipe window's footprint left
        // a plain sentence adrift in a panel sized for controls it never shows.
        Row row = detailsRow();
        if (workPicker) {
            int choices = Math.max(1 + (row == null ? 0 : row.workers().size()),
                    (row != null && row.operated() ? 1 : 0)
                            + (row != null && row.workerFallback() ? 1 : 0)
                            + (row == null ? 0 : row.operators().size()));
            int w = Math.min(this.width - SPACING * 2 - FRAME * 2, 390);
            int desiredH = DETAIL_STRIP_H + DETAIL_PAD + 4 + 14 + choices * 17 + DETAIL_PAD;
            int h = Math.min(this.height - SPACING * 2 - FRAME * 2, Math.max(142, desiredH));
            return new Rect((this.width - w) / 2, (this.height - h) / 2, w, h);
        }
        if (row != null && row.activity()) {
            int w = Math.min(this.width - SPACING * 2 - FRAME * 2, 300);
            int h = DETAIL_STRIP_H + DETAIL_PAD + 4 + 44 + DETAIL_PAD;
            return new Rect((this.width - w) / 2, (this.height - h) / 2, w, h);
        }
        Rect[] modes = Controls.segmentLayout(this.font, 0, 0, MODE_LABELS);
        Rect[] scopes = Controls.segmentLayout(this.font, 0, 0, SCOPE_LABELS);
        int settings = Math.max(modes[modes.length - 1].right() + 12,
                this.font.width("Counted across") + 8 + scopes[scopes.length - 1].right() + 12);
        if (detailsStacked()) {
            int w = Math.min(this.width - SPACING * 2 - FRAME * 2, 360);
            int desiredH = DETAIL_STRIP_H + DETAIL_PAD + 4 + 88 + DETAIL_PAD + 88 + DETAIL_PAD;
            int h = Math.min(this.height - SPACING * 2 - FRAME * 2, desiredH);
            return new Rect((this.width - w) / 2, (this.height - h) / 2, w, h);
        }
        int w = Math.min(this.width - SPACING * 2 - FRAME * 2,
                DETAIL_PAD + FACTS_W + DETAIL_PAD + settings + DETAIL_PAD);
        // Both columns end together: the left list scrolls for the rest, so a taller window
        // would only buy dead panel under the settings, which is what stood where the removed
        // "who may work it" box used to.
        int content = 88;
        int h = DETAIL_STRIP_H + DETAIL_PAD + 4 + content + DETAIL_PAD;
        return new Rect((this.width - w) / 2, (this.height - h) / 2, w, h);
    }

    private Rect detailsBack() {
        Rect win = detailsWindow();
        return new Rect(win.right() - INSET - 2 - DETAIL_ACTION_W,
                win.y() + (DETAIL_STRIP_H - Controls.PILL_H) / 2,
                DETAIL_ACTION_W, Controls.PILL_H);
    }

    private int detailsFieldsTop() {
        return detailsWindow().y() + DETAIL_STRIP_H + DETAIL_PAD + 4;
    }

    /** Bottom of the facts/list box; on compact screens the settings begin beneath it. */
    private int detailsFactsBottom() {
        Rect win = detailsWindow();
        if (!detailsStacked()) return win.bottom() - DETAIL_PAD;
        int reservedSettings = 88;
        return Math.max(detailsFieldsTop() + 54,
                win.bottom() - DETAIL_PAD - reservedSettings - DETAIL_PAD);
    }

    private int detailsSettingsTop() {
        return detailsStacked() ? detailsFactsBottom() + DETAIL_PAD : detailsFieldsTop();
    }

    private Rect[] detailsModes() {
        return Controls.segmentLayout(this.font, settingsLeft() + 6, detailsSettingsTop() + 8,
                MODE_LABELS);
    }

    private Rect[] detailsStepper(Row row) {
        return Controls.stepperLayout(this.font, settingsLeft() + 6, detailsSettingsTop() + 28,
                String.valueOf(row.target()));
    }

    private Rect[] detailsScope() {
        return Controls.segmentLayout(this.font,
                settingsLeft() + 6 + this.font.width("Counted across") + 8,
                detailsSettingsTop() + 66, SCOPE_LABELS);
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

        int tab = Controls.segmentAt(catalogueTabs(), mouseX, mouseY);
        if (tab >= 0) {
            if (jobsTab != (tab == 1)) {
                jobsTab = tab == 1;
                if (makeableButton != null) makeableButton.visible = !jobsTab;
                refreshCatalogue();
            }
            return true;
        }
        int listChoice = Controls.segmentAt(listSegments(), mouseX, mouseY);
        if (listChoice >= 0) {
            send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_LIST_ONLY, 0, listChoice));
            return true;
        }
        if (draft != null && clickComposer(mouseX, mouseY, site)) return true;
        int picked = Controls.scrollbarPick(
                ordersLeft() + ordersWidth() - INSET - Controls.SCROLLBAR_W, listTop(),
                orderListBottom() - INSET - listTop(), mouseX, mouseY,
                shownRows(), data.rows().size());
        if (picked >= 0) {
            orderScroll = picked;
            dragOrderScroll = true;
            return true;
        }
        return clickOrders(mouseX, mouseY, site);
    }

    /** How many rows fit from the current scroll, which is what the bar's proportions are. */
    private int shownRows() {
        List<Row> rows = data.rows();
        int bottom = orderListBottom() - INSET;
        int rowY = listTop() + 2;
        int shown = 0;
        for (int i = orderScroll; i < rows.size() && rowY < bottom; i++) {
            rowY += rowBaseHeight() + (rows.get(i).reason().isEmpty() ? 0 : REASON_H);
            shown++;
        }
        return shown;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
        if (dragAssignmentScroll && detailsFor >= 0 && workPicker) {
            Row row = detailsRow();
            if (row != null) {
                Rect area = assignmentScrollArea();
                setScroll = Controls.scrollbarDrag(area.y(), area.h(), my,
                        assignmentVisibleRows(), assignmentChoiceCount(row));
            }
            return true;
        }
        if (dragDetailScroll && detailsFor >= 0) {
            Row row = detailsRow();
            List<ItemStack> stacks = row == null ? null : detailStacks(row);
            if (stacks != null) {
                Rect area = detailListArea(row);
                setScroll = Controls.scrollbarDrag(area.y(), area.h(), my,
                        Math.max(1, area.h() / SET_ROW_H), stacks.size());
            }
            return true;
        }
        if (dragOrderScroll) {
            orderScroll = Controls.scrollbarDrag(listTop(), orderListBottom() - INSET - listTop(),
                    my, shownRows(), data.rows().size());
            return true;
        }
        return super.mouseDragged(mx, my, button, ddx, ddy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragDetailScroll = false;
        dragAssignmentScroll = false;
        dragOrderScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    private boolean clickOrders(double mx, double my, long site) {
        List<Row> rows = data.rows();
        int bottom = orderListBottom() - INSET;
        int rowY = listTop() + 2;
        int x = ordersLeft();
        int w = ordersWidth();
        for (int i = orderScroll; i < rows.size() && rowY < bottom; i++) {
            Row row = rows.get(i);
            int height = rowBaseHeight() + (row.reason().isEmpty() ? 0 : REASON_H);
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
            if (detailsLink(c, body, row).contains(mx, my)) {
                detailsFor = i;
                workPicker = false;
                setScroll = 0;
                return true;
            }
            if (!row.activity() && modeButton(c, body, row).contains(mx, my)) {
                send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_MODE, i,
                        nextMode(row.mode()).name()));
                return true;
            }
            if (row.mode().hasTarget() && !row.activity()) {
                int arrow = hitIndex(rowStepper(c, body, row), mx, my);
                if (stepFor(arrow) != 0) {
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
        int mode = d.activity ? -1 : Controls.segmentAt(composerModes(), mx, my);
        if (mode >= 0) {
            d.mode = Order.Mode.values()[mode];
            return true;
        }
        if (!d.activity && d.mode.hasTarget()) {
            int arrow = hitIndex(composerStepper(), mx, my);
            if (stepFor(arrow) != 0) {
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
        // A commission carries no item over the wire — only which of the player's slots the
        // server should take the workpiece from. The held slot is the hand-over gesture.
        Option commissioned = optionFor(d.product);
        if (commissioned != null && commissioned.commission()) {
            int slot = this.minecraft != null && this.minecraft.player != null
                    ? this.minecraft.player.getInventory().selected : 0;
            send(new OrderEditC2SPayload(site, OrderEditC2SPayload.Action.COMMISSION,
                    slot, Math.max(1, d.target), d.output.toString()));
            draft = null;
            return;
        }
        // A category commits with the tag marker, which is how the server knows to add a set line.
        send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.ADD, 0,
                d.tag ? "#" + d.output : d.product.toString()));
        if (d.activity) {
            // The server already knows a job is standing and has no target; sending settings for
            // one would only be refused.
            draft = null;
            return;
        }
        send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_MODE, index, d.mode.name()));
        if (d.mode.hasTarget()) {
            send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_TARGET, index, d.target));
            send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_SCOPE, index, d.scope.name()));
        }
        draft = null;
    }

    private boolean clickDetails(double mx, double my, long site) {
        Row row = detailsRow();
        if (row == null) {
            detailsFor = -1;
            workPicker = false;
            return true;
        }
        if (detailsBack().contains(mx, my)) {
            if (workPicker) {
                workPicker = false;
                setScroll = 0;
            }
            else detailsFor = -1;
            return true;
        }
        if (workPicker) return clickWorkPicker(row, mx, my, site);
        if (detailsAssignment(row).contains(mx, my)) {
            workPicker = true;
            setScroll = 0;
            return true;
        }
        if (row.activity()) return true;
        // The list's scrollbar takes a press anywhere along its lane, then rides the drag.
        List<ItemStack> stacks = detailStacks(row);
        if (stacks != null) {
            Rect area = detailListArea(row);
            int picked = Controls.scrollbarPick(area.right() - Controls.SCROLLBAR_W, area.y(),
                    area.h(), mx, my, Math.max(1, area.h() / SET_ROW_H), stacks.size());
            if (picked >= 0) {
                setScroll = picked;
                dragDetailScroll = true;
                return true;
            }
        }
        int mode = Controls.segmentAt(detailsModes(), mx, my);
        if (mode >= 0) {
            send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_MODE, detailsFor,
                    Order.Mode.values()[mode].name()));
            return true;
        }
        if (row.mode().hasTarget()) {
            int arrow = hitIndex(detailsStepper(row), mx, my);
            if (stepFor(arrow) != 0) {
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

    private boolean clickWorkPicker(Row row, double mx, double my, long site) {
        int visible = assignmentVisibleRows();
        int total = assignmentChoiceCount(row);
        if (total > visible) {
            Rect area = assignmentScrollArea();
            int picked = Controls.scrollbarPick(area.x(), area.y(), area.h(), mx, my,
                    visible, total);
            if (picked >= 0) {
                setScroll = picked;
                dragAssignmentScroll = true;
                return true;
            }
        }

        for (int shown = 0; shown < visible; shown++) {
            int actual = setScroll + shown;
            if (actual < 1 + row.workers().size() && workerChoiceRect(shown).contains(mx, my)) {
                String value = actual == 0 ? "automatic" : row.workers().get(actual - 1).value();
                send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_WORKER,
                        detailsFor, value));
                return true;
            }
            if (!row.operated() || actual >= operatorChoiceCount(row)
                    || !operatorChoiceRect(shown).contains(mx, my)) continue;
            String value;
            if (actual == 0) value = "automatic";
            else if (row.workerFallback() && actual == 1) value = "worker";
            else {
                int index = actual - 1 - (row.workerFallback() ? 1 : 0);
                value = "entity:" + row.operators().get(index).uuid();
            }
            send(OrderEditC2SPayload.of(site, OrderEditC2SPayload.Action.SET_OPERATOR,
                    detailsFor, value));
            return true;
        }
        return true;
    }

    /**
     * How far a click on a stepper part moves the number, or 0 for the value plate.
     *
     * <p>This used to hard-code five cases against a stepper that only had three parts, so the
     * value plate silently decremented and {@code +} did nothing at all. It reads the amounts from
     * the control itself now, and the control publishes how many parts it has.</p>
     */
    private static int stepFor(int part) {
        return part < 0 || part >= Controls.STEP_AMOUNTS.length ? 0 : Controls.STEP_AMOUNTS[part];
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

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) {
            // Escape unwinds one layer at a time: the modal, then the draft, then the screen.
            if (detailsFor >= 0) {
                if (workPicker) {
                    workPicker = false;
                    setScroll = 0;
                }
                else detailsFor = -1;
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
        // The modal owns the wheel while it is open: whatever item list it is showing scrolls —
        // a set's members or a recipe's needs — and nothing leaks through to the order list.
        if (detailsFor >= 0) {
            Row row = detailsRow();
            if (row != null && workPicker) {
                int max = Math.max(0, assignmentChoiceCount(row) - assignmentVisibleRows());
                setScroll = Math.max(0, Math.min(max, setScroll + (dy > 0 ? -1 : 1)));
            } else if (row != null && detailStacks(row) != null) {
                setScroll = Math.max(0, setScroll + (dy > 0 ? -1 : 1));
            }
            return true;
        }
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
        // The server pushes snapshots at whoever is watching; say when to stop.
        send(OrderEditC2SPayload.of(data.worksiteId(), OrderEditC2SPayload.Action.CLOSED, 0));
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

    /** What a line or an option is called: its own label when it carries one, else the item's name. */
    private static String nameOf(boolean useLabel, String label, ResourceLocation id) {
        // A non-empty label always wins: jobs and categories name themselves, and so do
        // commissions ("Copy Filled Map") and their lines ("Copy 'Village Map'").
        return !label.isEmpty() ? label : itemName(id);
    }

    private static String itemName(ResourceLocation id) {
        return displayStack(id).getHoverName().getString();
    }

    private static void drawItem(GuiGraphics g, ResourceLocation id, int x, int y) {
        ItemStack stack = displayStack(id);
        if (!stack.isEmpty()) g.renderItem(stack, x, y);
    }

    /**
     * The stack a raw id is drawn as. An order line carries only an item id, and a bare
     * {@code minecraft:potion} has no contents behind it, so it rendered — and named itself —
     * "Uncraftable Potion". The only potion any station here handles is boiled water, so it is
     * shown as what it will actually be.
     */
    private static ItemStack displayStack(ResourceLocation id) {
        ResourceLocation fallback = com.aetherianartificer.townstead.work.order.OrderProducts
                .fallbackItem(id);
        if (fallback != null) {
            return com.aetherianartificer.townstead.work.order.OrderProducts.displayStack(
                    id, fallback);
        }
        // Supply lines have no item behind them; furnace fuel is shown as the fuel everyone
        // reaches for, named for what the line actually accepts.
        if (com.aetherianartificer.townstead.supply.TownsteadSupplyLines.FURNACE_FUEL.equals(id)) {
            ItemStack fuel = new ItemStack(net.minecraft.world.item.Items.COAL);
            //? if >=1.21 {
            fuel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal("Any furnace fuel"));
            //?} else {
            /*fuel.setHoverName(net.minecraft.network.chat.Component.literal("Any furnace fuel"));
            *///?}
            return fuel;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        if (stack.is(net.minecraft.world.item.Items.POTION)) {
            //? if >=1.21 {
            stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    new net.minecraft.world.item.alchemy.PotionContents(
                            net.minecraft.world.item.alchemy.Potions.WATER));
            //?} else {
            /*net.minecraft.world.item.alchemy.PotionUtils.setPotion(stack,
                    net.minecraft.world.item.alchemy.Potions.WATER);
            *///?}
        }
        return stack;
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
