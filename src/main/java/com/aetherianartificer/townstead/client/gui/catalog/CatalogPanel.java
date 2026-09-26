package com.aetherianartificer.townstead.client.gui.catalog;

import com.aetherianartificer.townstead.client.catalog.*;
import com.aetherianartificer.townstead.client.catalog.CatalogGraphLayout.*;
import com.aetherianartificer.townstead.client.building.BuildingPinClientStore;
import com.aetherianartificer.townstead.client.gui.common.*;
import com.aetherianartificer.townstead.client.gui.common.TabButton;
import com.aetherianartificer.townstead.spirit.ClientVillageSpiritStore;
import com.aetherianartificer.townstead.spirit.SpiritRegistry;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Shared catalog UI embedded in MCA's Blueprint lifecycle; MCA remains the active Screen. */
public final class CatalogPanel {
    private static final int INSET = 8, GAP = 6;
    private static final int TITLE_BOTTOM = 29, SEARCH_HEIGHT = 20, TOOLBAR_HEIGHT = 18;
    private static final int SEARCH_TOP = TITLE_BOTTOM + GAP;
    private static final int SEARCH_RULE = SEARCH_TOP + SEARCH_HEIGHT + GAP;
    private static final int TOOLBAR_TOP = SEARCH_RULE + 1 + GAP;
    private static final int BODY_TOP = TOOLBAR_TOP + TOOLBAR_HEIGHT + GAP;
    private static final int FOOTER_HEIGHT = GAP + 16 + GAP + 1;
    private static final class SortOrder { Sort key = Sort.NAME; boolean descending; }
    private static final class TabState {
        final CatalogViewport camera = new CatalogViewport();
        final EnumMap<Grouping, SortOrder> sortOrders = new EnumMap<>(Grouping.class);
        Grouping grouping;
        Filter filter = Filter.ALL;
        String query = "", selected = "";
        boolean positioned;
        TabState(Grouping grouping) { this.grouping = grouping; }
        SortOrder order() { return sortOrders.computeIfAbsent(grouping, ignored -> new SortOrder()); }
    }
    private final Screen host;
    private final Font font;
    private final Runnable back;
    private final Runnable rebuildHost;
    private final TabState[] tabs = {new TabState(Grouping.GROUP), new TabState(Grouping.HANGOUT)};
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final CatalogMenu menu = new CatalogMenu();
    private int tab, x, y, w, h, graphW, bodyY, bodyH, virtualWidth, virtualHeight, revision = -1;
    private float uiScale = 1;
    private final Map<AbstractWidget, ScaledWidget> inputWidgets = new IdentityHashMap<>();
    private Object resourceManager, language;
    private Village village;
    private Object spiritSnapshot;
    private String pinned = "";
    private net.minecraft.resources.ResourceLocation background;
    private List<CatalogEntries.Display> entries = List.of();
    private Map<String, CatalogEntries.Display> byId = Map.of();
    private Layout layout = Layout.EMPTY;
    private CatalogGraphWidget graph;
    private CatalogInspectorWidget inspector;
    private EditBox search;
    private CatalogButton groupingButton, sortButton, directionButton, filterButton, pinButton, previousVariant, nextVariant, clearSearch;
    private String pendingFocus;
    private boolean dirty;
    private CatalogDataLoader.Theme theme = CatalogDataLoader.Theme.DEFAULT;

    public CatalogPanel(Screen host, Font font, Runnable back, Runnable rebuildHost) {
        this.host = host; this.font = font; this.back = back; this.rebuildHost = rebuildHost;
    }
    private TabState state() { return tabs[tab]; }
    private static Component tr(String key, Object... args) { return Component.translatable("townstead.catalog." + key, args); }
    private static Component option(Enum<?> value) { return tr(value.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT) + "." + value.name().toLowerCase(Locale.ROOT)); }
    public void init(int width, int height, Village village, Consumer<AbstractWidget> register) {
        this.village = village; widgets.clear(); inputWidgets.clear(); menu.close();
        var metrics = CatalogUiMetrics.forScreen(width, height, Minecraft.getInstance().getWindow().getGuiScale());
        uiScale = metrics.scale(); virtualWidth = metrics.width(); virtualHeight = metrics.height();
        w = Math.min(900, virtualWidth - 32); h = Math.min(500, virtualHeight - 32);
        x = (virtualWidth - w) / 2; y = (virtualHeight - h) / 2;
        int detailsW = Math.min(244, w * 30 / 100);
        graphW = w - detailsW - INSET * 3;
        bodyY = y + BODY_TOP; bodyH = h - BODY_TOP - FOOTER_HEIGHT;
        graph = new CatalogGraphWidget(font, x + INSET, bodyY, graphW, bodyH, state().camera,
                () -> state().selected, this::select);
        graph.uiScale(uiScale);
        inspector = new CatalogInspectorWidget(font, x + graphW + INSET * 2, bodyY, detailsW, bodyH);
        inspector.uiScale(uiScale);
        button(x + INSET, y + 6, 60, 18, Component.translatable("gui.back"), () -> false, b -> back.run());
        Component[] tabLabels = {Component.translatable("townstead.decorations.buildings_button"),
                Component.translatable("townstead.decorations.button")};
        int tabWidth = Math.max(font.width(tabLabels[0]), font.width(tabLabels[1])) + 16;
        var tabRects = Controls.tabLayout(x + INSET, y + SEARCH_TOP + (SEARCH_HEIGHT - Controls.TAB_H) / 2, tabWidth * 2, 2);
        for (int i = 0; i < tabLabels.length; i++) {
            int index = i;
            widgets.add(new TabButton(tabRects[i], tabLabels[i], () -> tab == index, b -> switchTab(index)));
        }
        search = new EditBox(font, tabRects[1].right() + 10, y + SEARCH_TOP, 100, SEARCH_HEIGHT, tr("search"));
        search.setMaxLength(128); search.setHint(tr("search")); search.setValue(state().query);
        search.setResponder(value -> { state().query = value; rebuild(true); }); widgets.add(search);
        clearSearch = button(search.getX() + search.getWidth() + 4, y + SEARCH_TOP, 20, SEARCH_HEIGHT, Component.literal("×"), () -> false, b -> {
            state().filter = Filter.ALL; search.setValue(""); rebuild(true);
        });
        groupingButton = button(x + INSET, y + TOOLBAR_TOP, 166, TOOLBAR_HEIGHT, Component.empty(), () -> false, b ->
                choices(b, groupings(), state().grouping, CatalogPanel::option, value -> {
                    state().grouping = value; rebuild(true);
                }));
        sortButton = button(x + 179, y + TOOLBAR_TOP, 126, TOOLBAR_HEIGHT, Component.empty(), () -> false, b ->
                choices(b, sorts(), state().order().key, CatalogPanel::option, value -> {
                    state().order().key = value; rebuild(true);
                }));
        directionButton = button(x + 308, y + TOOLBAR_TOP, 18, TOOLBAR_HEIGHT, Component.empty(), () -> false, b -> {
            state().order().descending = !state().order().descending; rebuild(true);
        });
        filterButton = button(x + 331, y + TOOLBAR_TOP, 130, TOOLBAR_HEIGHT, Component.empty(), () -> state().filter != Filter.ALL, b ->
                choices(b, tab == 0 ? List.of(Filter.values()) : List.of(Filter.ALL, Filter.HANGOUT, Filter.RECOGNIZED, Filter.MISSING),
                        state().filter, CatalogPanel::option, value -> { state().filter = value; rebuild(true); }));
        int cameraX = x + w - INSET - 113;
        button(cameraX, y + TOOLBAR_TOP, 18, TOOLBAR_HEIGHT, Component.literal("−"), () -> false, b -> zoom(-1));
        button(cameraX + 56, y + TOOLBAR_TOP, 18, TOOLBAR_HEIGHT, Component.literal("+"), () -> false, b -> zoom(1));
        button(cameraX + 79, y + TOOLBAR_TOP, 34, TOOLBAR_HEIGHT, tr("fit"), () -> false, b -> graph.fit());
        widgets.add(graph); widgets.add(inspector);
        int actionY = bodyY + bodyH - 26;
        pinButton = button(inspector.getX() + (detailsW - 156) / 2, actionY, 156, 20, tr("pin_requirements"),
                () -> BuildingPinClientStore.isPinned(state().selected), b -> togglePin());
        previousVariant = button(inspector.getX() + 8, inspector.variantControlsY(), 22, 18, Component.literal("‹"), () -> false, b -> inspector.changeVariant(-1));
        nextVariant = button(inspector.getX() + detailsW - 30, inspector.variantControlsY(), 22, 18, Component.literal("›"), () -> false, b -> inspector.changeVariant(1));
        refresh();
        for (var widget : widgets) {
            if (widget == inspector) continue;
            var input = new ScaledWidget(widget, uiScale); inputWidgets.put(widget, input); register.accept(input);
        }
        // Child controls inside the inspector must get the first chance to consume a click.
        var inspectorInput = new ScaledWidget(inspector, uiScale);
        inputWidgets.put(inspector, inspectorInput); register.accept(inspectorInput);
        if (pendingFocus != null) {
            state().selected = pendingFocus; pendingFocus = null; select(state().selected); graph.revealSelected();
        }
    }
    private CatalogButton button(int x, int y, int width, int height, Component label,
                                 java.util.function.BooleanSupplier selected, Button.OnPress action) {
        CatalogButton button = new CatalogButton(x, y, width, height, label, selected, action); widgets.add(button); return button;
    }
    private <T> void choices(Button button, List<T> values, T current, Function<T, Component> labels, Consumer<T> action) {
        menu.open(font, values.stream().map(labels).toList(), values.indexOf(current), button.getX(), button.getY() + button.getHeight(),
                virtualWidth, virtualHeight, index -> action.accept(values.get(index)));
    }
    private List<Grouping> groupings() { return tab == 0 ? List.of(Grouping.values()) : List.of(Grouping.MOD, Grouping.SPIRIT, Grouping.HANGOUT, Grouping.STATUS); }
    private Map<String, Integer> points() {
        return village == null ? Map.of() : ClientVillageSpiritStore.get(village.getId()).map(p -> p.perSpirit()).orElse(Map.of());
    }
    private boolean hasPoints() { return village != null && ClientVillageSpiritStore.get(village.getId()).isPresent(); }
    private List<Sort> sorts() { return state().grouping == Grouping.SPIRIT && hasPoints()
            ? List.of(Sort.values()) : List.of(Sort.NAME, Sort.RECOGNIZED, Sort.ENTRIES); }
    private void switchTab(int next) {
        if (next == tab) return;
        tab = next; rebuildHost.run();
    }
    public void focusBuilding(String id) {
        var decoration = com.aetherianartificer.townstead.spirit.DecorationSpiritContributions.decorationId(id);
        tab = decoration == null ? 0 : 1;
        String selected = decoration == null ? id : decoration.toString();
        state().selected = selected; state().query = ""; state().filter = Filter.ALL; pendingFocus = selected;
    }
    private void refresh() {
        var client = Minecraft.getInstance();
        CatalogDataLoader.refreshClientTheme(client.getResourceManager());
        revision = CatalogDataLoader.syncRevision(); resourceManager = client.getResourceManager(); language = Language.getInstance();
        pinned = pinnedId();
        var texture = CatalogDataLoader.theme().backgroundTexture().orElse(
                net.minecraft.resources.ResourceLocation.tryParse("townstead:textures/gui/catalog_background.png"));
        background = client.getResourceManager().getResource(texture).isPresent() ? texture : null;
        spiritSnapshot = village == null ? null : ClientVillageSpiritStore.get(village.getId()).orElse(null);
        theme = CatalogVillageTheme.resolve(CatalogDataLoader.theme(), village == null ? null : ClientVillageSpiritStore.get(village.getId()).orElse(null));
        graph.theme(theme); inspector.theme(theme); menu.theme(theme);
        entries = tab == 0 ? CatalogEntries.buildings(village) : CatalogEntries.decorations();
        Map<String, CatalogEntries.Display> map = new LinkedHashMap<>();
        for (var entry : entries) map.put(entry.entry().id(), entry);
        byId = Map.copyOf(map);
        if (!byId.containsKey(state().selected)) state().selected = entries.isEmpty() ? "" : entries.get(0).entry().id();
        rebuild(false);
        if (!state().positioned && !entries.isEmpty()) {
            // Opening a large modpack at fit-all scale would make every label unreadable.
            state().camera.zoom = 1; state().camera.panX = 0; state().camera.panY = 0;
            if (pendingFocus == null) layout.nodes().stream().filter(Node::match).findFirst().ifPresent(n -> select(n.entry().id()));
            graph.revealSelected(); state().positioned = true;
        }
        dirty = false;
    }
    private String sectorLabel(String id) {
        if (id.equals("~core")) return tr("group.core").getString();
        return SpiritRegistry.get(id).map(s -> Component.translatable(s.displayKey()).getString())
                .orElseGet(() -> tr("sector." + (id.equals("~unclassified") ? "unclassified" : id)).getString());
    }
    private void rebuild(boolean reveal) {
        if (graph == null) return;
        if (!sorts().contains(state().order().key)) state().order().key = Sort.NAME;
        layout = CatalogGraphLayout.build(entries.stream().map(CatalogEntries.Display::entry).toList(),
                state().grouping, state().order().key, state().order().descending, state().query, state().filter,
                points(), this::sectorLabel, graphW, bodyH);
        graph.content(layout, byId);
        inspector.select(layout.matches() == 0 ? null : byId.get(state().selected));
        if (reveal) {
            boolean visible = layout.nodes().stream().anyMatch(n -> n.entry().id().equals(state().selected) && n.match());
            if (!visible) layout.nodes().stream().filter(Node::match).findFirst().ifPresent(n -> select(n.entry().id()));
            graph.revealSelected();
        }
        label(groupingButton, tr("group_by", option(state().grouping)));
        label(sortButton, tr("sort_groups", option(state().order().key)));
        label(filterButton, tr("filter_by", option(state().filter)));
        label(directionButton, Component.literal(state().order().descending ? "↓" : "↑"));
        // Reserve the full catalog count so typing doesn't move the search field's right edge.
        int countWidth = font.width(tr("results", entries.size()));
        clearSearch.setX(x + w - INSET - countWidth - 8 - clearSearch.getWidth());
        search.setWidth(clearSearch.getX() - 4 - search.getX());
        updateActions();
    }
    private void label(Button button, Component label) { button.setMessage(label); }
    private void select(String id) { state().selected = id; inspector.select(byId.get(id)); updateActions(); }
    private void updateActions() {
        boolean selected = layout.matches() > 0 && byId.containsKey(state().selected);
        pinButton.visible = tab == 0; pinButton.active = selected;
        pinButton.setMessage(tr(BuildingPinClientStore.isPinned(state().selected) ? "unpin" : "pin_requirements"));
        previousVariant.visible = nextVariant.visible = tab == 1 && inspector.variantCount() > 1;
    }
    private void togglePin() {
        if (tab != 0 || !byId.containsKey(state().selected)) return;
        String next = BuildingPinClientStore.isPinned(state().selected) ? "" : state().selected;
        BuildingPinClientStore.optimistic(next);
        var payload = new com.aetherianartificer.townstead.building.pin.BuildingPinSetC2SPayload(next);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
        refresh();
    }
    private void zoom(int amount) { state().camera.zoomAt(amount, graphW / 2.0, bodyH / 2.0); }
    private String pinnedId() {
        var current = BuildingPinClientStore.current();
        return current.active() ? current.buildingType() : "";
    }
    public void render(GuiGraphics g, int mx, int my, float tick, Village village) {
        var client = Minecraft.getInstance();
        Object nextSpirits = village == null ? null : ClientVillageSpiritStore.get(village.getId()).orElse(null);
        dirty |= this.village != village || revision != CatalogDataLoader.syncRevision()
                || language != Language.getInstance() || resourceManager != client.getResourceManager()
                || spiritSnapshot != nextSpirits || !pinned.equals(pinnedId());
        this.village = village;
        if (dirty && !graph.dragging()) refresh();
        mx = (int) (mx / uiScale); my = (int) (my / uiScale);
        g.pose().pushPose(); g.pose().scale(uiScale, uiScale, 1);
        g.fill(x, y, x + w, y + h, theme.frameColor());
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, theme.panelColor());
        if (background != null) g.blit(background, x + 1, y + 1, w - 2, h - 2, 0, 0, 640, 380, 640, 380);
        g.fill(x + 1, y + 1, x + w - 1, y + TITLE_BOTTOM, theme.titleBarColor());
        g.drawCenteredString(font, Component.translatable("townstead.configuration.catalog"), x + w / 2,
                y + 1 + (TITLE_BOTTOM - 1 - 8) / 2, 0xF2ECD8);
        g.fill(x + INSET, y + SEARCH_RULE, x + w - INSET, y + SEARCH_RULE + 1, theme.borderColor());
        Component count = tr("results", layout.matches());
        g.drawString(font, count, x + w - INSET - font.width(count), y + SEARCH_TOP + (SEARCH_HEIGHT - 8) / 2, 0xADBEAF, false);
        g.drawCenteredString(font, Math.round(state().camera.zoom * 100) + "%", x + w - 84,
                y + TOOLBAR_TOP + (TOOLBAR_HEIGHT - 8) / 2, 0xD8DEC9);
        int legendY = bodyY + bodyH + GAP + 4;
        int hangoutX = x + INSET + 1;
        CatalogBadgeRenderer.hangoutLabel(g, font, tr("hangout").getString(), hangoutX, legendY, 0xADBEAF);
        int pinX = hangoutX + 28 + font.width(tr("hangout"));
        if (tab == 0) CatalogBadgeRenderer.pinLabel(g, font, tr("filter.pinned").getString(), pinX, legendY, 0xDACB9F);
        String help = tr("navigation_hint").getString();
        g.drawString(font, help, x + w - INSET - font.width(help), legendY, 0xADBEAF, false);
        updateActions();
        for (var input : inputWidgets.values()) input.sync();
        for (var widget : widgets) if (widget.visible) widget.render(g, mx, my, tick);
        if (tab == 1 && inspector.variantCount() > 1) {
            String variant = tr("variant_short", inspector.variant() + 1, inspector.variantCount()).getString();
            g.drawCenteredString(font, variant, inspector.getX() + inspector.getWidth() / 2, inspector.variantControlsY() + 5, 0xD8DEC9);
        }
        if (!menu.open()) {
            graph.renderTooltip(g, mx, my);
            inspector.renderTooltip(g, mx, my);
            for (var widget : widgets) if (widget instanceof CatalogButton && widget.visible && widget.isMouseOver(mx, my)) {
                Component hint = widget == directionButton ? tr(state().order().descending ? "descending" : "ascending")
                        : widget == previousVariant ? tr("previous_variant") : widget == nextVariant ? tr("next_variant")
                        : widget.getMessage().getString().equals("×") ? tr("clear") : widget.getMessage();
                g.renderTooltip(font, hint, mx, my);
                break;
            }
        }
        menu.render(g, font, mx, my);
        g.pose().popPose();
    }
    public boolean mouseClicked(double x, double y, int button) { return menu.click(x / uiScale, y / uiScale, button); }
    public boolean scroll(double x, double y, double amount) {
        if (menu.open()) return true;
        return inspector.scroll(x / uiScale, y / uiScale, amount) || graph.scroll(x / uiScale, y / uiScale, amount);
    }
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (menu.key(key)) return true;
        if (key == GLFW.GLFW_KEY_F && Screen.hasControlDown()) { host.setFocused(inputWidgets.get(search)); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE && search.isFocused()) {
            if (!search.getValue().isEmpty()) search.setValue("");
            else { search.setFocused(false); host.setFocused(inputWidgets.get(graph)); }
            return true;
        }
        return false;
    }
}
