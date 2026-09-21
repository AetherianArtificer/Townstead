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
        graphW = w - detailsW - 26;
        bodyY = y + 78; bodyH = h - 100;
        graph = new CatalogGraphWidget(font, x + 8, bodyY, graphW, bodyH, state().camera,
                () -> state().selected, this::select);
        graph.uiScale(uiScale);
        inspector = new CatalogInspectorWidget(font, x + graphW + 16, bodyY, detailsW, bodyH);
        inspector.uiScale(uiScale);
        button(x + 5, y + 3, 60, 16, Component.translatable("gui.back"), () -> false, b -> back.run());
        Component[] tabLabels = {Component.translatable("townstead.decorations.buildings_button"),
                Component.translatable("townstead.decorations.button")};
        int tabWidth = Math.max(font.width(tabLabels[0]), font.width(tabLabels[1])) + 16;
        var tabRects = Controls.tabLayout(x + 8, y + 30, tabWidth * 2, 2);
        for (int i = 0; i < tabLabels.length; i++) {
            int index = i;
            widgets.add(new TabButton(tabRects[i], tabLabels[i], () -> tab == index, b -> switchTab(index)));
        }
        search = new EditBox(font, tabRects[1].right() + 10, y + 27, 100, 20, tr("search"));
        search.setMaxLength(128); search.setHint(tr("search")); search.setValue(state().query);
        search.setResponder(value -> { state().query = value; rebuild(true); }); widgets.add(search);
        clearSearch = button(search.getX() + search.getWidth() + 4, y + 27, 20, 20, Component.literal("×"), () -> false, b -> {
            state().filter = Filter.ALL; search.setValue(""); rebuild(true);
        });
        groupingButton = button(x + 8, y + 54, 166, 18, Component.empty(), () -> false, b ->
                choices(b, groupings(), state().grouping, CatalogPanel::option, value -> {
                    state().grouping = value; rebuild(true);
                }));
        sortButton = button(x + 179, y + 54, 126, 18, Component.empty(), () -> false, b ->
                choices(b, sorts(), state().order().key, CatalogPanel::option, value -> {
                    state().order().key = value; rebuild(true);
                }));
        directionButton = button(x + 308, y + 54, 18, 18, Component.empty(), () -> false, b -> {
            state().order().descending = !state().order().descending; rebuild(true);
        });
        filterButton = button(x + 331, y + 54, 130, 18, Component.empty(), () -> state().filter != Filter.ALL, b ->
                choices(b, tab == 0 ? List.of(Filter.values()) : List.of(Filter.ALL, Filter.HANGOUT, Filter.RECOGNIZED, Filter.MISSING),
                        state().filter, CatalogPanel::option, value -> { state().filter = value; rebuild(true); }));
        int cameraX = x + w - 121;
        button(cameraX, y + 54, 18, 18, Component.literal("−"), () -> false, b -> zoom(-1));
        button(cameraX + 56, y + 54, 18, 18, Component.literal("+"), () -> false, b -> zoom(1));
        button(cameraX + 79, y + 54, 34, 18, tr("fit"), () -> false, b -> graph.fit());
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
    private List<Grouping> groupings() { return tab == 0 ? List.of(Grouping.values()) : List.of(Grouping.SPIRIT, Grouping.HANGOUT, Grouping.STATUS); }
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
        graph.theme(theme); inspector.theme(theme);
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
        clearSearch.setX(x + w - 8 - countWidth - 8 - clearSearch.getWidth());
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
        g.fill(x + 3, y + 3, x + w - 3, y + 20, theme.titleBarColor());
        g.drawCenteredString(font, Component.translatable("townstead.configuration.catalog"), x + w / 2, y + 7, 0xF2ECD8);
        g.fill(x + 4, y + 50, x + w - 4, y + 51, theme.borderColor());
        Component count = tr("results", layout.matches());
        g.drawString(font, count, x + w - 8 - font.width(count), y + 33, 0xADBEAF, false);
        g.drawCenteredString(font, Math.round(state().camera.zoom * 100) + "%", x + w - 84, y + 59, 0xD8DEC9);
        int legendY = y + h - 14;
        int hangoutX = x + 9;
        CatalogBadgeRenderer.hangoutLabel(g, font, tr("hangout").getString(), hangoutX, legendY, 0xADBEAF);
        int pinX = hangoutX + 28 + font.width(tr("hangout"));
        if (tab == 0) g.drawString(font, "◆ " + tr("filter.pinned").getString(), pinX, legendY, 0xDACB9F, false);
        String help = tr("navigation_hint").getString();
        g.drawString(font, help, x + w - 9 - font.width(help), legendY, 0xADBEAF, false);
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
