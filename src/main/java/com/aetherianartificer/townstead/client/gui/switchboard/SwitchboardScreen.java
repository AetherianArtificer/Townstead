package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.switchboard.SettingIndex;
import com.aetherianartificer.townstead.switchboard.SwitchboardOpenS2CPayload;
import com.aetherianartificer.townstead.switchboard.Systems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * World Setup, laid out like Create World. General holds a Play Style and the broad dials, each with a
 * "..." for its details; Peoples holds Roots and Cultures; More leads to presets, every setting, and
 * this computer's own settings.
 */
public class SwitchboardScreen extends MenuBackgroundScreen {
    private static final int COLUMN = 310;
    private static final int MORE_WIDTH = 20;
    private static final int VILLAGE = 0;
    private static final int PEOPLES = 1;
    private static final int MORE = 2;

    private final SwitchboardModel model;
    private final TabManager tabManager = new TabManager(w -> {}, w -> {});
    private final List<Page> pages = List.of(new Page("townstead.switchboard.tab.village"),
            new Page("townstead.switchboard.tab.peoples"), new Page("townstead.switchboard.tab.more"));
    private final List<Hint> hints = new ArrayList<>();
    private int tab;

    @Nullable private static SwitchboardOpenS2CPayload pending;

    private record Hint(Component text, int y) {}

    private SwitchboardScreen(SwitchboardOpenS2CPayload payload) {
        super(Component.translatable("townstead.switchboard.title"));
        this.model = new SwitchboardModel(payload);
    }

    /** Opens once the world is on screen; Destiny steps aside and reopens itself afterwards. */
    public static void open(SwitchboardOpenS2CPayload payload) {
        if (!payload.firstJoin()) {
            Minecraft.getInstance().setScreen(new SwitchboardScreen(payload));
            return;
        }
        pending = payload;
        tickPending();
    }

    public static void tickPending() {
        Minecraft mc = Minecraft.getInstance();
        if (pending == null) return;
        if (mc.level == null || mc.player == null) {
            if (mc.getConnection() == null) pending = null;
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof net.conczin.mca.client.gui.DestinyScreen)) return;
        SwitchboardOpenS2CPayload payload = pending;
        pending = null;
        mc.setScreen(new SwitchboardScreen(payload));
    }

    @Override
    protected void init() {
        hints.clear();
        List<Page> shown = new ArrayList<>();
        shown.add(pages.get(VILLAGE));
        if (Boolean.TRUE.equals(model.value(Systems.key(Systems.ROOTS)))) shown.add(pages.get(PEOPLES));
        shown.add(pages.get(MORE));
        if (!shown.contains(pages.get(tab))) tab = VILLAGE;
        TabNavigationBar bar = TabNavigationBar.builder(tabManager, width).addTabs(shown.toArray(Tab[]::new)).build();
        addRenderableWidget(bar);
        bar.selectTab(shown.indexOf(pages.get(tab)), false);
        bar.arrangeElements();
        int y = bar.getRectangle().bottom() + 16;

        switch (tab) {
            case PEOPLES -> initPeoples(y);
            case MORE -> initMore(y);
            default -> initVillage(y);
        }

        if (model.firstJoin) {
            addRenderableWidget(Button.builder(Component.translatable("townstead.switchboard.continue"), b -> save())
                    .bounds(width / 2 - 100, height - 28, 200, 20).build());
        } else {
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save())
                    .bounds(width / 2 - 154, height - 28, 150, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                    .bounds(width / 2 + 4, height - 28, 150, 20).build());
        }
    }

    private int left() {
        return width / 2 - COLUMN / 2;
    }

    private void initVillage(int y) {
        List<Dials.Style> styles = Dials.styles();
        int style = Dials.style(model);
        Component styleName = style >= 0 ? styles.get(style).name() : custom();
        addRenderableWidget(Button.builder(Component.translatable("townstead.switchboard.dial.value",
                        Component.translatable("townstead.switchboard.dial.style"), styleName), b -> {
                    Dials.applyStyle(model, styles.get((style + 1) % styles.size()));
                    rebuildWidgets();
                })
                .bounds(left(), y, COLUMN, 20).build());
        y += 24;
        Component hint = style >= 0 ? styles.get(style).hint()
                : Component.translatable("townstead.switchboard.style.custom.hint");
        hints.add(new Hint(hint, y));
        y += 10 * font.split(hint, COLUMN).size() + 10;

        // Two columns, like Create World's game options, so every dial fits on one screen.
        int half = (COLUMN - 8) / 2;
        List<Dials.Dial> dials = Dials.all();
        for (int i = 0; i < dials.size(); i++) {
            Dials.Dial dial = dials.get(i);
            int x = left() + (i % 2) * (half + 8);
            int rowY = y + (i / 2) * 24;
            int level = dial.level(model);
            Component value = level >= 0 ? dial.levels().get(level).name() : custom();
            Button button = Button.builder(Component.translatable("townstead.switchboard.dial.value", dial.label(), value),
                            b -> {
                                dial.set(model, (level + 1) % dial.levels().size());
                                rebuildWidgets();
                            })
                    .bounds(x, rowY, half - MORE_WIDTH - 4, 20).build();
            if (dial.locked(model)) lock(button);
            addRenderableWidget(button);
            Button more = Button.builder(Component.literal("..."), b -> openDetails(dial))
                    .bounds(x + half - MORE_WIDTH, rowY, MORE_WIDTH, 20).build();
            more.setTooltip(Tooltip.create(Component.translatable("townstead.switchboard.customize", dial.label())));
            addRenderableWidget(more);
        }
    }

    private void openDetails(Dials.Dial dial) {
        if (dial.id().equals(Dials.PEOPLES) && Boolean.TRUE.equals(model.value(Systems.key(Systems.ROOTS)))) {
            tab = PEOPLES;
            rebuildWidgets();
            return;
        }
        Predicate<String> keys = key -> dial.keys().contains(key) || dial.details().test(key);
        minecraft.setScreen(new SettingsListScreen(this, model, SettingsListScreen.Kind.CATEGORY, dial.label(),
                Component.translatable("townstead.switchboard.details." + dial.id()), keys));
    }

    private void initPeoples(int y) {
        y = worldToggle(SettingIndex.keyOf(TownsteadConfig.ALLOW_ROOT_CHOICE_IN_DESTINY), y);
        y = link("townstead.switchboard.peoples.roots", "townstead.switchboard.peoples.roots.hint", y, true,
                () -> new SettingsListScreen(this, model, SettingsListScreen.Kind.ROOTS,
                        Component.translatable("townstead.switchboard.tab.roots"),
                        Component.translatable("townstead.switchboard.intro.roots"), null));
        boolean cultures = Boolean.TRUE.equals(model.value(Systems.key(Systems.CULTURES)));
        y = link("townstead.switchboard.peoples.cultures", cultures ? "townstead.switchboard.peoples.cultures.hint"
                        : "townstead.switchboard.peoples.cultures.off", y, cultures,
                () -> new SettingsListScreen(this, model, SettingsListScreen.Kind.CULTURES,
                        Component.translatable("townstead.switchboard.tab.cultures"),
                        Component.translatable("townstead.switchboard.intro.cultures"), null));
        link("townstead.switchboard.peoples.names", "townstead.switchboard.peoples.names.hint", y, true,
                () -> new SettingsListScreen(this, model, SettingsListScreen.Kind.CATEGORY,
                        Component.translatable("townstead.switchboard.peoples.names.title"),
                        Component.translatable("townstead.switchboard.details.peoples"),
                        Dials.get(Dials.PEOPLES).details()));
    }

    private void initMore(int y) {
        y = link("townstead.switchboard.presets", null, y, true, () -> new PresetsScreen(this, model));
        y = link("townstead.switchboard.more.all", "townstead.switchboard.more.all.hint", y, true,
                () -> new SettingsListScreen(this, model, SettingsListScreen.Kind.ALL,
                        Component.translatable("townstead.switchboard.more.all.title"),
                        Component.translatable(model.firstJoin ? "townstead.switchboard.intro.first"
                                : "townstead.switchboard.intro.world"), null));
        link("townstead.switchboard.more.client", "townstead.switchboard.more.client.hint", y, true,
                () -> new SettingsListScreen(this, model, SettingsListScreen.Kind.CLIENT,
                        Component.translatable("townstead.switchboard.tab.device"),
                        Component.translatable("townstead.switchboard.intro.device"), null));
    }

    /** A full-width button that opens another screen, with an optional line of explanation under it. */
    private int link(String labelKey, @Nullable String hintKey, int y, boolean active, Supplier<Screen> next) {
        Button button = Button.builder(Component.translatable(labelKey), b -> minecraft.setScreen(next.get()))
                .bounds(left(), y, COLUMN, 20).build();
        button.active = active;
        addRenderableWidget(button);
        y += 24;
        if (hintKey != null) {
            Component hint = Component.translatable(hintKey);
            hints.add(new Hint(hint, y));
            y += 10 * font.split(hint, COLUMN).size() + 8;
        }
        return y;
    }

    /** A full-width "Label: ON" button for a world setting. */
    private int worldToggle(@Nullable String key, int y) {
        SettingIndex.Entry entry = key == null ? null : SettingIndex.get(key);
        if (entry == null) return y;
        boolean value = Boolean.TRUE.equals(model.value(key));
        CycleButton<Boolean> toggle = CycleButton.booleanBuilder(SettingControls.on(), SettingControls.off())
                .withInitialValue(value)
                .create(left(), y, COLUMN, 20, SettingLabels.label(entry), (b, v) -> model.set(key, v));
        if (model.isLocked(key)) lock(toggle);
        addRenderableWidget(toggle);
        return y + 30;
    }

    private static Component custom() {
        return Component.translatable("townstead.switchboard.custom").withStyle(ChatFormatting.YELLOW);
    }

    private static void lock(AbstractWidget control) {
        control.active = false;
        control.setTooltip(Tooltip.create(Component.translatable("townstead.switchboard.locked")));
    }

    private void save() {
        model.save();
        minecraft.setScreen(null);
    }

    @Override
    public void tick() {
        super.tick();
        int current = pages.indexOf(tabManager.getCurrentTab());
        if (current >= 0 && current != tab) {
            tab = current;
            rebuildWidgets();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !model.firstJoin;
    }

    //? if >=1.21 {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawHints(g);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        drawHints(g);
    }
    *///?}

    private void drawHints(GuiGraphics g) {
        for (Hint hint : hints) {
            int y = hint.y();
            for (FormattedCharSequence line : font.split(hint.text(), COLUMN)) {
                g.drawCenteredString(font, line, width / 2, y, 0xA0A0A0);
                y += 10;
            }
        }
    }

    /** A tab with no widgets of its own; the screen swaps its content when the selection changes. */
    private record Page(String titleKey) implements Tab {
        @Override public Component getTabTitle() { return Component.translatable(titleKey); }
        @Override public void visitChildren(Consumer<AbstractWidget> consumer) {}
        @Override public void doLayout(ScreenRectangle rectangle) {}
    }
}
