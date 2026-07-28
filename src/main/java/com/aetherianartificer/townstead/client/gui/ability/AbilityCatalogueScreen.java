package com.aetherianartificer.townstead.client.gui.ability;

import com.aetherianartificer.townstead.client.gui.common.FrameRenderer;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.client.gui.common.ParchmentButton;
import com.aetherianartificer.townstead.client.root.ClientAbilityLoadout;
import com.aetherianartificer.townstead.root.ability.AbilityLoadoutC2SPayload;
import com.aetherianartificer.townstead.root.ability.AbilityLoadoutS2CPayload;
import com.aetherianartificer.townstead.root.ability.AbilityNames;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Arranging your slots: every slot at once, and everything you own to put in them.
 *
 * <p>It began as a picker for ONE slot, reachable only by right-clicking a wedge. Filling
 * twenty-four slots that way is twenty-four round trips through a held-key gesture, which is the
 * wrong shape for something you do sitting down. The slot strip makes it one sitting, and doubles as
 * the overview of your arrangement that existed nowhere else.</p>
 *
 * <p>Shaped like the creative inventory: tabs, search, a grid of the board's frames. It is the one
 * layout every Minecraft player knows without being taught, and nothing here gains by being novel.
 * SOURCES ARE JUST STRINGS, so a tab appears because a provider reported one.</p>
 */
public final class AbilityCatalogueScreen extends Screen {

    private static final int PANEL_W = 292;
    private static final int FRAME_THICK = 6;
    /** Mirrors {@code ActiveAbilities.POOL_SIZE} and its layer size. */
    private static final int SLOT_COUNT = 24;
    private static final int SLOTS_PER_ROW = 8;

    /** The arrangement is drawn as the DIAL it edits, one per modifier layer. */
    private static final int DIAL_R = 32;
    private static final int DIAL_SLOT_R = 21;
    private static final int DIAL_CELL = 14;
    private static final int DIALS_H = 96;
    private static final int TAB_H = 15;
    private static final int SEARCH_H = 13;
    /** Tall enough for the name AND the chip row under it, which 30 was not. */
    private static final int DETAIL_H = 32;
    /** Every gap between stacked zones. One number, so nothing drifts against anything else. */
    private static final int GAP = 5;
    private static final int BUTTON_H = 18;
    private static final int CELL = 20;
    private static final int PITCH = 23;

    private int slot;
    private final Screen parent;

    private final List<String> sources = new ArrayList<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private List<AbilityLoadoutS2CPayload.Option> shown = List.of();
    private String source = "";
    private String selectedId = "";
    private EditBox search;
    private double scroll;
    private int tabScroll;
    /** What is in hand, where it came from (0 = the catalogue), and whether it has actually moved. */
    private ResourceLocation dragId;
    private String dragIcon = "";
    private String dragName = "";
    private int dragFrom;
    private boolean dragMoved;
    private int dragX;
    private int dragY;

    private int left;
    private int top;
    private int panelH;
    private int stripTop;
    private int tabsTop;
    private int searchTop;
    private int gridTop;
    private int gridBottom;
    private int detailTop;
    private int buttonY;
    private int columns = 1;

    public AbilityCatalogueScreen(int slot, Screen parent) {
        super(Component.translatable("townstead.ability.picker.title", slot));
        this.slot = slot;
        this.parent = parent;
    }

    @Override
    protected void init() {
        // FIT FIRST, then spend what is left on the grid. And count EVERY gap: the chrome sum used
        // to leave 50 pixels for a 32-pixel band, an 18-pixel button and the space between them,
        // so the button's top landed above the chip row and the two overlapped.
        int chrome = GAP + DIALS_H + GAP + TAB_H + 3 + SEARCH_H + GAP
                + GAP + DETAIL_H + GAP + BUTTON_H + GAP;
        int available = height - 2 * FRAME_THICK - 8;
        int gridH = Math.max(0, Math.min(available - chrome, 184));
        panelH = Math.min(chrome + gridH, available);
        gridH = Math.max(0, panelH - chrome);

        left = (width - PANEL_W) / 2;
        top = Math.max(FRAME_THICK + 2, (height - panelH) / 2);

        // Stacked top-down from one cursor, so a change to any zone cannot silently eat another.
        stripTop = top + GAP;
        tabsTop = stripTop + DIALS_H + GAP;
        searchTop = tabsTop + TAB_H + 3;
        gridTop = searchTop + SEARCH_H + GAP;
        gridBottom = gridTop + gridH;
        detailTop = gridBottom + GAP;
        buttonY = detailTop + DETAIL_H + GAP;
        columns = Math.max(1, (PANEL_W - 20) / PITCH);

        sources.clear();
        counts.clear();
        for (AbilityLoadoutS2CPayload.Option option : ClientAbilityLoadout.available()) {
            counts.merge(option.source(), 1, Integer::sum);
        }
        sources.addAll(counts.keySet());

        search = new EditBox(font, left + 11, searchTop + 3, PANEL_W - 90, SEARCH_H - 5,
                Component.translatable("townstead.ability.picker.search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(0xFFF0DDB0);
        search.setResponder(text -> {
            scroll = 0;
            refresh();
        });
        addRenderableWidget(search);
        // DELIBERATELY NOT FOCUSED. This screen opens from the wheel, which opens by HOLDING a key,
        // so focusing the field meant that held key auto-repeated into it and you arrived to a box
        // full of "rrrrrrrr". Click it, or press Tab.

        // ONLY Done. Assign and Leave empty are what dragging replaced: with a drag there is no
        // longer a state where you have chosen a thing but not yet placed it, and clearing is
        // dragging a wedge off the dial rather than a button that acts on a slot you must first
        // have selected somewhere else.
        int buttonW = 80;
        addRenderableWidget(new ParchmentButton(left + (PANEL_W - buttonW) / 2, buttonY, buttonW,
                BUTTON_H, CommonComponents.GUI_DONE, b -> onClose()));

        refresh();
    }

    /**
     * Applies the source filter and the query, then puts what you already own at the front.
     *
     * <p>"What have I already got" is the question asked before "what else is there", and in
     * registry order the answer is scattered through two hundred cells.</p>
     */
    private void refresh() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        Map<Integer, ResourceLocation> arrangement = ClientAbilityLoadout.arrangement();
        List<AbilityLoadoutS2CPayload.Option> out = new ArrayList<>();
        for (AbilityLoadoutS2CPayload.Option option : ClientAbilityLoadout.available()) {
            if (!source.isEmpty() && !option.source().equals(source)) continue;
            if (!query.isEmpty() && !option.name().toLowerCase(Locale.ROOT).contains(query)) continue;
            out.add(option);
        }
        out.sort(Comparator.comparingInt(
                (AbilityLoadoutS2CPayload.Option o) -> assignedTo(arrangement, o) > 0 ? 0 : 1));
        shown = List.copyOf(out);
    }

    /** Which slot holds this, or 0. */
    private static int assignedTo(Map<Integer, ResourceLocation> arrangement,
                                  AbilityLoadoutS2CPayload.Option option) {
        for (Map.Entry<Integer, ResourceLocation> entry : arrangement.entrySet()) {
            if (entry.getValue().toString().equals(option.id())) return entry.getKey();
        }
        return 0;
    }

    private AbilityLoadoutS2CPayload.Option selected() {
        for (AbilityLoadoutS2CPayload.Option option : shown) {
            if (option.id().equals(selectedId)) return option;
        }
        return null;
    }

    /** The panel is the background; the vanilla one would re-blur over it inside super.render. */
    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawPanel(g);
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics g) {
        drawPanel(g);
    }
    *///?}

    private void drawPanel(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x99070402);
        FrameRenderer.drawWoodenFrame(g, left, top, PANEL_W, panelH, FRAME_THICK);
        g.fill(left, top, left + PANEL_W, top + panelH, Palette.DESK_DEEP);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //? if >=1.21 {
        super.render(g, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        *///?}
        drawDials(g, mouseX, mouseY);
        drawTabs(g, mouseX, mouseY);
        drawSearch(g);
        drawGrid(g, mouseX, mouseY);
        drawDetail(g, mouseX, mouseY);
        if (dragId != null && dragMoved) {
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            drawEntryMark(g, dragX, dragY, dragIcon, dragName, true, 0.85f);
            g.pose().popPose();
        }
    }

    /**
     * The arrangement, drawn as the DIALS it edits: one per modifier layer.
     *
     * <p>It was a 3x8 grid, which meant every placement asked you to translate "slot 11" into
     * "shift, upper right" and then translate back in the fight. Where you see it is now where you
     * aim. It also retires the caption: a grid of bare boxes needed telling you it was your
     * arrangement, and three dials do not.</p>
     */
    private void drawDials(GuiGraphics g, int mouseX, int mouseY) {
        // Named by ORDER, not by keystroke. "Plain / Shift / Ctrl" labelled the input rather than
        // the thing, and "Plain" is not a word for anything. The modifier is still the answer to
        // "how do I reach this one", so it stays as a quiet second line where there is one.
        String[] layers = {
                Component.translatable("townstead.ability.picker.layer_first").getString(),
                Component.translatable("townstead.ability.picker.layer_second").getString(),
                Component.translatable("townstead.ability.picker.layer_third").getString()};
        // Reads the player's ACTUAL binding, so rebinding the modifier rebinds the instruction.
        String[] hints = {"",
                holdHint(com.aetherianartificer.townstead.client.TownsteadKeybinds.LAYER_SECOND),
                holdHint(com.aetherianartificer.townstead.client.TownsteadKeybinds.LAYER_THIRD)};
        for (int layer = 0; layer < 3; layer++) {
            int cx = dialCentreX(layer);
            int cy = stripTop + DIAL_R + 6;
            // The SAME ground tone the wheel gives this layer, so the two read as one instrument
            // seen twice rather than as two unrelated brass circles.
            int face = switch (layer) {
                case 1 -> 0xFF1B1E24;
                case 2 -> 0xFF1C2119;
                default -> 0xFF221A0F;
            };
            WheelArt.disc(g, cx, cy, DIAL_R + 2, Palette.DESK_EDGE);
            WheelArt.disc(g, cx, cy, DIAL_R, face);
            WheelArt.rim(g, cx, cy, DIAL_R, DIAL_R - 2);
            WheelArt.disc(g, cx, cy, 9, 0xFF14100A);

            for (int wedge = 0; wedge < 8; wedge++) {
                int index = layer * 8 + wedge + 1;
                int[] at = wedgeAt(layer, wedge);
                drawSlotCell(g, at[0], at[1], index, mouseX, mouseY);
            }
            boolean here = layer == (slot - 1) / 8;
            String label = layers[layer];
            g.drawString(font, label, cx - font.width(label) / 2, cy + DIAL_R + 3,
                    here ? Palette.BRASS_HOT : 0xFF8A7A5E, false);
            String hint = hints[layer];
            if (!hint.isEmpty()) {
                g.drawString(font, hint, cx - font.width(hint) / 2, cy + DIAL_R + 13,
                        here ? 0xFFB79A6C : 0xFF5A4A32, false);
            }
        }
    }

    /** "hold Left Shift", or a warning when the layer has no key at all. */
    private static String holdHint(net.minecraft.client.KeyMapping mapping) {
        String key = com.aetherianartificer.townstead.client.TownsteadKeybinds.keyName(mapping);
        if (mapping == null || mapping.isUnbound()) {
            return Component.translatable("townstead.ability.picker.hold_unbound").getString();
        }
        return Component.translatable("townstead.ability.picker.hold", key).getString();
    }

    private int dialCentreX(int layer) {
        return left + PANEL_W / 2 - 96 + layer * 96;
    }

    /** Screen position of one wedge. Slot 1 sits at twelve o'clock, as it does on the wheel. */
    private int[] wedgeAt(int layer, int wedge) {
        double angle = -Math.PI / 2 + wedge * (Math.PI / 4);
        return new int[]{
                dialCentreX(layer) + (int) Math.round(Math.cos(angle) * DIAL_SLOT_R),
                stripTop + DIAL_R + 6 + (int) Math.round(Math.sin(angle) * DIAL_SLOT_R)};
    }

    /** The slot under the cursor, or 0. */
    private int slotAt(int mouseX, int mouseY) {
        for (int layer = 0; layer < 3; layer++) {
            for (int wedge = 0; wedge < 8; wedge++) {
                int[] at = wedgeAt(layer, wedge);
                if (Math.abs(mouseX - at[0]) <= DIAL_CELL / 2
                        && Math.abs(mouseY - at[1]) <= DIAL_CELL / 2) {
                    return layer * 8 + wedge + 1;
                }
            }
        }
        return 0;
    }

    private void drawSlotCell(GuiGraphics g, int cx, int cy, int index, int mouseX, int mouseY) {
        AbilityLoadoutS2CPayload.Entry entry = ClientAbilityLoadout.slot(index);
        boolean target = index == slot;
        boolean hover = Math.abs(mouseX - cx) <= DIAL_CELL / 2
                && Math.abs(mouseY - cy) <= DIAL_CELL / 2;
        boolean dropping = dragId != null && dragMoved;

        int rim;
        int inner;
        if (dropping && hover) {
            rim = Palette.BRASS_HOT;
            inner = 0xFF3D2C10;
        } else if (dropping) {
            // Everything that is not the drop target recedes, so one gesture has one answer.
            rim = 0xFF2A2420;
            inner = 0xFF150F08;
        } else if (target || hover) {
            rim = Palette.BRASS_HOT;
            inner = 0xFF2E2317;
        } else {
            rim = entry == null ? 0xFF3A322A : Palette.BRASS_DEEP;
            inner = entry == null ? 0xFF1A1309 : 0xFF2E2317;
        }
        int x = cx - DIAL_CELL / 2;
        int y = cy - DIAL_CELL / 2;
        g.fill(x, y, x + DIAL_CELL, y + DIAL_CELL, Palette.DESK_EDGE);
        g.fill(x + 1, y + 1, x + DIAL_CELL - 1, y + DIAL_CELL - 1, rim);
        g.fill(x + 2, y + 2, x + DIAL_CELL - 2, y + DIAL_CELL - 2, inner);
        if (dropping && hover && entry == null) {
            // A plus, so an empty target still says "drop here".
            g.fill(cx - 2, cy - 1, cx + 3, cy, Palette.BRASS_HOT);
            g.fill(cx, cy - 3, cx + 1, cy + 2, Palette.BRASS_HOT);
            return;
        }
        if (entry == null) return;
        if (dragFrom == index && dragMoved) return;
        drawEntryMark(g, cx, cy, entry.icon(), entry.name(), target, 0.6f);
    }

    /** An icon, or the initials that stand in for one. */
    private void drawEntryMark(GuiGraphics g, int cx, int cy, String icon, String name,
                               boolean lit, float scale) {
        if (com.aetherianartificer.townstead.client.gui.common.IconArt
                .drawCentred(g, icon, cx, cy, scale)) {
            return;
        }
        String mark = AbilityNames.initialsOf(name);
        g.drawString(font, mark, cx - font.width(mark) / 2, cy - 4,
                lit ? Palette.BRASS_HOT : 0xFF9A8A70, false);
    }

    /** Sources across the top, sized to their own text, with arrows when they run out of room. */
    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(left + 6, tabsTop + TAB_H, left + PANEL_W - 6, tabsTop + TAB_H + 1, Palette.DESK_LIP);
        int x = left + 6;
        int limit = left + PANEL_W - 24;
        int index = 0;
        for (int i = tabScroll; i <= sources.size(); i++) {
            String name = i == 0
                    ? Component.translatable("townstead.ability.picker.all").getString()
                    : sources.get(i - 1);
            int tally = i == 0 ? ClientAbilityLoadout.available().size()
                    : counts.getOrDefault(name, 0);
            String label = RecordTrim.fit(font, name, 90);
            int w = font.width(label) + font.width(String.valueOf(tally)) + 14;
            if (x + w > limit) break;
            boolean active = i == 0 ? source.isEmpty() : name.equals(source);
            boolean hover = mouseX >= x && mouseX < x + w
                    && mouseY >= tabsTop && mouseY < tabsTop + TAB_H;
            g.fill(x, tabsTop, x + w, tabsTop + TAB_H, active ? 0xFF3A2C14 : 0xFF1C1409);
            g.fill(x, tabsTop, x + w, tabsTop + 1, active ? Palette.BRASS : 0xFF3A2E1E);
            if (active) g.fill(x, tabsTop + TAB_H, x + w, tabsTop + TAB_H + 1, 0xFF3A2C14);
            g.drawString(font, label, x + 5, tabsTop + 4,
                    active ? Palette.BRASS_HOT : (hover ? 0xFFC0AC85 : 0xFF8A7A5E), false);
            g.drawString(font, String.valueOf(tally), x + 10 + font.width(label), tabsTop + 4,
                    active ? 0xFFB79A6C : 0xFF4A4034, false);
            x += w + 2;
            index++;
        }
        boolean more = tabScroll + index <= sources.size();
        drawArrow(g, left + PANEL_W - 20, tabsTop, false, tabScroll > 0);
        drawArrow(g, left + PANEL_W - 12, tabsTop, true, more);
    }

    /** A triangle: widest at its base, one pixel at its point. Both arrows pointed left before. */
    private void drawArrow(GuiGraphics g, int x, int y, boolean right, boolean live) {
        g.fill(x, y, x + 7, y + TAB_H, 0xFF1C1409);
        int color = live ? Palette.BRASS_HOT : 0xFF4A4034;
        for (int step = 0; step < 3; step++) {
            int px = right ? x + 2 + step : x + 4 - step;
            int tall = 5 - 2 * step;
            int py = y + TAB_H / 2 - tall / 2;
            g.fill(px, py, px + 1, py + tall, color);
        }
    }

    /** One field, searching every source at once: narrowing is what the tabs are for. */
    private void drawSearch(GuiGraphics g) {
        g.fill(left + 6, searchTop, left + PANEL_W - 6, searchTop + SEARCH_H, 0xFF171009);
        g.fill(left + 6, searchTop, left + PANEL_W - 6, searchTop + 1, 0xFF100B06);
        // NO LENS. A four-pixel magnifier reads as a smudge on the glass; the placeholder says it.
        if (search != null && search.getValue().isEmpty() && !search.isFocused()) {
            g.drawString(font, Component.translatable("townstead.ability.picker.search"),
                    left + 11, searchTop + 3, 0xFF5A4A32, false);
        }
        String tally = Component.translatable("townstead.ability.picker.tally",
                shown.size(), ClientAbilityLoadout.available().size()).getString();
        g.drawString(font, tally, left + PANEL_W - 11 - font.width(tally), searchTop + 3,
                0xFF6E5A38, false);
    }

    /** The grid, as a recessed well so a short list reads as a short list rather than a fault. */
    private void drawGrid(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(left + 6, gridTop, left + PANEL_W - 6, gridBottom, 0xFF1A1309);
        g.fill(left + 6, gridTop, left + PANEL_W - 6, gridTop + 1, 0xFF0F0B06);
        Map<Integer, ResourceLocation> arrangement = ClientAbilityLoadout.arrangement();

        g.enableScissor(left + 6, gridTop, left + PANEL_W - 6, gridBottom);
        for (int i = 0; i < shown.size(); i++) {
            int cx = cellX(i);
            int cy = cellY(i);
            if (cy + CELL < gridTop || cy - CELL > gridBottom) continue;
            AbilityLoadoutS2CPayload.Option option = shown.get(i);
            drawCell(g, cx, cy, option, option.id().equals(selectedId),
                    overCell(mouseX, mouseY, cx, cy), assignedTo(arrangement, option));
        }
        g.disableScissor();

        // The track is ALWAYS reserved, so three entries and three hundred lay out identically and
        // the grid never shifts sideways the moment a scrollbar appears.
        int trackX = left + PANEL_W - 10;
        g.fill(trackX, gridTop + 2, trackX + 2, gridBottom - 2, 0xFF241A0E);
        int content = rows() * PITCH + 6;
        int view = gridBottom - gridTop;
        if (content > view) {
            int thumb = Math.max(12, (view - 4) * view / content);
            int thumbY = gridTop + 2 + (int) ((view - 4 - thumb) * (scroll / (content - view)));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumb, 0xFF705A38);
        }
        if (shown.isEmpty()) {
            String empty = Component.translatable(ClientAbilityLoadout.available().isEmpty()
                    ? "townstead.ability.picker.empty"
                    : "townstead.ability.picker.no_match").getString();
            g.drawString(font, RecordTrim.fit(font, empty, PANEL_W - 28), left + 12, gridTop + 8,
                    0xFF8A7654, false);
        }
    }

    private int cellX(int index) {
        return left + 12 + (index % columns) * PITCH + CELL / 2;
    }

    private int cellY(int index) {
        return gridTop + 6 + (index / columns) * PITCH + CELL / 2 - (int) scroll;
    }

    private boolean overCell(int mouseX, int mouseY, int cx, int cy) {
        return Math.abs(mouseX - cx) <= CELL / 2 && Math.abs(mouseY - cy) <= CELL / 2
                && mouseY >= gridTop && mouseY < gridBottom;
    }

    private void drawCell(GuiGraphics g, int cx, int cy, AbilityLoadoutS2CPayload.Option option,
                          boolean chosen, boolean hover, int assigned) {
        int x = cx - CELL / 2;
        int y = cy - CELL / 2;
        int rim = chosen ? Palette.BRASS : (hover ? Palette.BRASS_HOT : 0xFF3A322A);
        g.fill(x + 1, y + 2, x + CELL + 1, y + CELL + 2, 0x66000000);
        g.fill(x, y, x + CELL, y + CELL, Palette.DESK_EDGE);
        g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, rim);
        g.fill(x + 2, y + 2, x + CELL - 2, y + CELL - 2, chosen ? 0xFF33260F : 0xFF241A0E);
        g.fill(x + 1, y + 1, x + CELL - 1, y + 2, chosen ? Palette.BRASS_HOT : 0xFF4A4034);

        if (!com.aetherianartificer.townstead.client.gui.common.IconArt
                .drawCentred(g, option.icon(), cx, cy, 0.85f)) {
            String mark = AbilityNames.initialsOf(option.name());
            g.drawString(font, mark, cx - font.width(mark) / 2, cy - 4,
                    chosen ? Palette.BRASS_HOT : 0xFFC0A46E, false);
        }
        // Already somewhere: a corner pip, so you can see at a glance what is spoken for.
        if (assigned > 0) {
            g.flush();
            g.fill(x + CELL - 5, y + 2, x + CELL - 2, y + 5, Palette.BRASS_HOT);
        }
    }

    /** What it is and what it costs. Falls back to what the slot already holds, never dead space. */
    private void drawDetail(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(left + 6, detailTop - GAP + 1, left + PANEL_W - 6, detailTop - GAP + 2,
                Palette.DESK_LIP);
        AbilityLoadoutS2CPayload.Option option = hovered(mouseX, mouseY);
        if (option == null) option = selected();

        String name;
        String icon;
        String entrySource = "";
        String kindWord;
        int kindGlyph;
        int cooldown = 0;
        String costText = "";
        String whereText = "";
        Map<Integer, ResourceLocation> arrangement = ClientAbilityLoadout.arrangement();

        if (option != null) {
            name = option.name();
            icon = option.icon();
            kindWord = Component.translatable(option.toggle()
                    ? "townstead.ability.wheel.kind.toggle_off"
                    : "townstead.ability.wheel.kind.cast").getString();
            kindGlyph = option.toggle() ? 1 : 2;
            cooldown = option.cooldownTicks();
            if (option.costAmount() > 0 && !option.costLabel().isEmpty()) {
                costText = option.costAmount() + " " + option.costLabel();
            }
            int at = assignedTo(arrangement, option);
            whereText = at > 0
                    ? Component.translatable("townstead.ability.picker.in_slot", at).getString()
                    : "";
        } else {
            AbilityLoadoutS2CPayload.Entry entry = ClientAbilityLoadout.slot(slot);
            if (entry == null) {
                g.drawString(font, Component.translatable("townstead.ability.picker.slot_empty",
                                slot), left + 12, detailTop + 11, 0xFF6E5A38, false);
                return;
            }
            name = entry.name();
            icon = entry.icon();
            entrySource = "";
            kindWord = Component.translatable(entry.toggle()
                    ? "townstead.ability.wheel.kind.toggle_off"
                    : "townstead.ability.wheel.kind.cast").getString();
            kindGlyph = entry.toggle() ? 1 : 2;
            cooldown = entry.cooldownTicks();
            if (entry.costAmount() > 0 && !entry.costLabel().isEmpty()) {
                costText = entry.costAmount() + " " + entry.costLabel();
            }
            whereText = Component.translatable("townstead.ability.picker.in_slot", slot).getString();
        }

        int cx = left + 18;
        int cy = detailTop + 15;
        g.fill(cx - 10, cy - 10, cx + 10, cy + 10, Palette.DESK_EDGE);
        g.fill(cx - 9, cy - 9, cx + 9, cy + 9, Palette.BRASS);
        g.fill(cx - 8, cy - 8, cx + 8, cy + 8, 0xFF33260F);
        g.fill(cx - 9, cy - 9, cx + 9, cy - 8, Palette.BRASS_HOT);
        drawEntryMark(g, cx, cy, icon, name, true, 0.85f);

        g.drawString(font, RecordTrim.fit(font, name, PANEL_W - 50), left + 34, detailTop + 6,
                Palette.BRASS_HOT, false);
        // Facts as CHIPS, not a run of dot-separated words. Each one is a different kind of thing
        // and a reader is usually after exactly one of them; a sentence makes you parse all four.
        int chipX = left + 34;
        int chipY = detailTop + 17;
        chipX = chip(g, chipX, chipY, source(option, entrySource), 0, 0xFF8A7A5E);
        chipX = chip(g, chipX, chipY, kindWord, kindGlyph, Palette.BRASS_DEEP);
        if (cooldown > 0) {
            chipX = chip(g, chipX, chipY, Component.translatable(
                    "townstead.ability.wheel.cooldown_s", cooldown / 20).getString(),
                    3, 0xFF8A7A5E);
        }
        if (!costText.isEmpty()) {
            chipX = chip(g, chipX, chipY, costText, 4, 0xFF8A7A5E);
        }
        if (!whereText.isEmpty()) {
            chip(g, chipX, chipY, whereText, 0, Palette.BRASS);
        }
    }

    /**
     * One labelled fact, drawn as a small plate.
     *
     * @param glyph 0 none, 1 switch, 2 spark, 3 cooldown, 4 cost
     * @return where the next chip starts
     */
    private int chip(GuiGraphics g, int x, int y, String label, int glyph, int tone) {
        if (label.isEmpty()) return x;
        int glyphW = glyph == 0 ? 0 : 10;
        int w = glyphW + font.width(label) + 8;
        if (x + w > left + PANEL_W - 8) return x;
        g.fill(x, y - 1, x + w, y + 10, 0xFF1E1509);
        g.fill(x, y - 1, x + w, y, 0xFF2E2317);
        switch (glyph) {
            case 1 -> WheelArt.switchMark(g, x + 3, y + 2, false);
            case 2 -> WheelArt.sparkMark(g, x + 3, y + 2, tone);
            case 3 -> {
                // A dial face: the shape a cooldown already has on the wheel's ring.
                WheelArt.disc(g, x + 6, y + 5, 4, tone);
                WheelArt.disc(g, x + 6, y + 5, 3, 0xFF1E1509);
                g.fill(x + 6, y + 2, x + 7, y + 6, tone);
            }
            case 4 -> {
                WheelArt.disc(g, x + 6, y + 5, 3, tone);
                WheelArt.disc(g, x + 6, y + 5, 2, 0xFF1E1509);
            }
            default -> { }
        }
        g.drawString(font, label, x + 4 + glyphW, y + 1, tone, false);
        return x + w + 3;
    }

    private static String source(AbilityLoadoutS2CPayload.Option option, String fallback) {
        return option != null ? option.source() : fallback;
    }

    private int rows() {
        return (shown.size() + columns - 1) / columns;
    }

    private AbilityLoadoutS2CPayload.Option hovered(int mouseX, int mouseY) {
        for (int i = 0; i < shown.size(); i++) {
            if (overCell(mouseX, mouseY, cellX(i), cellY(i))) return shown.get(i);
        }
        return null;
    }

    /**
     * Puts {@code id} in {@code target}, SWAPPING when it came from another slot.
     *
     * <p>Swapping is the commonest edit once an arrangement exists, and it is the one thing the
     * Assign button could never express: with a button it was three operations and a spare slot.</p>
     */
    private void place(int target, ResourceLocation id, int from) {
        Map<Integer, ResourceLocation> arrangement = ClientAbilityLoadout.arrangement();
        ResourceLocation displaced = arrangement.get(target);
        if (from > 0) {
            arrangement.remove(from);
            if (displaced != null) arrangement.put(from, displaced);
        } else {
            // From the catalogue: an ability lives in ONE slot, so take it out of wherever it was.
            arrangement.values().removeIf(id::equals);
        }
        arrangement.put(target, id);
        send(arrangement);
        slot = target;
    }

    private void clearSlot(int target) {
        Map<Integer, ResourceLocation> arrangement = ClientAbilityLoadout.arrangement();
        if (arrangement.remove(target) != null) send(arrangement);
    }

    /** Click-click: with something chosen in the catalogue, a click on a wedge places it there. */
    private void assignTo(int target) {
        AbilityLoadoutS2CPayload.Option option = selected();
        if (option == null) {
            slot = target;
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(option.id());
        if (id == null) return;
        place(target, id, 0);
        selectedId = "";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        // A press on something draggable arms a drag; whether it BECOMES one is decided on move.
        int hitSlot = slotAt((int) mouseX, (int) mouseY);
        if (hitSlot > 0) {
            AbilityLoadoutS2CPayload.Entry entry = ClientAbilityLoadout.slot(hitSlot);
            if (entry != null) {
                dragId = ResourceLocation.tryParse(entry.id());
                dragIcon = entry.icon();
                dragName = entry.name();
                dragFrom = hitSlot;
                dragMoved = false;
                dragX = (int) mouseX;
                dragY = (int) mouseY;
            }
            return true;
        }
        AbilityLoadoutS2CPayload.Option option = hovered((int) mouseX, (int) mouseY);
        if (option != null) {
            dragId = ResourceLocation.tryParse(option.id());
            dragIcon = option.icon();
            dragName = option.name();
            dragFrom = 0;
            dragMoved = false;
            dragX = (int) mouseX;
            dragY = (int) mouseY;
            return true;
        }

        if (mouseY >= tabsTop && mouseY < tabsTop + TAB_H) return clickTabs(mouseX);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragId != null && button == 0) {
            dragX = (int) mouseX;
            dragY = (int) mouseY;
            // A few pixels of slack, so a click with a shaky hand stays a click.
            if (Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5) dragMoved = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragId == null || button != 0) return super.mouseReleased(mouseX, mouseY, button);
        ResourceLocation id = dragId;
        int from = dragFrom;
        boolean moved = dragMoved;
        dragId = null;
        dragFrom = 0;
        dragMoved = false;

        int target = slotAt((int) mouseX, (int) mouseY);
        if (moved) {
            if (target > 0) {
                place(target, id, from);
            } else if (from > 0) {
                // Dragged off the dial: that is what clearing is now.
                clearSlot(from);
            }
            return true;
        }
        // Never moved, so it was a click after all.
        if (from > 0) {
            slot = from;
            assignTo(from);
        } else {
            selectedId = id.toString();
        }
        return true;
    }

    private boolean clickTabs(double mouseX) {
        if (mouseX >= left + PANEL_W - 20 && mouseX < left + PANEL_W - 13) {
            tabScroll = Math.max(0, tabScroll - 1);
            return true;
        }
        if (mouseX >= left + PANEL_W - 12 && mouseX < left + PANEL_W - 5) {
            tabScroll = Math.min(sources.size(), tabScroll + 1);
            return true;
        }
        int x = left + 6;
        for (int i = tabScroll; i <= sources.size(); i++) {
            String name = i == 0
                    ? Component.translatable("townstead.ability.picker.all").getString()
                    : sources.get(i - 1);
            int tally = i == 0 ? ClientAbilityLoadout.available().size()
                    : counts.getOrDefault(name, 0);
            String label = RecordTrim.fit(font, name, 90);
            int w = font.width(label) + font.width(String.valueOf(tally)) + 14;
            if (x + w > left + PANEL_W - 24) break;
            if (mouseX >= x && mouseX < x + w) {
                source = i == 0 ? "" : name;
                scroll = 0;
                refresh();
                return true;
            }
            x += w + 2;
        }
        return false;
    }

    /** Arrows move the selection, Enter takes it: typing to filter should not need the mouse. */
    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        // Enter places the chosen entry in the slot the dials are pointing at, which is the
        // keyboard's version of dropping it there.
        if (key == 257 || key == 335) {
            if (selected() != null) {
                assignTo(slot);
                return true;
            }
        }
        if (key >= 262 && key <= 265 && !shown.isEmpty()) {
            int index = 0;
            for (int i = 0; i < shown.size(); i++) {
                if (shown.get(i).id().equals(selectedId)) {
                    index = i;
                    break;
                }
            }
            int step = switch (key) {
                case 262 -> 1;
                case 263 -> -1;
                case 264 -> columns;
                default -> -columns;
            };
            index = Mth.clamp(index + step, 0, shown.size() - 1);
            selectedId = shown.get(index).id();
            keepVisible(index);
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    private void keepVisible(int index) {
        int rowTop = (index / columns) * PITCH;
        int view = gridBottom - gridTop - 12;
        if (rowTop < scroll) scroll = rowTop;
        else if (rowTop + PITCH > scroll + view) scroll = rowTop + PITCH - view;
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollBy(deltaY);
        return true;
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollBy(delta);
        return true;
    }
    *///?}

    private void scrollBy(double delta) {
        int max = Math.max(0, rows() * PITCH + 6 - (gridBottom - gridTop));
        scroll = Mth.clamp(scroll - delta * 14, 0, max);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void send(Map<Integer, ResourceLocation> arrangement) {
        AbilityLoadoutC2SPayload payload = new AbilityLoadoutC2SPayload(Map.copyOf(arrangement));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
