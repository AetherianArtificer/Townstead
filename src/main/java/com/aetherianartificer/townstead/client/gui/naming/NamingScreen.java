package com.aetherianartificer.townstead.client.gui.naming;

import com.aetherianartificer.townstead.client.gui.root.RootPanel;
import com.aetherianartificer.townstead.client.gui.root.ScrollPane;
import com.aetherianartificer.townstead.client.naming.NameSyncClient;
import com.aetherianartificer.townstead.client.naming.NamingCatalogClient;
import com.aetherianartificer.townstead.naming.NameClientStore;
import com.aetherianartificer.townstead.naming.NameParts;
import com.aetherianartificer.townstead.naming.NamingCatalogS2CPayload;
import com.aetherianartificer.townstead.naming.NamingTradition;
import com.aetherianartificer.townstead.villager.TownsteadEditorCommitPayload;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Picks a person's culture or naming tradition and shows the name it gives them. A pick goes to the
 * server at once and the server answers with the surname it settled, so what shows is what applies.
 */
public final class NamingScreen extends Screen {

    /** Who is being named. {@code entityId} is the real entity, which is what the name store keys on. */
    public record Target(int entityId, UUID uuid, Supplier<String> given, boolean player) {}

    private static final int PAD = 6;
    private static final int FIELD_H = 18;
    private static final int ROW_H = 14;
    private static final int HEADER_H = 13;
    private static final int RANDOM_W = 50;
    private static final int MUTED = 0xFFA0A0A0;
    private static final int HEADING = 0xFFE0C070;

    private final Screen parent;
    private final Target target;
    private final List<Row> rows = new ArrayList<>();
    private NamingCatalogS2CPayload.Entry selected;
    private String query = "";
    private String typed;
    private boolean dirty;

    private EditBox search;
    private EditBox surname;
    private Button random;
    private PickList list;
    private int detailX, detailY, detailW, detailH, captionY;

    public NamingScreen(Screen parent, Target target) {
        super(Component.translatable("gui.townstead.naming.title"));
        this.parent = parent;
        this.target = target;
    }

    private sealed interface Row permits Heading, Pick {}
    private record Heading(Component text) implements Row {}
    private record Pick(NamingCatalogS2CPayload.Entry entry, String label) implements Row {}

    @Override
    protected void init() {
        int panelW = Math.min(width - 16, 380);
        int panelH = Math.min(height - 16, 236);
        int left = (width - panelW) / 2;
        int top = (height - panelH) / 2;
        int listW = panelW * 11 / 20;

        search = new EditBox(font, left + 1, top + 1, listW - 2, FIELD_H - 2,
                Component.translatable("gui.townstead.naming.search"));
        search.setMaxLength(40);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            rebuild();
            list.resetToTop();
        });
        addRenderableWidget(search);

        list = addRenderableWidget(new PickList(left, top + FIELD_H + 2, listW, panelH - FIELD_H - 2));

        detailX = left + listW + PAD;
        detailY = top;
        detailW = panelW - listW - PAD;
        detailH = panelH;
        int inner = detailW - PAD * 2;

        int doneY = top + panelH - PAD - 20;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(detailX + PAD, doneY, inner, 20).build());

        int fieldY = doneY - PAD - FIELD_H;
        captionY = fieldY - 11;
        surname = new EditBox(font, detailX + PAD + 1, fieldY + 1, inner - RANDOM_W - 4, FIELD_H - 2,
                Component.translatable("gui.townstead.naming.surname"));
        surname.setMaxLength(TownsteadEditorCommitPayload.MAX_NAME_LENGTH);
        surname.setHint(Component.translatable("gui.townstead.naming.none").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        surname.setValue(typed != null ? typed : NameClientStore.family(target.entityId()));
        surname.setResponder(value -> {
            typed = value;
            dirty = !value.equals(NameClientStore.family(target.entityId()));
        });
        addRenderableWidget(surname);
        random = addRenderableWidget(Button.builder(Component.translatable("gui.button.random"), b -> roll())
                .bounds(detailX + PAD + inner - RANDOM_W, fieldY - 1, RANDOM_W, 20).build());

        selected = currentPick();
        rebuild();
        list.reveal(selected);
        refresh();
        setInitialFocus(search);
        NameSyncClient.watch(target.entityId(), this::onSynced);
    }

    /** A pick taken off the list later is still theirs until they choose another. */
    private NamingCatalogS2CPayload.Entry currentPick() {
        String culture = NameClientStore.culture(target.entityId());
        String tradition = NameClientStore.tradition(target.entityId());
        for (NamingCatalogS2CPayload.Entry entry : NamingCatalogClient.entries()) {
            if (culture.isEmpty() ? entry.culture().isEmpty() && entry.id().equals(tradition) : entry.culture().equals(culture)) {
                return entry;
            }
        }
        return new NamingCatalogS2CPayload.Entry(tradition, NameClientStore.rule(target.entityId()), "", culture);
    }

    private static boolean isNone(NamingCatalogS2CPayload.Entry entry) {
        return entry.id().isEmpty() && entry.culture().isEmpty();
    }

    private void rebuild() {
        rows.clear();
        List<Pick> cultures = new ArrayList<>();
        List<Pick> traditions = new ArrayList<>();
        for (NamingCatalogS2CPayload.Entry entry : NamingCatalogClient.entries()) {
            String label = NamingRow.pickName(entry);
            if (!NamingCatalogClient.matches(query, label, entry.id())) continue;
            (entry.culture().isEmpty() ? traditions : cultures).add(new Pick(entry, label));
        }
        Comparator<Pick> byLabel = Comparator.comparing(pick -> pick.label().toLowerCase(Locale.ROOT));
        cultures.sort(byLabel);
        traditions.sort(byLabel);

        // A villager always has a tradition, so only a player can have none.
        if (target.player() && query.isBlank()) {
            rows.add(new Pick(new NamingCatalogS2CPayload.Entry("", NamingTradition.FamilyType.NONE, "", ""),
                    Component.translatable("gui.townstead.naming.none").getString()));
        }
        if (!cultures.isEmpty()) {
            rows.add(new Heading(Component.translatable("gui.townstead.naming.cultures")));
            rows.addAll(cultures);
        }
        if (!traditions.isEmpty()) {
            if (!cultures.isEmpty()) rows.add(new Heading(Component.translatable("gui.townstead.naming.traditions")));
            rows.addAll(traditions);
        }
    }

    private void choose(NamingCatalogS2CPayload.Entry entry) {
        if (entry.equals(selected)) return;
        selected = entry;
        list.reveal(entry);
        send(TownsteadEditorCommitPayload.identityOnly(target.uuid(), entry.id(), entry.culture()));
    }

    private void roll() {
        dirty = false;
        typed = null;
        send(TownsteadEditorCommitPayload.familyNameOnly(target.uuid(), ""));
    }

    private void commit() {
        if (!dirty) return;
        dirty = false;
        send(TownsteadEditorCommitPayload.familyNameOnly(target.uuid(), surname.getValue()));
    }

    private void onSynced() {
        if (minecraft == null || minecraft.screen != this) return;
        if (NameClientStore.familyLocked(target.entityId())) dirty = false;
        if (!dirty) {
            surname.setValue(NameClientStore.family(target.entityId()));
            surname.setCursorPosition(0);
            surname.setHighlightPos(0);
            dirty = false;
            typed = null;
        }
        refresh();
    }

    private void refresh() {
        int id = target.entityId();
        boolean locked = NameClientStore.familyLocked(id);
        NamingTradition.FamilyType rule = selected == null ? NameClientStore.rule(id) : selected.rule();
        surname.setEditable(!locked);
        surname.setTooltip(Tooltip.create(Component.translatable(NamingRow.surnameTooltip(locked, rule))));
        random.active = !locked && rule == NamingTradition.FamilyType.INHERITED;
    }

    private void send(TownsteadEditorCommitPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    @Override
    public void onClose() {
        commit();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            step(key == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && getFocused() instanceof EditBox) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /** A click on the list keeps the search ready, so typing and the arrow keys carry on working. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (getFocused() == list) setFocused(search);
        return handled;
    }

    private void step(int direction) {
        List<NamingCatalogS2CPayload.Entry> picks = new ArrayList<>();
        for (Row row : rows) if (row instanceof Pick pick) picks.add(pick.entry());
        if (picks.isEmpty()) return;
        int at = picks.indexOf(selected);
        int next = at < 0 ? 0 : Math.max(0, Math.min(picks.size() - 1, at + direction));
        choose(picks.get(next));
    }

    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        RootPanel.draw(g, detailX, detailY, detailX + detailW, detailY + detailH);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawDetail(g);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        RootPanel.draw(g, detailX, detailY, detailX + detailW, detailY + detailH);
        super.render(g, mouseX, mouseY, partialTick);
        drawDetail(g);
    }
    *///?}

    private void drawDetail(GuiGraphics g) {
        if (search.getValue().isEmpty()) {
            g.drawString(font, search.getMessage(), search.getX() + 4, search.getY() + (search.getHeight() - 8) / 2, MUTED, false);
        }
        int centre = detailX + detailW / 2;
        int inner = detailW - PAD * 2;
        int id = target.entityId();

        // The name as it reads with whatever is in the field, typed or not.
        String name = NameParts.format(target.given().get(), surname.getValue(), NameClientStore.order(id));
        boolean large = font.width(name) * 2 <= inner;
        var nameLines = font.split(Component.literal(name), inner);
        List<FormattedCharSequence> whereLines = new ArrayList<>();
        boolean none = selected == null || isNone(selected);
        Component where = none ? Component.translatable("gui.townstead.naming.no_tradition")
                : Component.literal(NamingRow.pickName(selected));
        whereLines.addAll(font.split(where, inner));
        Component rule = none ? null : Component.translatable("gui.townstead.naming.rule."
                + selected.rule().name().toLowerCase(Locale.ROOT));
        List<FormattedCharSequence> ruleLines = rule == null ? List.of() : font.split(rule, inner);

        // Centred in the space above the surname field.
        int nameH = large ? 20 : nameLines.size() * 11;
        int blockH = nameH + 6 + whereLines.size() * 10 + (ruleLines.isEmpty() ? 0 : 2 + ruleLines.size() * 10);
        int y = detailY + Math.max(PAD, (captionY - detailY - blockH) / 2);
        if (large) {
            g.pose().pushPose();
            g.pose().translate(centre, y, 0);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.drawCenteredString(font, name, 0, 0, 0xFFFFFFFF);
            g.pose().popPose();
            y += nameH;
        } else {
            for (FormattedCharSequence line : nameLines) {
                g.drawCenteredString(font, line, centre, y, 0xFFFFFFFF);
                y += 11;
            }
        }
        y += 6;
        for (FormattedCharSequence line : whereLines) {
            g.drawCenteredString(font, line, centre, y, none ? MUTED : HEADING);
            y += 10;
        }
        y += 2;
        for (FormattedCharSequence line : ruleLines) {
            g.drawCenteredString(font, line, centre, y, MUTED);
            y += 10;
        }
        g.drawString(font, Component.translatable("gui.townstead.naming.surname"), detailX + PAD, captionY, MUTED, false);
    }

    /** The cultures and traditions, under their headings. */
    private final class PickList extends ScrollPane {
        private int contentTop;

        PickList(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        protected int renderContent(GuiGraphics g, int left, int top, int innerW, int innerTop, int innerBottom,
                                    int mouseX, int mouseY) {
            contentTop = top;
            int rowRight = left + innerW - 4;
            int y = top;
            if (rows.isEmpty()) {
                g.drawString(font, Component.translatable("gui.townstead.naming.no_matches"), left + 3, y + 3, MUTED, false);
                return ROW_H;
            }
            for (Row row : rows) {
                if (row instanceof Heading heading) {
                    g.drawString(font, heading.text(), left + 2, y + 4, HEADING, false);
                    y += HEADER_H;
                    continue;
                }
                Pick pick = (Pick) row;
                boolean chosen = pick.entry().equals(selected);
                boolean hot = isMouseOver(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_H
                        && mouseY >= innerTop && mouseY < innerBottom;
                if (chosen) g.fill(left, y, rowRight, y + ROW_H, 0x60FFFFFF);
                else if (hot) g.fill(left, y, rowRight, y + ROW_H, 0x28FFFFFF);
                String rule = pick.entry().rule().isDerived() ? Component.translatable("gui.townstead.naming.rule_short."
                        + pick.entry().rule().name().toLowerCase(Locale.ROOT)).getString() : "";
                int ruleW = font.width(rule);
                int labelW = innerW - 4 - 8 - (ruleW > 0 ? ruleW + 6 : 0);
                g.drawString(font, fit(pick.label(), labelW), left + 3, y + 3, chosen ? 0xFFFFFFFF : 0xFFE0E0E0, false);
                if (ruleW > 0) g.drawString(font, rule, rowRight - ruleW - 3, y + 3, MUTED, false);
                y += ROW_H;
            }
            return y - top;
        }

        private String fit(String text, int width) {
            if (font.width(text) <= width) return text;
            return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            int y = contentTop;
            for (Row row : rows) {
                int h = row instanceof Heading ? HEADER_H : ROW_H;
                if (mouseY >= y && mouseY < y + h && row instanceof Pick pick) {
                    choose(pick.entry());
                    return;
                }
                y += h;
            }
        }

        void reveal(NamingCatalogS2CPayload.Entry entry) {
            int y = 0;
            for (Row row : rows) {
                int h = row instanceof Heading ? HEADER_H : ROW_H;
                if (row instanceof Pick pick && pick.entry().equals(entry)) {
                    scrollToReveal(y, y + h);
                    return;
                }
                y += h;
            }
        }

        void resetToTop() {
            resetScroll();
        }

        @Override
        protected int contentHeightHint() {
            int total = 0;
            for (Row row : rows) total += row instanceof Heading ? HEADER_H : ROW_H;
            return total;
        }
    }
}
