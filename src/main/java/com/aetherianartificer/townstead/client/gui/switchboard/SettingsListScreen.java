package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.switchboard.SettingIndex;
import com.aetherianartificer.townstead.switchboard.WorldKeys;
import com.aetherianartificer.townstead.switchboard.WorldKeys.RootState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * One scrolling list of settings: All Settings, Client Settings, the details behind a Village dial,
 * Roots, or Cultures. Edits land in the shared model; Done returns to the screen that opened it.
 */
final class SettingsListScreen extends MenuBackgroundScreen {
    enum Kind { ALL, CLIENT, CATEGORY, ROOTS, CULTURES }

    private static final int CONTROL_WIDTH = 140;
    /** Roots and Cultures rows share two columns: who may have it, and its spawn rate. */
    private static final int WHO_WIDTH = 140;
    private static final int RATE_WIDTH = 90;

    private final Screen parent;
    private final SwitchboardModel model;
    private final Kind kind;
    @Nullable private final Predicate<String> keys;
    private final Component intro;
    private String filter = "";
    private int groupBy = 1;
    private boolean dirty;
    @Nullable private SettingList list;

    SettingsListScreen(Screen parent, SwitchboardModel model, Kind kind, Component title, Component intro,
                       @Nullable Predicate<String> keys) {
        super(title);
        this.parent = parent;
        this.model = model;
        this.kind = kind;
        this.intro = intro;
        this.keys = keys;
    }

    @Override
    protected void init() {
        boolean grouped = kind == Kind.ROOTS;
        EditBox search = new EditBox(font, width / 2 - 155, 40, grouped ? 200 : 310, 20,
                Component.translatable("townstead.switchboard.search"));
        search.setHint(Component.translatable("townstead.switchboard.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(filter);
        search.setResponder(text -> {
            filter = text;
            fill(false);
        });
        addRenderableWidget(search);
        if (grouped) {
            addRenderableWidget(CycleButton.<Integer>builder(i -> Component.translatable(
                            "townstead.switchboard.dimension." + WorldKeys.DIMENSIONS.get(i)))
                    .withValues(0, 1, 2, 3).withInitialValue(groupBy)
                    .create(width / 2 + 49, 40, 106, 20, Component.translatable("townstead.switchboard.groupby"),
                            (b, v) -> {
                                groupBy = v;
                                fill(false);
                            }));
        }
        list = new SettingList(minecraft, width, height - 36 - 66, 66,
                kind == Kind.ROOTS || kind == Kind.CULTURES ? 400 : 330);
        addRenderableWidget(list);
        fill(false);

        boolean packDefaults = kind != Kind.CLIENT && !model.pack.isEmpty();
        addRenderableWidget(Button.builder(Component.translatable(packDefaults
                        ? "townstead.switchboard.defaults.modpack" : "townstead.switchboard.defaults"), b -> reset())
                .bounds(width / 2 - 154, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 + 4, height - 28, 150, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        super.tick();
        if (dirty) {
            dirty = false;
            fill(true);
        }
    }

    //? if >=1.21 {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawHeader(g);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        drawHeader(g);
    }
    *///?}

    private void drawHeader(GuiGraphics g) {
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, intro.copy().withStyle(kind == Kind.CLIENT ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                width / 2, 26, 0xFFFFFF);
    }

    private void reset() {
        switch (kind) {
            case CLIENT -> model.resetDevice();
            case ROOTS -> model.resetWorld(key -> key.startsWith("roots."));
            case CULTURES -> model.resetWorld(key -> key.startsWith("cultures."));
            case CATEGORY -> model.resetWorld(key -> keys != null && keys.test(key));
            default -> model.resetWorld(key -> SettingIndex.get(key) != null && !key.startsWith("roots."));
        }
        fill(false);
    }

    // ── Rows ──────────────────────────────────────────────────────────────

    private void fill(boolean keepScroll) {
        if (list == null) return;
        double scroll = list.getScrollAmount();
        list.clear();
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        switch (kind) {
            case ROOTS -> fillRoots(needle);
            case CULTURES -> fillCultures(needle);
            default -> fillSettings(needle);
        }
        if (list.isEmpty()) {
            String empty = kind == Kind.ROOTS ? "townstead.switchboard.empty.roots"
                    : kind == Kind.CULTURES ? "townstead.switchboard.empty.cultures" : "townstead.switchboard.empty";
            list.add(new SettingList.Header(Component.translatable(empty).withStyle(ChatFormatting.GRAY)));
        }
        list.setScrollAmount(keepScroll ? scroll : 0);
    }

    private void fillSettings(String needle) {
        boolean client = kind == Kind.CLIENT;
        String lastGroup = null;
        for (SettingIndex.Entry entry : client ? SettingIndex.client() : SettingIndex.all()) {
            if (kind == Kind.ALL && entry.key().startsWith("roots.")) continue;
            if (kind == Kind.CATEGORY && (keys == null || !keys.test(entry.key()))) continue;
            Component label = SettingLabels.label(entry);
            if (!matches(needle, label, entry.key())) continue;
            String group = entry.key().contains(".") ? entry.key().substring(0, entry.key().lastIndexOf('.')) : "";
            // Inside a category its own switches lead without a heading.
            boolean headed = !(kind == Kind.CATEGORY && group.equals("systems"));
            if (!group.equals(lastGroup)) {
                if (headed) list.add(new SettingList.Header(SettingLabels.groupLabel(group)));
                lastGroup = group;
            }
            list.add(settingRow(entry, label, client));
        }
    }

    SettingList.Setting settingRow(SettingIndex.Entry entry, Component label, boolean client) {
        Object current = client ? model.deviceValue(entry) : model.value(entry.key());
        List<AbstractWidget> controls = SettingControls.build(entry, current, label, CONTROL_WIDTH, v -> {
            if (client) model.setDevice(entry, v);
            else model.set(entry.key(), v);
        });
        boolean locked = !client && model.isLocked(entry.key());
        if (locked) controls.forEach(SettingsListScreen::lock);
        return new SettingList.Setting(this, label, SettingLabels.tooltip(entry, locked), controls, locked);
    }

    private void fillRoots(String needle) {
        SettingIndex.Entry choice = SettingIndex.get(SettingIndex.keyOf(TownsteadConfig.ALLOW_ROOT_CHOICE_IN_DESTINY));
        if (choice != null && needle.isEmpty()) list.add(settingRow(choice, SettingLabels.label(choice), false));

        String dimension = WorldKeys.DIMENSIONS.get(groupBy);
        if (!model.catalog.roots.isEmpty()) {
            list.add(columns("townstead.switchboard.column.who", "townstead.switchboard.column.rate.roots.tip"));
        }
        Map<String, List<ContentCatalog.RootRow>> groups = new LinkedHashMap<>();
        for (ContentCatalog.RootRow root : model.catalog.roots) {
            if (!matches(needle, root.name(), root.id())) continue;
            groups.computeIfAbsent(root.groups().getOrDefault(dimension, ""), k -> new ArrayList<>()).add(root);
        }
        List<String> order = new ArrayList<>(groups.keySet());
        order.sort(Comparator.comparing((String id) -> id.isEmpty() ? "￿"
                : model.catalog.groupName(dimension, id).getString(), String.CASE_INSENSITIVE_ORDER));
        for (String groupId : order) {
            List<ContentCatalog.RootRow> members = groups.get(groupId);
            if (groupId.isEmpty()) {
                list.add(new SettingList.Header(Component.translatable("townstead.switchboard.group.none."
                        + dimension).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW)));
            } else {
                list.add(groupRow(dimension, groupId, members.size()));
            }
            for (ContentCatalog.RootRow root : members) list.add(rootRow(root));
        }
    }

    private SettingList.Setting groupRow(String dimension, String groupId, int count) {
        String onKey = WorldKeys.groupOn(dimension, groupId);
        String rateKey = WorldKeys.groupRate(dimension, groupId);
        boolean on = (Boolean) model.value(onKey);
        CycleButton<Boolean> toggle = SettingControls.onOff(on, WHO_WIDTH,
                model.catalog.groupName(dimension, groupId), v -> content(onKey, v));
        RateSlider rate = new RateSlider(RATE_WIDTH, (Double) model.value(rateKey),
                r -> Component.translatable("townstead.switchboard.rate.all", r), r -> content(rateKey, r));
        rate.active = on;
        if (model.isLocked(onKey)) lock(toggle);
        if (model.isLocked(rateKey)) lock(rate);
        Component label = model.catalog.groupName(dimension, groupId).copy().withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
        Component tip = Component.translatable("townstead.switchboard.group.tip." + dimension, count);
        return new SettingList.Setting(this, label, tip, List.of(toggle, rate), false);
    }

    private SettingList.Setting rootRow(ContentCatalog.RootRow root) {
        String stateKey = WorldKeys.rootState(root.id());
        String rateKey = WorldKeys.rootRate(root.id());
        RootState state = (RootState) model.value(stateKey);
        String offGroup = null;
        double own = (Double) model.value(rateKey);
        double overall = own;
        for (Map.Entry<String, String> group : root.groups().entrySet()) {
            if (offGroup == null && !(Boolean) model.value(WorldKeys.groupOn(group.getKey(), group.getValue()))) {
                offGroup = group.getKey();
            }
            overall *= (Double) model.value(WorldKeys.groupRate(group.getKey(), group.getValue()));
        }
        boolean blocked = offGroup != null;
        List<AbstractWidget> stateControls = SettingControls.stepper(List.of((Object[]) RootState.values()), state,
                s -> Component.translatable("townstead.switchboard.root.state." + ((RootState) s).name().toLowerCase(Locale.ROOT)),
                root.name(), WHO_WIDTH, v -> content(stateKey, v));
        boolean spawns = !blocked && (state == RootState.EVERYONE || state == RootState.VILLAGERS
                || state == RootState.DISCOVERABLE);
        RateSlider rate = new RateSlider(RATE_WIDTH, own,
                r -> spawns ? r : Component.translatable("townstead.switchboard.rate.none"), r -> content(rateKey, r));
        rate.active = spawns;
        if (blocked || model.isLocked(stateKey)) stateControls.forEach(SettingsListScreen::lock);
        if (model.isLocked(rateKey)) lock(rate);

        Component tip = blocked
                ? Component.translatable("townstead.switchboard.root.group_off",
                        Component.translatable("townstead.switchboard.dimension." + offGroup))
                : Component.translatable("townstead.switchboard.root.state." + state.name().toLowerCase(Locale.ROOT) + ".tip");
        if (!blocked && state == RootState.DISCOVERABLE) {
            tip = tip.copy().append("\n").append(root.discoveredBy() == null
                    ? Component.translatable("townstead.switchboard.root.undiscovered")
                    : Component.translatable("townstead.switchboard.root.discovered_by", root.discoveredBy()));
        }
        if (spawns && Math.abs(overall - own) > 1e-9) {
            tip = tip.copy().append("\n").append(Component.translatable("townstead.switchboard.root.overall",
                    RateSlider.format(overall)));
        }
        List<AbstractWidget> controls = new ArrayList<>(stateControls);
        controls.add(rate);
        return new SettingList.Setting(this, Component.literal("  ").append(root.name()), tip, controls,
                blocked || state == RootState.OFF);
    }

    private void fillCultures(String needle) {
        if (!model.catalog.cultures.isEmpty()) {
            list.add(columns("townstead.switchboard.column.allowed", "townstead.switchboard.column.rate.cultures.tip"));
        }
        String lastPack = null;
        for (ContentCatalog.CultureRow culture : model.catalog.cultures) {
            if (!matches(needle, culture.name(), culture.id())) continue;
            String packId = culture.id().contains(":") ? culture.id().substring(0, culture.id().indexOf(':')) : "";
            if (!packId.equals(lastPack)) {
                list.add(new SettingList.Header(Component.literal(SettingLabels.pretty(packId))
                        .withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW)));
                lastPack = packId;
            }
            String onKey = WorldKeys.cultureOn(culture.id());
            String rateKey = WorldKeys.cultureRate(culture.id());
            boolean on = (Boolean) model.value(onKey);
            CycleButton<Boolean> toggle = SettingControls.onOff(on, WHO_WIDTH, culture.name(), v -> content(onKey, v));
            RateSlider rate = new RateSlider(RATE_WIDTH, (Double) model.value(rateKey), r -> r, r -> content(rateKey, r));
            rate.active = on;
            if (model.isLocked(onKey)) lock(toggle);
            if (model.isLocked(rateKey)) lock(rate);
            list.add(new SettingList.Setting(this, culture.name(),
                    Component.translatable("townstead.switchboard.culture.tip"), List.of(toggle, rate), !on));
        }
    }

    private SettingList.Columns columns(String firstKey, String rateTipKey) {
        return new SettingList.Columns(this, List.of(
                new SettingList.Columns.Column(Component.translatable(firstKey), WHO_WIDTH, null),
                new SettingList.Columns.Column(Component.translatable("townstead.switchboard.column.rate"), RATE_WIDTH,
                        Component.translatable(rateTipKey))));
    }

    /** Sets a Root or culture value; other rows may depend on it, so the list redraws next tick. */
    private void content(String key, Object value) {
        model.set(key, value);
        dirty = true;
    }

    private static boolean matches(String needle, Component label, String id) {
        return needle.isEmpty() || label.getString().toLowerCase(Locale.ROOT).contains(needle)
                || id.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static void lock(AbstractWidget control) {
        control.active = false;
        control.setTooltip(Tooltip.create(Component.translatable("townstead.switchboard.locked")));
    }
}
