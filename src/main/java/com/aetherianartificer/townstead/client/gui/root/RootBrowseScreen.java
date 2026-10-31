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
import com.aetherianartificer.townstead.root.RootMix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every root at once, opened from the Root tab's "Browse roots..." button. Filters on the left
 * (shortcuts, species, pack, traits), every result grouped in the middle, the picked root on the
 * right with the editor's own model wearing it. Filters that split nothing are left out, so a small
 * catalogue gets a small screen.
 *
 * <p>Pick | Mix switches what a click does. In Pick it chooses one root; in Mix it adds the root to
 * a tray of up to {@value RootMix#MAX}, all of one species, whose shares step in quarters. The tray
 * keeps its four slots drawn so nothing below it moves, and each member keeps one colour across its
 * tile, its slot and the share bar.</p>
 *
 * <p>It wears the MCA editor's greys and borrows the Quest Ledger's icons, tab strip and spacing
 * (a {@value #GUTTER}px gutter, {@value #PAD}px panel padding). Every header control starts or ends
 * on the edge of the panel below it. It opens on what the editor shows. Each click shows a founding the
 * server rolled ({@link RootEditSession}), or the target itself when the click picks what it already
 * is; Choose returns to the editor with that same founding on show, not applied, and Back with
 * whatever was on show before.</p>
 */
public final class RootBrowseScreen extends Screen {

    // The Quest Ledger's grid.
    private static final int GUTTER = 10;
    private static final int HEADER_H = 20;
    private static final int HEADER_GAP = 6;
    private static final int PAD = 6;
    private static final int SEARCH_MIN = 60;
    private static final int ROW = 12;
    private static final int TILE = 16;
    private static final int TILE_GAP = 2;
    private static final int SECTION_GAP = 6;
    private static final int FACET_VALUES = 5;
    private static final int ICON_SLOT = 12;

    private static final int SCRIM = 0xA0000000;
    private static final int PANEL = 0xE8101010;
    private static final int RULE = 0xFF2E2E2E;
    private static final int COUNT = 0xFF707070;
    private static final int TEXT = 0xFFD8D8D8;
    private static final int MUTED = 0xFF8A8A8A;
    private static final int OFF_TEXT = 0xFF4A4A4A;
    private static final int CHOSEN_FILL = 0xFF34511C;
    private static final int ROW_HOVER = 0xFF2A2A2A;
    private static final int TILE_FILL = 0xFF1A1A1A;
    private static final int TILE_EDGE = 0xFF2E2E2E;
    private static final int TILE_HOVER = 0xFF2C2C2C;
    private static final int TILE_SELECTED = 0xFF3A3A3A;
    private static final int TILE_SELECTED_EDGE = 0xFFA0A0A0;
    private static final int TILE_CURRENT = 0xFF2F6B2F;
    private static final int TILE_MEMBER = 0xFF232323;
    private static final int TILE_OFF = 0xFF121212;
    private static final int TILE_OFF_EDGE = 0xFF1C1C1C;
    private static final int SEG_ACTIVE = 0xFF5A8A2A;
    private static final int SEG_ACTIVE_EDGE = 0xFF88DD44;
    private static final int SEG_NORMAL = 0xFF3A3A3A;
    private static final int SEG_HOVER = 0xFF5A5A5A;
    private static final int SEG_DIVIDER = 0xFF222222;
    private static final int EMPTY_SLOT = 0xFF353535;

    /** Mix members' colours: Okabe-Ito, distinct for the common colour blindnesses and in lightness. */
    private static final int[] MEMBER_COLORS = { 0xFFD55E00, 0xFF56B4E9, 0xFFF0E442, 0xFF009E73 };

    private static final ResourceLocation HANGOUT = ResourceLocation.tryParse("townstead:textures/gui/catalog/hangout.png");
    private static final ResourceLocation LETTER = ResourceLocation.tryParse("townstead:textures/gui/quest/letter.png");

    private static final List<String> TRAITS = List.of("winged", "small", "large", "aquatic", "lays_eggs",
            "nocturnal", "darkvision", "ageless", "sterile");

    private final Screen parent;
    private final @Nullable LivingEntity model;
    private final RootEditSession session;
    /** What was on show when the browser opened, for Back. */
    private final RootEditSession.@Nullable Founding atOpen;
    /** The editor the open browser sits over, which keeps its model's state meanwhile. */
    private static @Nullable Screen over;
    private boolean returning;
    private final Map<String, RootCatalogEntry> entries = new LinkedHashMap<>();
    private final Map<String, Item> items = new HashMap<>();
    private final RootBrowseModel browser;
    private final RootBrowseModel.State state = new RootBrowseModel.State();
    private final Set<Facet> openFacets = new LinkedHashSet<>();
    private final boolean manyPacks;
    private final boolean mixable;

    private @Nullable RootCatalogEntry selected;
    private boolean mixing;
    private final List<Slot> tray = new ArrayList<>();
    private View view;
    private EditBox search;
    private Button pin;
    private Button roll;
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
    private int groupX, groupCell, modeX, modeW;
    private boolean groupCycle;

    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    /** One root in the mix tray: its id, its share in quarters, and the colour it keeps. */
    private static final class Slot {
        final String id;
        final int color;
        int quarters;

        Slot(String id, int color) {
            this.id = id;
            this.color = color;
        }
    }

    /** One scrollable panel's offset and how far it can go, learned from the last frame. */
    private static final class Scroll {
        double offset;
        int max;
        // The bar as last drawn, for clicks and drags.
        int barX, trackTop, trackBottom, thumbTop, thumbBottom;
        // Where the cursor holds the thumb while dragging; negative when not dragging.
        double grab = -1;

        void by(double delta, int step) {
            offset = Math.max(0, Math.min(max, offset - delta * step));
        }

        void settle(int contentBottom, int viewBottom) {
            max = Math.max(0, contentBottom + (int) offset - viewBottom);
            offset = Math.min(offset, max);
        }

        void draw(GuiGraphics g, int x, int top, int bottom, int mouseX, int mouseY) {
            barX = x;
            trackTop = top;
            trackBottom = bottom;
            if (max <= 0) {
                thumbTop = thumbBottom = 0;
                return;
            }
            int track = bottom - top;
            int thumb = Math.max(12, track * track / (track + max));
            thumbTop = top + (int) ((track - thumb) * offset / max);
            thumbBottom = thumbTop + thumb;
            boolean lit = grab >= 0 || overBar(mouseX, mouseY) && mouseY >= thumbTop && mouseY < thumbBottom;
            g.fill(x, top, x + 2, bottom, 0x50000000);
            g.fill(x, thumbTop, x + 2, thumbBottom, lit ? 0xFFFFFFFF : 0xFFAAAAAA);
        }

        /** The bar is 2px wide; the panel's right padding around it all counts as the bar. */
        boolean overBar(double mouseX, double mouseY) {
            return max > 0 && mouseX >= barX + 3 - PAD && mouseX < barX + 3 && mouseY >= trackTop && mouseY < trackBottom;
        }

        /** Grabs the thumb, or jumps it to the cursor when the click lands on the track. */
        boolean press(double mouseX, double mouseY) {
            if (!overBar(mouseX, mouseY)) return false;
            if (mouseY >= thumbTop && mouseY < thumbBottom) {
                grab = mouseY - thumbTop;
            } else {
                grab = (thumbBottom - thumbTop) / 2.0;
                drag(mouseY);
            }
            return true;
        }

        void drag(double mouseY) {
            int span = trackBottom - trackTop - (thumbBottom - thumbTop);
            if (span <= 0) return;
            offset = Math.max(0, Math.min(max, (mouseY - grab - trackTop) * max / span));
        }
    }

    /** A vanilla button with a small die beside its label, laid out like {@link QuestIconButton}. */
    private static final class RollButton extends Button {
        RollButton(int x, int y, int w, int h, Component message, OnPress onPress) {
            super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderString(GuiGraphics g, Font font, int color) {
            int total = 9 + 4 + font.width(getMessage());
            int left = getX() + Math.max(2, (getWidth() - total) / 2);
            drawDie(g, left, getY() + (getHeight() - 9) / 2, active ? 0xFFECECEC : 0xFF8A8A8A);
            g.drawString(font, getMessage(), left + 13, getY() + (getHeight() - 8) / 2, color);
        }
    }

    /** A 9px die showing three. */
    private static void drawDie(GuiGraphics g, int x, int y, int face) {
        g.fill(x, y, x + 9, y + 9, 0xFF1A1A1A);
        g.fill(x + 1, y + 1, x + 8, y + 8, face);
        for (int i = 2; i <= 6; i += 2) g.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFF1A1A1A);
    }

    public RootBrowseScreen(Screen parent, boolean self, @Nullable LivingEntity model, RootEditSession session) {
        super(Component.translatable("townstead.root.browse.title"));
        this.parent = parent;
        this.model = model;
        this.session = session;
        this.atOpen = session.shown();
        over = parent;
        List<Item> list = new ArrayList<>();
        Set<String> packs = new LinkedHashSet<>();
        Map<String, Integer> perSpecies = new HashMap<>();
        for (RootCatalogEntry entry : RootCatalogClient.origins()) {
            if (!entry.listedFor(self)) continue;
            entries.put(entry.id(), entry);
            Item item = item(entry, RootCatalogClient.browse(entry.id()));
            list.add(item);
            items.put(item.id(), item);
            packs.add(item.pack());
            perSpecies.merge(item.speciesId(), 1, Integer::sum);
        }
        this.manyPacks = packs.size() > 1;
        this.mixable = perSpecies.values().stream().anyMatch(n -> n > 1);
        Map<String, String> traitLabels = new LinkedHashMap<>();
        for (String trait : TRAITS) traitLabels.put(trait, Component.translatable("townstead.root.trait." + trait).getString());
        this.browser = new RootBrowseModel(list, traitLabels);
        // Open on what the editor shows: a mix in Mix mode with its tray, a root picked in Pick.
        RootEditSession.Choice shown = session.shownChoice();
        RootMix initialMix = shown != null ? shown.mix() : null;
        this.selected = shown != null ? entries.get(shown.rootId()) : null;
        if (initialMix != null && mixable) {
            mixing = true;
            for (RootMix.Member m : initialMix.members()) {
                if (!entries.containsKey(m.rootId())) continue;
                Slot slot = new Slot(m.rootId(), nextColor());
                slot.quarters = m.quarters();
                tray.add(slot);
            }
            if (tray.stream().mapToInt(s -> s.quarters).sum() != RootMix.WHOLE) rebalance();
        }
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

        // Over the preview: Pick | Mix from its left edge, Back to its right edge.
        int backW = Math.max(50, font.width(Component.translatable("gui.back")) + 16);
        int backX = previewX + previewW - backW;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(backX, GUTTER, backW, HEADER_H).build());
        modeX = previewX;
        modeW = backX - PAD - previewX;

        // Over the results: search from the left edge, Group by ending on the right edge.
        groupCell = 0;
        for (GroupBy by : GroupBy.values()) groupCell = Math.max(groupCell, font.width(groupLabel(by)) + PAD * 2);
        int searchX = centerX + 13;
        // Too narrow for the whole strip: one cell that cycles through the groupings.
        groupCycle = manyPacks && centerW - 13 - PAD - SEARCH_MIN < groupCell * GroupBy.values().length;
        groupX = centerX + centerW - groupCell * (groupCycle ? 1 : GroupBy.values().length);
        int searchRight = manyPacks ? groupX - PAD : centerX + centerW;
        String previous = search != null ? search.getValue() : state.query;
        search = new EditBox(font, searchX + 1, GUTTER + 1, Math.max(20, searchRight - searchX - 2), HEADER_H - 2,
                Component.translatable("townstead.root.browse.search"));
        // The box does not clip its hint, so a long one runs into the Group by buttons.
        search.setHint(Component.literal(GeneVisuals.truncate(font,
                Component.translatable("townstead.root.browse.search").getString(), search.getInnerWidth())));
        search.setMaxLength(80);
        search.setValue(previous);
        search.setResponder(value -> {
            state.query = value;
            resultScroll.offset = 0;
            refresh();
        });
        addRenderableWidget(search);

        // Two equal buttons in both modes, so Choose never moves.
        buttonsY = bottom - PAD - 20;
        int inner = previewW - PAD * 2;
        int half = (inner - PAD) / 2;
        int leftX = previewX + PAD;
        pin = addRenderableWidget(new QuestIconButton(leftX, buttonsY, half, 20,
                Component.translatable("townstead.root.browse.pin"), b -> togglePin(), QuestIcons.Icon.PINNED));
        roll = addRenderableWidget(new RollButton(leftX, buttonsY, half, 20,
                Component.translatable("townstead.root.browse.roll"), b -> {
                    RootEditSession.Choice choice = trayChoice();
                    if (choice != null) session.roll(choice);
                }));
        choose = addRenderableWidget(new QuestIconButton(leftX + half + PAD, buttonsY, inner - half - PAD, 20,
                Component.translatable("townstead.root.browse.choose"), b -> chooseSelected(), QuestIcons.Icon.DONE));
        updateButtons();
        refresh();
        RootWorldCounts.request(() -> Minecraft.getInstance().execute(this::refresh));
    }

    @Override
    public void removed() {
        RootWorldCounts.stopListening();
        over = null;
        // Closed some other way than back to the editor: the editor is gone too, so it cleans up now.
        if (!returning) parent.removed();
        super.removed();
    }

    /**
     * Whether a browser opened over {@code editor}. Opening one takes the editor off screen, but the
     * browser shows the editor's model, so the editor must keep that model's state until it is back.
     */
    public static boolean isOver(Screen editor) {
        return over == editor;
    }

    private void backToEditor() {
        if (minecraft == null) return;
        returning = true;
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (minecraft == null) return;
        session.restore(atOpen);
        backToEditor();
    }

    /** Back to the editor with what is on show, still to be applied there. */
    private void chooseSelected() {
        if (minecraft == null) return;
        if (mixing) {
            if (tray.isEmpty()) return;
            for (Slot slot : tray) RootBrowsePrefs.viewed(slot.id);
        } else {
            if (selected == null) return;
            RootBrowsePrefs.viewed(selected.id());
        }
        backToEditor();
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
        session.want(RootEditSession.Choice.root(entry.id()));
        updateButtons();
    }

    private void setMixing(boolean on) {
        if (mixing == on) return;
        mixing = on;
        detailScroll.offset = 0;
        if (on) {
            if (tray.isEmpty() && selected != null) {
                tray.add(new Slot(selected.id(), nextColor()));
                rebalance();
            }
            previewMix();
        } else {
            if (!tray.isEmpty()) selected = entries.get(largest().id);
            if (selected != null) session.want(RootEditSession.Choice.root(selected.id()));
        }
        updateButtons();
    }

    private void updateButtons() {
        if (pin == null) return;
        pin.visible = !mixing;
        roll.visible = mixing;
        pin.active = selected != null;
        roll.active = !tray.isEmpty() && !session.rolling();
        choose.active = mixing ? !tray.isEmpty() : selected != null;
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

    // ---- the mix ----

    private int trayIndex(String id) {
        for (int i = 0; i < tray.size(); i++) if (tray.get(i).id.equals(id)) return i;
        return -1;
    }

    /** The species every member shares, or null while the tray is empty. */
    private @Nullable String lockedSpecies() {
        if (tray.isEmpty()) return null;
        Item first = items.get(tray.get(0).id);
        return first == null ? null : first.speciesId();
    }

    private boolean joinable(Item item) {
        String species = lockedSpecies();
        return species == null || species.equals(item.speciesId());
    }

    private void toggleMember(String id) {
        int at = trayIndex(id);
        if (at >= 0) {
            tray.remove(at);
        } else {
            Item item = items.get(id);
            if (item == null || !joinable(item) || tray.size() >= RootMix.MAX) return;
            tray.add(new Slot(id, nextColor()));
        }
        rebalance();
        detailScroll.offset = 0;
        previewMix();
        updateButtons();
    }

    private void removeMember(int index) {
        tray.remove(index);
        rebalance();
        previewMix();
        updateButtons();
    }

    private int nextColor() {
        for (int color : MEMBER_COLORS) {
            boolean used = false;
            for (Slot slot : tray) used |= slot.color == color;
            if (!used) return color;
        }
        return MEMBER_COLORS[0];
    }

    /** Splits the whole evenly, the earliest members taking any quarter left over. */
    private void rebalance() {
        int n = tray.size();
        if (n == 0) return;
        for (int i = 0; i < n; i++) tray.get(i).quarters = RootMix.WHOLE / n + (i < RootMix.WHOLE % n ? 1 : 0);
    }

    /** The other member that gives up a quarter when {@code index} takes one: the largest that can spare it. */
    private int donorFor(int index) {
        int best = -1;
        for (int i = 0; i < tray.size(); i++) {
            if (i == index || tray.get(i).quarters <= 1) continue;
            if (best < 0 || tray.get(i).quarters > tray.get(best).quarters) best = i;
        }
        return best;
    }

    /** The other member that takes the quarter {@code index} gives up: the smallest. */
    private int recipientFor(int index) {
        int best = -1;
        for (int i = 0; i < tray.size(); i++) {
            if (i == index) continue;
            if (best < 0 || tray.get(i).quarters < tray.get(best).quarters) best = i;
        }
        return best;
    }

    private void more(int index) {
        int donor = donorFor(index);
        if (donor < 0) return;
        tray.get(donor).quarters--;
        tray.get(index).quarters++;
        previewMix();
    }

    private void less(int index) {
        int recipient = recipientFor(index);
        if (recipient < 0 || tray.get(index).quarters <= 1) return;
        tray.get(index).quarters--;
        tray.get(recipient).quarters++;
        previewMix();
    }

    private Slot largest() {
        Slot best = tray.get(0);
        for (Slot slot : tray) if (slot.quarters > best.quarters) best = slot;
        return best;
    }

    /** The tray as a mix, or null with fewer than two members. */
    private @Nullable RootMix mix() {
        if (tray.size() < 2) return null;
        List<RootMix.Member> members = new ArrayList<>(tray.size());
        for (Slot slot : tray) members.add(new RootMix.Member(slot.id, slot.quarters));
        return new RootMix(members);
    }

    /** The tray as a choice: the mix, the one root when it stands alone, or null when empty. */
    private RootEditSession.@Nullable Choice trayChoice() {
        if (tray.isEmpty()) return null;
        RootMix mix = mix();
        return mix != null ? RootEditSession.Choice.of(mix) : RootEditSession.Choice.root(tray.get(0).id);
    }

    /** Shows the tray on the model; an empty tray shows the target as it is. */
    private void previewMix() {
        RootEditSession.Choice choice = trayChoice();
        if (choice != null) session.want(choice);
        else session.showCurrent();
    }

    /** {@code [name, backstory]} of a mix, from the founding or the target when either is it; else null. */
    private String @Nullable [] mixNamed(RootMix mix) {
        RootEditSession.Founding founding = session.shown();
        if (founding != null && mix.equals(founding.choice().mix())) return new String[] { founding.name(), founding.backstory() };
        RootEditSession.Identity identity = session.identity();
        if (identity != null && mix.equals(identity.choice().mix())) return new String[] { identity.name(), identity.backstory() };
        return null;
    }

    /** The target's own root, when it is one root rather than a mix. */
    private String currentId() {
        RootEditSession.Identity identity = session.identity();
        return identity != null && identity.choice().mix() == null ? identity.choice().rootId() : "";
    }

    // ---- rendering ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        hover.clear();
        updateButtons();
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
        if (mixing) drawMixPreview(g, mouseX, mouseY);
        else drawPreview(g, mouseX, mouseY);
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
        String count = view.matching() == view.total() ? Integer.toString(view.total())
                : Component.translatable("townstead.root.browse.of", view.matching(), view.total()).getString();
        // Title and count stay over the rail, so they never run under the search icon.
        String heading = GeneVisuals.truncate(font, title.getString(), railW - PAD - font.width(count));
        g.drawString(font, heading, railX, textY, 0xFFFFFFFF, false);
        g.drawString(font, count, railX + font.width(heading) + PAD, textY, COUNT, false);
        QuestIcons.draw(g, QuestIcons.Icon.DETAILS, centerX, GUTTER + (HEADER_H - 9) / 2);
        if (groupCycle) {
            GroupBy[] all = GroupBy.values();
            segment(g, groupX, groupCell, groupLabel(state.groupBy), true, true, mouseX, mouseY, () -> {
                state.groupBy = all[(state.groupBy.ordinal() + 1) % all.length];
                resultScroll.offset = 0;
                refresh();
            });
        } else if (manyPacks) {
            GroupBy[] all = GroupBy.values();
            for (GroupBy by : all) {
                int x = groupX + by.ordinal() * groupCell;
                segment(g, x, groupCell, groupLabel(by), state.groupBy == by, by.ordinal() == 0, mouseX, mouseY, () -> {
                    state.groupBy = by;
                    resultScroll.offset = 0;
                    refresh();
                });
            }
        }
        if (mixable) {
            int pickW = modeW / 2;
            segment(g, modeX, pickW, Component.translatable("townstead.root.browse.mode.pick"), !mixing, true,
                    mouseX, mouseY, () -> setMixing(false));
            segment(g, modeX + pickW, modeW - pickW, Component.translatable("townstead.root.browse.mode.mix"), mixing, false,
                    mouseX, mouseY, () -> setMixing(true));
        }
    }

    private Component groupLabel(GroupBy by) {
        return Component.translatable("townstead.root.browse.group." + by.name().toLowerCase(Locale.ROOT));
    }

    /** One cell of the Quest Ledger's tab strip: grey, the chosen one green with a bright top edge. */
    private void segment(GuiGraphics g, int x, int w, Component label, boolean chosen, boolean first,
                         int mouseX, int mouseY, Runnable action) {
        int y = GUTTER;
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + HEADER_H;
        g.fill(x, y, x + w, y + HEADER_H, chosen ? SEG_ACTIVE : hovered ? SEG_HOVER : SEG_NORMAL);
        g.fill(x, y, x + w, y + 1, chosen ? SEG_ACTIVE_EDGE : 0xFF555555);
        g.fill(x, y + HEADER_H - 1, x + w, y + HEADER_H, SEG_DIVIDER);
        if (!first) g.fill(x, y + 1, x + 1, y + HEADER_H - 1, SEG_DIVIDER);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (HEADER_H - 8) / 2,
                chosen ? 0xFFFFFFFF : hovered ? 0xFFDDDDDD : 0xFFAAAAAA, false);
        hits.add(new Hit(x, y, x + w, y + HEADER_H, action));
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
            railRow(g, x, y, w, mouseX, mouseY, on, false, Component.translatable(
                    "townstead.root.browse.shortcut." + s.name().toLowerCase(Locale.ROOT)), shortcut.getValue(), () -> {
                state.shortcut = on ? Shortcut.ALL : s;
                resultScroll.offset = 0;
                refresh();
            }, clipTop, clipBottom);
            shortcutIcon(g, s, x + 1, y + 1);
            y += ROW;
        }
        if (anyShortcut) y += SECTION_GAP;
        String locked = mixing ? lockedSpecies() : null;
        for (Map.Entry<Facet, List<Value>> facet : view.facets().entrySet()) {
            Facet f = facet.getKey();
            y = heading(g, Component.translatable("townstead.root.browse.facet." + f.name().toLowerCase(Locale.ROOT)),
                    null, x, y, w);
            List<Value> values = facet.getValue();
            boolean open = openFacets.contains(f) || values.size() <= FACET_VALUES + 1;
            int shown = open ? values.size() : FACET_VALUES;
            for (int i = 0; i < shown; i++) {
                Value v = values.get(i);
                if (locked != null && f == Facet.SPECIES) {
                    // A mix stays within one species: its own is held, the rest are out of reach.
                    boolean own = v.key().equals(locked);
                    railRow(g, x, y, w, mouseX, mouseY, own, !own, Component.literal(v.label()), v.count(), null,
                            clipTop, clipBottom);
                    if (own) drawLock(g, x + 1, y + 1);
                    y += ROW;
                    continue;
                }
                railRow(g, x, y, w, mouseX, mouseY, v.chosen(), false, Component.literal(v.label()), v.count(), () -> {
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
        scrollbar(g, railX + railW - 3, railScroll, mouseX, mouseY);
    }

    /** One rail line: a 9px icon slot, the label, its count on the right. A null action draws it inert. */
    private void railRow(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY, boolean chosen, boolean off,
                         Component label, int count, @Nullable Runnable action, int clipTop, int clipBottom) {
        boolean hovered = action != null && within(mouseX, mouseY, x, y, w, ROW, clipTop, clipBottom);
        if (chosen) g.fill(x, y, x + w, y + ROW, CHOSEN_FILL);
        else if (hovered) g.fill(x, y, x + w, y + ROW, ROW_HOVER);
        String countText = Integer.toString(count);
        int countW = font.width(countText);
        g.drawString(font, RootDetails.trim(font, label.getString(), w - 13 - countW - PAD), x + 13, y + 2,
                off ? OFF_TEXT : chosen || hovered ? 0xFFFFFFFF : TEXT, false);
        g.drawString(font, countText, x + w - countW - 2, y + 2, chosen ? 0xFFB8D8A0 : off ? 0xFF444444 : COUNT, false);
        if (action != null) addHit(x, y, w, ROW, clipTop, clipBottom, action);
    }

    /** A gold heading with an optional count and a muted note on the right, ruled underneath. */
    private int heading(GuiGraphics g, Component text, @Nullable String count, int x, int y, int w) {
        return heading(g, text, count, null, x, y, w);
    }

    private int heading(GuiGraphics g, Component text, @Nullable String count, @Nullable Component note,
                        int x, int y, int w) {
        g.drawString(font, text, x, y + 2, RootDetails.GOLD, false);
        if (count != null) g.drawString(font, count, x + font.width(text) + PAD, y + 2, COUNT, false);
        if (note != null) g.drawString(font, note, x + w - font.width(note) - 2, y + 2, 0xFF6A6A6A, false);
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

    /** A 9px padlock: the species a mix is held to. */
    private static void drawLock(GuiGraphics g, int x, int y) {
        g.fill(x + 2, y, x + 7, y + 1, 0xFFD8D8D8);
        g.fill(x + 1, y + 1, x + 2, y + 4, 0xFFD8D8D8);
        g.fill(x + 7, y + 1, x + 8, y + 4, 0xFFD8D8D8);
        g.fill(x + 1, y + 4, x + 8, y + 9, 0xFFD4A63A);
        g.fill(x + 4, y + 5, x + 5, y + 7, 0xFF3A2A10);
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
        Set<String> pinned = RootBrowsePrefs.pinned();
        for (Section section : view.sections()) {
            boolean allOff = mixing && lockedSpecies() != null;
            if (allOff) {
                for (Item item : section.items()) allOff &= !joinable(item);
            }
            y = heading(g, Component.literal(section.title()), Integer.toString(section.items().size()),
                    allOff ? Component.translatable("townstead.root.browse.other_species") : null, x, y, w);
            int column = 0;
            for (Item item : section.items()) {
                // Columns share the width exactly, so the grid's right edge meets the panel padding.
                int tx = x + column * (w + TILE_GAP) / columns;
                int tw = x + (column + 1) * (w + TILE_GAP) / columns - TILE_GAP - tx;
                drawTile(g, item, tx, y, tw, mouseX, mouseY, pinned.contains(item.id()), clipTop, clipBottom);
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
        scrollbar(g, centerX + centerW - 3, resultScroll, mouseX, mouseY);
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
        int member = mixing ? trayIndex(item.id()) : -1;
        boolean off = mixing && member < 0 && !joinable(item);
        boolean isSelected = !mixing && selected != null && selected.id().equals(item.id());
        boolean hovered = !off && within(mouseX, mouseY, x, y, w, TILE, clipTop, clipBottom);
        boolean current = !mixing && item.id().equals(currentId());
        int edge = member >= 0 ? tray.get(member).color : isSelected ? TILE_SELECTED_EDGE : off ? TILE_OFF_EDGE : TILE_EDGE;
        int fill = member >= 0 ? TILE_MEMBER : current ? TILE_CURRENT : isSelected ? TILE_SELECTED
                : off ? TILE_OFF : hovered ? TILE_HOVER : TILE_FILL;
        g.fill(x, y, x + w, y + TILE, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + TILE - 1, fill);
        int textY = y + (TILE - 8) / 2;
        // The icon slot is always kept, so names line up whether or not a root has art.
        int textX = x + 2 + ICON_SLOT + 4;
        RootBrowseEntry browse = RootCatalogClient.browse(item.id());
        if (browse != null && !browse.icon().isEmpty() && IconArt.draw(g, browse.icon(), x + 2, y + 2, 0.75f) && off) {
            g.fill(x + 2, y + 2, x + 2 + ICON_SLOT, y + 2 + ICON_SLOT, 0xB0121212);
        }
        int right = x + w - 2;
        if (member >= 0 && tray.size() > 1) {
            int badgeW = 20;
            right -= badgeW;
            g.fill(right, y + 2, right + badgeW, y + 2 + ROW, tray.get(member).color);
            String s = RootMix.shareLabel(tray.get(member).quarters);
            g.drawString(font, s, right + (badgeW - font.width(s)) / 2, y + 4, 0xFF0C0C0C, false);
            right -= 4;
        } else {
            right -= 2;
            String qualifier = manyPacks && browse != null && !mixing ? RootDetails.text(browse.packQualifier()).getString() : "";
            if (!qualifier.isEmpty() && font.width(qualifier) < w / 3) {
                right -= font.width(qualifier);
                g.drawString(font, qualifier, right, textY, isSelected ? 0xFF9A9A9A : 0xFF5E5E5E, false);
                right -= 4;
            }
        }
        if (pinned && !off) {
            right -= 9;
            QuestIcons.draw(g, QuestIcons.Icon.PINNED, right, y + (TILE - 9) / 2);
            right -= 4;
        }
        g.drawString(font, RootDetails.trim(font, item.name(), right - textX), textX, textY,
                off ? 0xFF444444 : member >= 0 || isSelected || hovered || current ? 0xFFFFFFFF : TEXT, false);
        if (off) return;
        addHit(x, y, w, TILE, clipTop, clipBottom, mixing ? () -> toggleMember(item.id()) : () -> select(item.id()));
    }

    // ---- preview ----

    private int modelHeight() {
        return Math.max(60, Math.min(120, (bottom - top) * 2 / 5));
    }

    private void drawWell(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, RootDetails.WELL);
        if (model != null) drawModel(g, x, y, x + w, y + h, mouseX, mouseY);
        if (session.rollingLong()) {
            // A roll that takes a moment says so; a quick one just lands.
            drawDie(g, x + w - 13, y + 4, 0xFFECECEC);
            g.drawString(font, Component.translatable("townstead.root.rolling"), x + 4, y + 5, MUTED, false);
        }
    }

    private void drawPreview(GuiGraphics g, int mouseX, int mouseY) {
        int x = previewX + PAD;
        int w = previewW - PAD * 2;
        int y = top + PAD;
        int modelH = modelHeight();
        drawWell(g, x, y, w, modelH, mouseX, mouseY);
        y += modelH + PAD;
        int clipBottom = buttonsY - PAD;
        if (selected == null) {
            detailScroll.max = 0;
            Component pick = Component.translatable("townstead.root.browse.pick");
            g.drawString(font, pick, x + (w - font.width(pick)) / 2, y + PAD, MUTED, false);
            return;
        }
        drawRootDetails(g, selected, x, y, w, clipBottom, mouseX, mouseY, true);
    }

    /** The root's header, backstory, facts and powers, scrolling between {@code y} and {@code clipBottom}. */
    private void drawRootDetails(GuiGraphics g, RootCatalogEntry root, int x, int y, int w, int clipBottom,
                                 int mouseX, int mouseY, boolean withHeader) {
        int clipTop = y;
        g.enableScissor(previewX + 1, clipTop, previewX + previewW - 1, clipBottom);
        int cy = y - (int) detailScroll.offset;
        RootBrowseEntry browse = RootCatalogClient.browse(root.id());
        if (withHeader) cy = RootDetails.header(g, root, browse, x, cy, w) + PAD;
        cy = RootDetails.backstory(g, root, x, cy, w, Integer.MAX_VALUE);
        cy = RootDetails.facts(g, browse, x, cy + PAD, w);
        boolean inside = mouseY >= clipTop && mouseY < clipBottom;
        cy = RootDetails.powers(g, browse, x, cy + PAD, w, inside ? mouseX : -1, mouseY, hover);
        g.disableScissor();
        detailScroll.settle(cy, clipBottom);
        detailScroll.draw(g, previewX + previewW - 3, clipTop, clipBottom, mouseX, mouseY);
    }

    /**
     * Mix mode's column: the model wearing the roll, the mix's name, the tray (four slots, always
     * drawn), then the details below, scrolling.
     */
    private void drawMixPreview(GuiGraphics g, int mouseX, int mouseY) {
        int x = previewX + PAD;
        int w = previewW - PAD * 2;
        int y = top + PAD;
        int modelH = modelHeight();
        drawWell(g, x, y, w, modelH, mouseX, mouseY);
        y += modelH + PAD;

        // Header: what the mix is called, where it comes from.
        RootMix mix = mix();
        String[] named = mix != null ? mixNamed(mix) : null;
        if (tray.isEmpty()) {
            Component hint = Component.translatable("townstead.root.browse.mix_empty");
            g.drawString(font, hint, x + (w - font.width(hint)) / 2, y + 6, MUTED, false);
        } else if (mix == null) {
            RootCatalogEntry only = entries.get(tray.get(0).id);
            if (only != null) RootDetails.header(g, only, RootCatalogClient.browse(only.id()), x, y, w);
        } else {
            RootCatalogEntry lead = entries.get(largest().id);
            for (int i = Math.min(2, tray.size()) - 1; i >= 0; i--) {
                RootBrowseEntry art = RootCatalogClient.browse(tray.get(i).id);
                if (art != null && !art.icon().isEmpty()) IconArt.draw(g, art.icon(), x + i * 4, y + i * 8, 0.75f);
            }
            String name = named != null && !named[0].isEmpty() ? named[0] : lead != null ? lead.name() : "";
            g.drawString(font, RootDetails.trim(font, name, w - 20), x + 20, y, 0xFFFFFFFF, false);
            if (lead != null) {
                g.drawString(font, RootDetails.trim(font, RootDetails.crumb(lead, RootCatalogClient.browse(lead.id())), w - 20),
                        x + 20, y + 10, RootDetails.GOLD, false);
            }
        }
        y += 20 + PAD;

        y = drawTray(g, x, y, w, mouseX, mouseY) + PAD;

        int clipBottom = buttonsY - PAD;
        if (tray.isEmpty()) {
            detailScroll.max = 0;
            return;
        }
        if (mix == null) {
            RootCatalogEntry only = entries.get(tray.get(0).id);
            if (only != null) drawRootDetails(g, only, x, y, w, clipBottom, mouseX, mouseY, false);
            return;
        }
        // A mix: the heritage's own backstory when one matches, then the facts of the largest share,
        // whose root sets the life cycle.
        int clipTop = y;
        g.enableScissor(previewX + 1, clipTop, previewX + previewW - 1, clipBottom);
        int cy = y - (int) detailScroll.offset;
        if (named != null && !named[1].isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.literal(named[1]), w)) {
                g.drawString(font, line, x, cy, TEXT, false);
                cy += 10;
            }
            cy += PAD;
        }
        cy = RootDetails.facts(g, RootCatalogClient.browse(largest().id), x, cy, w);
        g.disableScissor();
        detailScroll.settle(cy, clipBottom);
        detailScroll.draw(g, previewX + previewW - 3, clipTop, clipBottom, mouseX, mouseY);
    }

    /** The four tray slots and the share bar beneath them. Returns where the tray ends. */
    private int drawTray(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        int h = TILE_GAP + RootMix.MAX * TILE + (RootMix.MAX - 1) * TILE_GAP + TILE_GAP + 4 + TILE_GAP;
        g.fill(x, y, x + w, y + h, RootDetails.WELL);
        int sx = x + TILE_GAP;
        int sw = w - TILE_GAP * 2;
        int sy = y + TILE_GAP;
        for (int i = 0; i < RootMix.MAX; i++) {
            if (i < tray.size()) drawSlot(g, i, sx, sy, sw, mouseX, mouseY);
            else dashed(g, sx, sy, sw, TILE, EMPTY_SLOT);
            sy += TILE + TILE_GAP;
        }
        // Share bar: each member's colour, as wide as its share.
        int barY = sy;
        if (!tray.isEmpty()) {
            int gaps = tray.size() - 1;
            int span = sw - gaps;
            int bx = sx;
            int used = 0;
            for (int i = 0; i < tray.size(); i++) {
                used += tray.get(i).quarters;
                int end = sx + i + span * used / RootMix.WHOLE;
                g.fill(bx, barY, end, barY + 4, tray.get(i).color);
                bx = end + 1;
            }
        }
        return y + h;
    }

    /**
     * One member's slot, a tile's height: its colour, art and name, then the steppers, its share and
     * remove, on one set of columns down the right of every slot.
     */
    private void drawSlot(GuiGraphics g, int index, int x, int y, int w, int mouseX, int mouseY) {
        Slot slot = tray.get(index);
        g.fill(x, y, x + w, y + TILE, TILE_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + TILE - 1, TILE_FILL);
        g.fill(x + 1, y + 1, x + 3, y + TILE - 1, slot.color);
        RootBrowseEntry browse = RootCatalogClient.browse(slot.id);
        if (browse != null && !browse.icon().isEmpty()) IconArt.draw(g, browse.icon(), x + 5, y + 2, 0.75f);

        int by = y + 2;
        int removeX = x + w - 2 - ROW;
        int plusX = removeX - 4 - ROW;
        int shareX = plusX - TILE_GAP - 20;
        int minusX = shareX - TILE_GAP - ROW;
        int nameX = x + 5 + ICON_SLOT + 4;
        RootCatalogEntry entry = entries.get(slot.id);
        g.drawString(font, RootDetails.trim(font, entry != null ? entry.name() : slot.id, minusX - 4 - nameX),
                nameX, y + 4, 0xFFE0E0E0, false);
        boolean several = tray.size() > 1;
        stepper(g, minusX, by, "-", several && slot.quarters > 1, mouseX, mouseY, () -> less(index));
        String s = several ? RootMix.shareLabel(slot.quarters) : "";
        g.drawString(font, s, shareX + (20 - font.width(s)) / 2, y + 4, 0xFFFFFFFF, false);
        stepper(g, plusX, by, "+", several && donorFor(index) >= 0, mouseX, mouseY, () -> more(index));
        boolean overRemove = mouseX >= removeX && mouseX < removeX + ROW && mouseY >= by && mouseY < by + ROW;
        if (overRemove) g.fill(removeX, by, removeX + ROW, by + ROW, ROW_HOVER);
        QuestIcons.draw(g, QuestIcons.Icon.FAILED, removeX + 1, by + 1);
        hits.add(new Hit(removeX, by, removeX + ROW, by + ROW, () -> removeMember(index)));
    }

    /** A Row-sized vanilla-looking button. */
    private void stepper(GuiGraphics g, int x, int y, String label, boolean active, int mouseX, int mouseY,
                         Runnable action) {
        boolean hovered = active && mouseX >= x && mouseX < x + ROW && mouseY >= y && mouseY < y + ROW;
        g.fill(x, y, x + ROW, y + ROW, 0xFF000000);
        g.fill(x + 1, y + 1, x + ROW - 1, y + ROW - 1, !active ? 0xFF2C2C2C : hovered ? 0xFF8A8A8A : 0xFF6F6F6F);
        if (active) {
            g.fill(x + 1, y + 1, x + ROW - 1, y + 2, 0xFFAAAAAA);
            g.fill(x + 1, y + ROW - 2, x + ROW - 1, y + ROW - 1, 0xFF555555);
        }
        g.drawString(font, label, x + (ROW - font.width(label) + 1) / 2, y + 2, active ? 0xFFFFFFFF : 0xFF6A6A6A, false);
        if (active) hits.add(new Hit(x, y, x + ROW, y + ROW, action));
    }

    /** An empty tray slot: a dashed outline. */
    private static void dashed(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int i = 0; i < w; i += 4) {
            int e = Math.min(x + i + 2, x + w);
            g.fill(x + i, y, e, y + 1, color);
            g.fill(x + i, y + h - 1, e, y + h, color);
        }
        for (int i = 0; i < h; i += 4) {
            int e = Math.min(y + i + 2, y + h);
            g.fill(x, y + i, x + 1, e, color);
            g.fill(x + w - 1, y + i, x + w, e, color);
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

    private void scrollbar(GuiGraphics g, int x, Scroll scroll, int mouseX, int mouseY) {
        scroll.draw(g, x, top + PAD, bottom - PAD, mouseX, mouseY);
    }

    private List<Scroll> scrolls() {
        return List.of(railScroll, resultScroll, detailScroll);
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
        for (Scroll scroll : scrolls()) {
            if (scroll.press(mouseX, mouseY)) return true;
        }
        for (Hit hit : List.copyOf(hits)) {
            if (hit.contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (Scroll scroll : scrolls()) {
            if (scroll.grab >= 0) {
                scroll.drag(mouseY);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean dragging = false;
        for (Scroll scroll : scrolls()) {
            dragging |= scroll.grab >= 0;
            scroll.grab = -1;
        }
        return dragging || super.mouseReleased(mouseX, mouseY, button);
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
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && (mixing ? !tray.isEmpty() : selected != null)
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
