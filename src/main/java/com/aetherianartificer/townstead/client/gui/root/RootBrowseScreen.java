package com.aetherianartificer.townstead.client.gui.root;

import com.aetherianartificer.townstead.client.gui.common.IconArt;
import com.aetherianartificer.townstead.client.gui.quest.QuestIconButton;
import com.aetherianartificer.townstead.client.gui.quest.QuestIcons;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.Facet;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.GroupBy;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.Item;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.Section;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.Shortcut;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.Value;
import com.aetherianartificer.townstead.client.gui.root.RootBrowseModel.View;
import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootWorldCounts;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootBrowseEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Every root at once, opened from the Root tab's "Browse roots..." button. Filters on the left
 * (shortcuts, species, pack, traits), every result grouped in the middle, the picked root on the
 * right with the editor's own model wearing it. Filters that split nothing are left out, so a small
 * catalogue gets a small screen.
 *
 * <p>It wears the MCA editor's greys and borrows the Quest Ledger's icons, tab strip and spacing
 * (a {@value #GUTTER}px gutter, {@value #PAD}px panel padding), so it reads as part of the same
 * family. Choosing returns to the editor with the root previewed, not applied; Back returns with
 * whatever was previewed before.</p>
 */
public final class RootBrowseScreen extends Screen {

    // The Quest Ledger's grid.
    private static final int GUTTER = 10;
    private static final int HEADER_H = 20;
    private static final int HEADER_GAP = 6;
    private static final int PAD = 6;
    private static final int ROW = 12;
    private static final int TILE = 16;
    private static final int TILE_GAP = 2;
    private static final int SECTION_GAP = 6;
    private static final int FACET_VALUES = 5;

    private static final int SCRIM = 0xA0000000;
    private static final int PANEL = 0xE8101010;
    private static final int RULE = 0xFF2E2E2E;
    private static final int COUNT = 0xFF707070;
    private static final int TEXT = 0xFFD8D8D8;
    private static final int MUTED = 0xFF8A8A8A;
    private static final int CHOSEN_FILL = 0xFF34511C;
    private static final int ROW_HOVER = 0xFF2A2A2A;
    private static final int TILE_FILL = 0xFF1A1A1A;
    private static final int TILE_EDGE = 0xFF2E2E2E;
    private static final int TILE_HOVER = 0xFF2C2C2C;
    private static final int TILE_SELECTED = 0xFF3A3A3A;
    private static final int TILE_SELECTED_EDGE = 0xFFA0A0A0;
    private static final int TILE_CURRENT = 0xFF2F6B2F;
    private static final int SEG_ACTIVE = 0xFF5A8A2A;
    private static final int SEG_ACTIVE_EDGE = 0xFF88DD44;
    private static final int SEG_NORMAL = 0xFF3A3A3A;
    private static final int SEG_HOVER = 0xFF5A5A5A;
    private static final int SEG_DIVIDER = 0xFF222222;

    private static final ResourceLocation HANGOUT = ResourceLocation.tryParse("townstead:textures/gui/catalog/hangout.png");
    private static final ResourceLocation LETTER = ResourceLocation.tryParse("townstead:textures/gui/quest/letter.png");

    private static final List<String> TRAITS = List.of("winged", "small", "large", "aquatic", "lays_eggs",
            "nocturnal", "darkvision", "ageless", "sterile");

    private final Screen parent;
    private final @Nullable LivingEntity model;
    private final Consumer<RootCatalogEntry> onPreview;
    private final Consumer<RootCatalogEntry> onReturn;
    private final String currentId;
    private final @Nullable RootCatalogEntry initial;
    private final Map<String, RootCatalogEntry> entries = new LinkedHashMap<>();
    private final RootBrowseModel browser;
    private final RootBrowseModel.State state = new RootBrowseModel.State();
    private final Set<Facet> openFacets = new LinkedHashSet<>();
    private final boolean manyPacks;

    private @Nullable RootCatalogEntry selected;
    private View view;
    private EditBox search;
    private Button pin;
    private Button choose;
    private final List<Hit> hits = new ArrayList<>();
    private final RootDetails.Hover hover = new RootDetails.Hover();
    private final Scroll railScroll = new Scroll();
    private final Scroll resultScroll = new Scroll();
    private final Scroll detailScroll = new Scroll();
    private long lastClick;
    private String lastClickedId = "";

    // Layout, recomputed in init. Each panel is its outer box; content sits PAD inside it.
    private int top, bottom, railX, railW, centerX, centerW, previewX, previewW, buttonsY;

    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    /** One scrollable panel's offset and how far it can go, learned from the last frame. */
    private static final class Scroll {
        double offset;
        int max;

        void by(double delta, int step) {
            offset = Math.max(0, Math.min(max, offset - delta * step));
        }

        void settle(int contentBottom, int viewBottom) {
            max = Math.max(0, contentBottom + (int) offset - viewBottom);
            offset = Math.min(offset, max);
        }
    }

    public RootBrowseScreen(Screen parent, boolean self, @Nullable LivingEntity model, String currentId,
                            @Nullable RootCatalogEntry initial, Consumer<RootCatalogEntry> onPreview,
                            Consumer<RootCatalogEntry> onReturn) {
        super(Component.translatable("townstead.root.browse.title"));
        this.parent = parent;
        this.model = model;
        this.currentId = currentId;
        this.initial = initial;
        this.selected = initial;
        this.onPreview = onPreview;
        this.onReturn = onReturn;
        List<Item> items = new ArrayList<>();
        Set<String> packs = new LinkedHashSet<>();
        for (RootCatalogEntry entry : RootCatalogClient.origins()) {
            if (!entry.listedFor(self)) continue;
            entries.put(entry.id(), entry);
            Item item = item(entry, RootCatalogClient.browse(entry.id()));
            items.add(item);
            packs.add(item.pack());
        }
        this.manyPacks = packs.size() > 1;
        Map<String, String> traitLabels = new LinkedHashMap<>();
        for (String trait : TRAITS) traitLabels.put(trait, Component.translatable("townstead.root.trait." + trait).getString());
        this.browser = new RootBrowseModel(items, traitLabels);
    }

    private static Item item(RootCatalogEntry entry, @Nullable RootBrowseEntry browse) {
        String pack = browse != null ? browse.pack() : namespace(entry.id());
        String packName = browse != null ? RootDetails.text(browse.packName()).getString() : pack;
        StringBuilder search = new StringBuilder();
        search.append(entry.name()).append('\n').append(entry.demonymPlural()).append('\n')
                .append(entry.speciesName()).append('\n').append(entry.ancestryName()).append('\n')
                .append(entry.lineageName()).append('\n').append(packName);
        Set<String> traits = Set.of();
        boolean featured = false;
        if (browse != null) {
            search.append('\n').append(RootDetails.text(browse.packQualifier()).getString());
            for (RootBrowseEntry.Power power : browse.powers()) {
                GeneCatalogEntry gene = RootCatalogClient.gene(power.geneId());
                if (gene != null) search.append('\n').append(gene.name());
            }
            for (RootBrowseEntry.Fact fact : browse.facts()) search.append('\n').append(RootDetails.text(fact.value()).getString());
            traits = new LinkedHashSet<>(browse.traits());
            featured = browse.featured();
        }
        String speciesId = browse != null && !browse.speciesId().isEmpty() ? browse.speciesId() : entry.speciesName();
        String ancestryId = browse != null && !browse.ancestryId().isEmpty() ? browse.ancestryId() : entry.ancestryName();
        return new Item(entry.id(), entry.name(), speciesId, entry.speciesName(), ancestryId, entry.ancestryName(),
                pack, packName, traits, featured, search.toString());
    }

    private static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    // ---- lifecycle ----

    @Override
    protected void init() {
        top = GUTTER + HEADER_H + HEADER_GAP;
        bottom = height - GUTTER;
        railW = Math.max(110, Math.min(150, width / 5));
        previewW = Math.max(130, Math.min(180, width / 4));
        railX = GUTTER;
        previewX = width - GUTTER - previewW;
        centerX = railX + railW + GUTTER;
        centerW = previewX - GUTTER - centerX;

        int backW = Math.max(50, font.width(Component.translatable("gui.back")) + 16);
        int backX = width - GUTTER - backW;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(backX, GUTTER, backW, HEADER_H).build());
        // Search spans the results column so the header lines up with the panels below it.
        int searchX = centerX + 13;
        int searchRight = manyPacks ? Math.min(previewX - GUTTER, backX - GUTTER - segmentWidth() - GUTTER)
                : previewX - GUTTER;
        String previous = search != null ? search.getValue() : state.query;
        search = new EditBox(font, searchX + 1, GUTTER + 1, Math.max(60, searchRight - searchX - 2), HEADER_H - 2,
                Component.translatable("townstead.root.browse.search"));
        search.setHint(Component.translatable("townstead.root.browse.search"));
        search.setMaxLength(80);
        search.setValue(previous);
        search.setResponder(value -> {
            state.query = value;
            resultScroll.offset = 0;
            refresh();
        });
        addRenderableWidget(search);

        buttonsY = bottom - PAD - 20;
        int inner = previewW - PAD * 2;
        int pinW = (inner - PAD) / 3;
        pin = addRenderableWidget(new QuestIconButton(previewX + PAD, buttonsY, pinW, 20,
                Component.translatable("townstead.root.browse.pin"), b -> togglePin(), QuestIcons.Icon.PINNED));
        choose = addRenderableWidget(new QuestIconButton(previewX + PAD + pinW + PAD, buttonsY, inner - pinW - PAD, 20,
                Component.translatable("townstead.root.browse.choose"), b -> chooseSelected(), QuestIcons.Icon.DONE));
        updateButtons();
        refresh();
        RootWorldCounts.request(() -> Minecraft.getInstance().execute(this::refresh));
    }

    @Override
    public void removed() {
        RootWorldCounts.stopListening();
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft == null) return;
        if (initial != null) onReturn.accept(initial);
        minecraft.setScreen(parent);
    }

    private void chooseSelected() {
        if (selected == null || minecraft == null) return;
        RootBrowsePrefs.viewed(selected.id());
        onReturn.accept(selected);
        minecraft.setScreen(parent);
    }

    private void togglePin() {
        if (selected == null) return;
        RootBrowsePrefs.togglePin(selected.id());
        updateButtons();
        refresh();
    }

    private void select(String id) {
        RootCatalogEntry entry = entries.get(id);
        if (entry == null) return;
        long now = System.currentTimeMillis();
        boolean again = id.equals(lastClickedId) && now - lastClick < 350;
        lastClick = now;
        lastClickedId = id;
        if (again && entry == selected) {
            chooseSelected();
            return;
        }
        selected = entry;
        detailScroll.offset = 0;
        onPreview.accept(entry);
        updateButtons();
    }

    private void updateButtons() {
        if (pin == null) return;
        pin.active = selected != null;
        choose.active = selected != null;
        pin.setMessage(Component.translatable(selected != null && RootBrowsePrefs.isPinned(selected.id())
                ? "townstead.root.browse.unpin" : "townstead.root.browse.pin"));
    }

    private void refresh() {
        view = browser.view(state, new RootBrowseModel.Context(
                RootBrowsePrefs.recent(), RootBrowsePrefs.pinned(), RootWorldCounts.counts()));
    }

    private int columns() {
        return Math.max(1, Math.min(4, (centerW - PAD * 2) / 100));
    }

    // ---- rendering ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        hover.clear();
        //? if >=1.21 {
        renderBackground(g, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(g);
        *///?}
        g.fill(0, 0, width, height, SCRIM);
        drawHeader(g, mouseX, mouseY);
        panel(g, railX, railW);
        panel(g, centerX, centerW);
        panel(g, previewX, previewW);
        drawRail(g, mouseX, mouseY);
        drawResults(g, mouseX, mouseY);
        drawPreview(g, mouseX, mouseY);
        for (var child : children()) {
            if (child instanceof Renderable r) r.render(g, mouseX, mouseY, partialTick);
        }
        hover.draw(g, font, mouseX, mouseY);
    }

    private void panel(GuiGraphics g, int x, int w) {
        g.fill(x, top, x + w, bottom, PANEL);
        RootPanel.drawBorder(g, x, top, x + w, bottom);
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        int textY = GUTTER + (HEADER_H - 8) / 2;
        g.drawString(font, title, railX, textY, 0xFFFFFFFF, false);
        String count = view.matching() == view.total() ? Integer.toString(view.total())
                : Component.translatable("townstead.root.browse.of", view.matching(), view.total()).getString();
        g.drawString(font, count, railX + font.width(title) + PAD, textY, COUNT, false);
        QuestIcons.draw(g, QuestIcons.Icon.DETAILS, centerX, GUTTER + (HEADER_H - 9) / 2);
        if (manyPacks) drawSegments(g, mouseX, mouseY);
    }

    private int segmentWidth() {
        int w = 0;
        for (GroupBy by : GroupBy.values()) w += font.width(groupLabel(by)) + PAD * 2;
        return w;
    }

    private Component groupLabel(GroupBy by) {
        return Component.translatable("townstead.root.browse.group." + by.name().toLowerCase(Locale.ROOT));
    }

    /** The Quest Ledger's view tab strip: grey cells, the active one green with a bright top edge. */
    private void drawSegments(GuiGraphics g, int mouseX, int mouseY) {
        int x = width - GUTTER - Math.max(50, font.width(Component.translatable("gui.back")) + 16) - GUTTER - segmentWidth();
        int y = GUTTER;
        for (GroupBy by : GroupBy.values()) {
            Component label = groupLabel(by);
            int w = font.width(label) + PAD * 2;
            boolean chosen = state.groupBy == by;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + HEADER_H;
            g.fill(x, y, x + w, y + HEADER_H, chosen ? SEG_ACTIVE : hovered ? SEG_HOVER : SEG_NORMAL);
            g.fill(x, y, x + w, y + 1, chosen ? SEG_ACTIVE_EDGE : 0xFF555555);
            g.fill(x, y + HEADER_H - 1, x + w, y + HEADER_H, SEG_DIVIDER);
            if (by.ordinal() > 0) g.fill(x, y + 1, x + 1, y + HEADER_H - 1, SEG_DIVIDER);
            g.drawString(font, label, x + PAD, y + (HEADER_H - 8) / 2, chosen ? 0xFFFFFFFF : hovered ? 0xFFDDDDDD : 0xFFAAAAAA, false);
            hits.add(new Hit(x, y, x + w, y + HEADER_H, () -> {
                state.groupBy = by;
                resultScroll.offset = 0;
                refresh();
            }));
            x += w;
        }
    }

    // ---- rail ----

    private void drawRail(GuiGraphics g, int mouseX, int mouseY) {
        int x = railX + PAD;
        int w = railW - PAD * 2;
        int clipTop = top + 1;
        int clipBottom = bottom - 1;
        g.enableScissor(railX + 1, clipTop, railX + railW - 1, clipBottom);
        int y = top + PAD - (int) railScroll.offset;
        boolean anyShortcut = false;
        for (Map.Entry<Shortcut, Integer> shortcut : view.shortcuts().entrySet()) {
            if (shortcut.getValue() <= 0) continue;
            anyShortcut = true;
            Shortcut s = shortcut.getKey();
            boolean on = state.shortcut == s;
            railRow(g, x, y, w, mouseX, mouseY, on, Component.translatable(
                    "townstead.root.browse.shortcut." + s.name().toLowerCase(Locale.ROOT)), shortcut.getValue(), () -> {
                state.shortcut = on ? Shortcut.ALL : s;
                resultScroll.offset = 0;
                refresh();
            }, clipTop, clipBottom);
            shortcutIcon(g, s, x + 1, y + 1);
            y += ROW;
        }
        if (anyShortcut) y += SECTION_GAP;
        for (Map.Entry<Facet, List<Value>> facet : view.facets().entrySet()) {
            Facet f = facet.getKey();
            y = heading(g, Component.translatable("townstead.root.browse.facet." + f.name().toLowerCase(Locale.ROOT)),
                    null, x, y, w);
            List<Value> values = facet.getValue();
            boolean open = openFacets.contains(f) || values.size() <= FACET_VALUES + 1;
            int shown = open ? values.size() : FACET_VALUES;
            for (int i = 0; i < shown; i++) {
                Value v = values.get(i);
                railRow(g, x, y, w, mouseX, mouseY, v.chosen(), Component.literal(v.label()), v.count(), () -> {
                    state.toggle(f, v.key());
                    resultScroll.offset = 0;
                    refresh();
                }, clipTop, clipBottom);
                QuestIcons.draw(g, v.chosen() ? QuestIcons.Icon.DONE : QuestIcons.Icon.OPEN, x + 1, y + 1);
                y += ROW;
            }
            if (values.size() > FACET_VALUES + 1) {
                Component more = open ? Component.translatable("townstead.root.browse.show_fewer")
                        : Component.translatable("townstead.root.browse.show_all", values.size());
                boolean hovered = within(mouseX, mouseY, x, y, w, ROW, clipTop, clipBottom);
                g.drawString(font, more, x + 13, y + 2, hovered ? 0xFFFFFFFF : MUTED, false);
                addHit(x, y, w, ROW, clipTop, clipBottom, () -> {
                    if (!openFacets.remove(f)) openFacets.add(f);
                });
                y += ROW;
            }
            y += SECTION_GAP;
        }
        g.disableScissor();
        railScroll.settle(y - SECTION_GAP + PAD, bottom);
        scrollbar(g, railX + railW - 3, railScroll);
    }

    /** One clickable rail line: a 9px icon slot, the label, its count on the right. */
    private void railRow(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY, boolean chosen,
                         Component label, int count, Runnable action, int clipTop, int clipBottom) {
        boolean hovered = within(mouseX, mouseY, x, y, w, ROW, clipTop, clipBottom);
        if (chosen) g.fill(x, y, x + w, y + ROW, CHOSEN_FILL);
        else if (hovered) g.fill(x, y, x + w, y + ROW, ROW_HOVER);
        String countText = Integer.toString(count);
        int countW = font.width(countText);
        g.drawString(font, RootDetails.trim(font, label.getString(), w - 13 - countW - PAD), x + 13, y + 2,
                chosen || hovered ? 0xFFFFFFFF : TEXT, false);
        g.drawString(font, countText, x + w - countW - 2, y + 2, chosen ? 0xFFB8D8A0 : COUNT, false);
        addHit(x, y, w, ROW, clipTop, clipBottom, action);
    }

    /** A gold heading with an optional count, ruled underneath. Returns where the rows start. */
    private int heading(GuiGraphics g, Component text, @Nullable String count, int x, int y, int w) {
        g.drawString(font, text, x, y + 2, RootDetails.GOLD, false);
        if (count != null) g.drawString(font, count, x + font.width(text) + PAD, y + 2, COUNT, false);
        g.fill(x, y + ROW, x + w, y + ROW + 1, RULE);
        return y + ROW + 3;
    }

    private void shortcutIcon(GuiGraphics g, Shortcut shortcut, int x, int y) {
        switch (shortcut) {
            case RECENT -> QuestIcons.draw(g, QuestIcons.Icon.WAITING, x, y);
            case PINNED -> QuestIcons.draw(g, QuestIcons.Icon.PINNED, x, y);
            case IN_WORLD -> g.blit(HANGOUT, x, y, 9, 9, 0f, 0f, 16, 16, 16, 16);
            case FEATURED -> g.blit(LETTER, x, y, 9, 9, 0f, 0f, 16, 16, 16, 16);
            default -> {}
        }
    }

    // ---- results ----

    private void drawResults(GuiGraphics g, int mouseX, int mouseY) {
        int x = centerX + PAD;
        int w = centerW - PAD * 2;
        int clipTop = top + 1;
        int clipBottom = bottom - 1;
        g.enableScissor(centerX + 1, clipTop, centerX + centerW - 1, clipBottom);
        int y = top + PAD - (int) resultScroll.offset;
        y = drawChips(g, x, y, w, mouseX, mouseY, clipTop, clipBottom);
        if (view.sections().isEmpty()) {
            Component empty = Component.translatable("townstead.root.browse.empty");
            g.drawString(font, empty, x + (w - font.width(empty)) / 2, y + ROW, MUTED, false);
        }
        int columns = columns();
        int tileW = (w - (columns - 1) * TILE_GAP) / columns;
        Set<String> pinned = RootBrowsePrefs.pinned();
        for (Section section : view.sections()) {
            y = heading(g, Component.literal(section.title()), Integer.toString(section.items().size()), x, y, w);
            int column = 0;
            for (Item item : section.items()) {
                int tx = x + column * (tileW + TILE_GAP);
                drawTile(g, item, tx, y, tileW, mouseX, mouseY, pinned.contains(item.id()), clipTop, clipBottom);
                if (++column == columns) {
                    column = 0;
                    y += TILE + TILE_GAP;
                }
            }
            if (column != 0) y += TILE + TILE_GAP;
            y += SECTION_GAP;
        }
        g.disableScissor();
        resultScroll.settle(y - SECTION_GAP + PAD, bottom);
        scrollbar(g, centerX + centerW - 3, resultScroll);
    }

    /** Active filters as removable green chips, and a way to clear them all. */
    private int drawChips(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY, int clipTop, int clipBottom) {
        List<Component> labels = new ArrayList<>();
        List<Runnable> removers = new ArrayList<>();
        if (state.shortcut != Shortcut.ALL) {
            labels.add(Component.translatable("townstead.root.browse.shortcut." + state.shortcut.name().toLowerCase(Locale.ROOT)));
            removers.add(() -> state.shortcut = Shortcut.ALL);
        }
        for (Map.Entry<Facet, List<Value>> facet : view.facets().entrySet()) {
            for (Value v : facet.getValue()) {
                if (!v.chosen()) continue;
                labels.add(Component.literal(v.label()));
                removers.add(() -> state.toggle(facet.getKey(), v.key()));
            }
        }
        if (labels.isEmpty()) return y;
        int chipH = ROW + 2;
        int cx = x;
        for (int i = 0; i < labels.size(); i++) {
            Component label = labels.get(i);
            int cw = font.width(label) + PAD + 9 + PAD + 2;
            if (cx + cw > x + w && cx > x) {
                cx = x;
                y += chipH + TILE_GAP;
            }
            g.fill(cx, y, cx + cw, y + chipH, 0xFF000000);
            g.fill(cx + 1, y + 1, cx + cw - 1, y + chipH - 1, SEG_ACTIVE);
            g.fill(cx + 1, y + 1, cx + cw - 1, y + 2, SEG_ACTIVE_EDGE);
            g.drawString(font, label, cx + PAD, y + 3, 0xFFFFFFFF, false);
            QuestIcons.draw(g, QuestIcons.Icon.FAILED, cx + cw - PAD - 9, y + 3);
            Runnable remove = removers.get(i);
            addHit(cx, y, cw, chipH, clipTop, clipBottom, () -> {
                remove.run();
                resultScroll.offset = 0;
                refresh();
            });
            cx += cw + PAD;
        }
        Component clear = Component.translatable("townstead.root.browse.clear");
        int clearW = font.width(clear) + 4;
        if (cx + clearW > x + w) {
            cx = x;
            y += chipH + TILE_GAP;
        }
        boolean hovered = within(mouseX, mouseY, cx, y, clearW, chipH, clipTop, clipBottom);
        g.drawString(font, clear, cx + 2, y + 3, hovered ? 0xFFFFFFFF : MUTED, false);
        addHit(cx, y, clearW, chipH, clipTop, clipBottom, () -> {
            state.chosen.clear();
            state.shortcut = Shortcut.ALL;
            resultScroll.offset = 0;
            refresh();
        });
        return y + chipH + SECTION_GAP;
    }

    private void drawTile(GuiGraphics g, Item item, int x, int y, int w, int mouseX, int mouseY, boolean pinned,
                          int clipTop, int clipBottom) {
        boolean isSelected = selected != null && selected.id().equals(item.id());
        boolean hovered = within(mouseX, mouseY, x, y, w, TILE, clipTop, clipBottom);
        boolean current = item.id().equals(currentId);
        g.fill(x, y, x + w, y + TILE, isSelected ? TILE_SELECTED_EDGE : TILE_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + TILE - 1,
                current ? TILE_CURRENT : isSelected ? TILE_SELECTED : hovered ? TILE_HOVER : TILE_FILL);
        int textY = y + (TILE - 8) / 2;
        int textX = x + 4;
        RootBrowseEntry browse = RootCatalogClient.browse(item.id());
        if (browse != null && !browse.icon().isEmpty() && IconArt.draw(g, browse.icon(), x + 2, y + 2, 0.75f)) {
            textX = x + 2 + 12 + 4;
        }
        int right = x + w - 4;
        String qualifier = manyPacks && browse != null ? RootDetails.text(browse.packQualifier()).getString() : "";
        if (!qualifier.isEmpty() && font.width(qualifier) < w / 3) {
            right -= font.width(qualifier);
            g.drawString(font, qualifier, right, textY, isSelected ? 0xFF9A9A9A : 0xFF5E5E5E, false);
            right -= 4;
        }
        if (pinned) {
            right -= 9;
            QuestIcons.draw(g, QuestIcons.Icon.PINNED, right, y + (TILE - 9) / 2);
            right -= 4;
        }
        g.drawString(font, RootDetails.trim(font, item.name(), right - textX), textX, textY,
                isSelected || hovered || current ? 0xFFFFFFFF : TEXT, false);
        addHit(x, y, w, TILE, clipTop, clipBottom, () -> select(item.id()));
    }

    // ---- preview ----

    private void drawPreview(GuiGraphics g, int mouseX, int mouseY) {
        int x = previewX + PAD;
        int w = previewW - PAD * 2;
        int y = top + PAD;
        int modelH = Math.max(60, Math.min(120, (bottom - top) * 2 / 5));
        g.fill(x, y, x + w, y + modelH, RootDetails.WELL);
        if (model != null) drawModel(g, x, y, x + w, y + modelH, mouseX, mouseY);
        y += modelH + PAD;
        int clipBottom = buttonsY - PAD;
        if (selected == null) {
            Component pick = Component.translatable("townstead.root.browse.pick");
            g.drawString(font, pick, x + (w - font.width(pick)) / 2, y + PAD, MUTED, false);
            return;
        }
        int clipTop = y;
        g.enableScissor(previewX + 1, clipTop, previewX + previewW - 1, clipBottom);
        int cy = y - (int) detailScroll.offset;
        RootBrowseEntry browse = RootCatalogClient.browse(selected.id());
        cy = RootDetails.header(g, selected, browse, x, cy, w);
        cy = RootDetails.backstory(g, selected, x, cy + PAD, w, Integer.MAX_VALUE);
        cy = RootDetails.facts(g, browse, x, cy + PAD, w);
        boolean inside = mouseY >= clipTop && mouseY < clipBottom;
        cy = RootDetails.powers(g, browse, x, cy + PAD, w, inside ? mouseX : -1, mouseY, hover);
        g.disableScissor();
        detailScroll.settle(cy, clipBottom);
        if (detailScroll.max > 0) {
            int track = clipBottom - clipTop;
            int thumb = Math.max(12, track * track / (track + detailScroll.max));
            int thumbY = clipTop + (int) ((track - thumb) * detailScroll.offset / detailScroll.max);
            g.fill(previewX + previewW - 3, clipTop, previewX + previewW - 1, clipBottom, 0x50000000);
            g.fill(previewX + previewW - 3, thumbY, previewX + previewW - 1, thumbY + thumb, 0xFFAAAAAA);
        }
    }

    private void drawModel(GuiGraphics g, int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
        LivingEntity entity = model;
        if (entity == null) return;
        int scale = Math.max(12, Math.round((y1 - y0) * 0.42f / Math.max(0.6f, entity.getBbHeight())));
        try {
            //? if >=1.21 {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, scale, 0.0625f, mouseX, mouseY, entity);
            //?} else {
            /*g.enableScissor(x0, y0, x1, y1);
            int cx = (x0 + x1) / 2;
            int feet = y1 - 6;
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feet, scale, cx - mouseX, (y0 + y1) / 2f - mouseY, entity);
            g.disableScissor();
            *///?}
        } catch (RuntimeException ignored) {
            // A model that cannot draw in a GUI leaves the box empty rather than closing the screen.
        }
    }

    // ---- helpers ----

    private void scrollbar(GuiGraphics g, int x, Scroll scroll) {
        if (scroll.max <= 0) return;
        int trackTop = top + PAD;
        int track = bottom - PAD - trackTop;
        int thumb = Math.max(12, track * track / (track + scroll.max));
        int thumbY = trackTop + (int) ((track - thumb) * scroll.offset / scroll.max);
        g.fill(x, trackTop, x + 2, trackTop + track, 0x50000000);
        g.fill(x, thumbY, x + 2, thumbY + thumb, 0xFFAAAAAA);
    }

    private static boolean within(int mouseX, int mouseY, int x, int y, int w, int h, int clipTop, int clipBottom) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h && mouseY >= clipTop && mouseY < clipBottom;
    }

    private void addHit(int x, int y, int w, int h, int clipTop, int clipBottom, Runnable action) {
        if (y + h <= clipTop || y >= clipBottom) return;
        hits.add(new Hit(x, Math.max(y, clipTop), x + w, Math.min(y + h, clipBottom), action));
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        for (Hit hit : List.copyOf(hits)) {
            if (hit.contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scroll(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    private boolean scroll(double mouseX, double mouseY, double delta) {
        if (mouseY < top || mouseY >= bottom) return false;
        if (mouseX >= railX && mouseX < railX + railW) {
            railScroll.by(delta, ROW);
            return true;
        }
        if (mouseX >= centerX && mouseX < centerX + centerW) {
            resultScroll.by(delta, TILE + TILE_GAP);
            return true;
        }
        if (mouseX >= previewX && mouseX < previewX + previewW) {
            detailScroll.by(delta, 10);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && selected != null
                && (search == null || !search.isFocused())) {
            chooseSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
