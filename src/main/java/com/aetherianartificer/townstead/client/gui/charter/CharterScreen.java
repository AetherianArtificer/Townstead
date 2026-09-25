package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.gui.common.FrameRenderer;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.client.gui.common.IconArt;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import java.util.ArrayList;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;
import com.aetherianartificer.townstead.client.gui.common.PaperField;
import com.aetherianartificer.townstead.client.gui.common.Controls;
import com.aetherianartificer.townstead.politics.charter.CharterActionC2SPayload;
import com.aetherianartificer.townstead.politics.charter.CharterSnapshotS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Native Charter records using the shared Townstead materials and independently scrollable panes. */
public final class CharterScreen extends Screen {
    private static final int TARGET_W = 620, TARGET_H = 390;
    private CharterSnapshotS2CPayload snapshot;
    private int left, top, panelW, panelH, profileIndex, cultureIndex, tab;
    private String draftName = "New Settlement";
    private boolean review, customFaction;
    private String draftFaction = "", factionBase = "", factionPattern = "{name}";
    private String selectedOrganization = "", searchQuery = "";
    private ScrollPaper mainPane, personalPane, directoryPane;
    private int bodyTop, bodyBottom, censusIndex, requestsView, refreshTicks;
    private PaperField organizationSearch;
    private CharterButton reviewButton;

    private static Component tr(String key, Object... args) { return Component.translatable("charter.townstead." + key, args); }
    private boolean isRecord() { return snapshot.state() == CharterSnapshotS2CPayload.FOUNDED
            || snapshot.state() == CharterSnapshotS2CPayload.REPAIR && snapshot.profiles().isEmpty(); }


    private CharterScreen(CharterSnapshotS2CPayload snapshot) {
        super(Component.translatable("charter.townstead.title"));
        this.snapshot = snapshot;
    }

    public static void accept(CharterSnapshotS2CPayload snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (minecraft.screen instanceof FactionNameScreen naming && naming.accept(snapshot)) return;
        if (minecraft.screen instanceof HeraldryScreen editor && editor.accept(snapshot)) return;
        if (minecraft.screen instanceof CharterScreen screen
                && screen.snapshot.lectern().equals(snapshot.lectern())) {
            if (screen.snapshot.equals(snapshot)) return;
            double scroll = screen.mainPane == null ? 0 : screen.mainPane.scroll;
            double directoryScroll = screen.directoryPane == null ? 0 : screen.directoryPane.scroll;
            double personalScroll = screen.personalPane == null ? 0 : screen.personalPane.scroll;
            boolean mainFocused = screen.mainPane != null && screen.mainPane.isFocused();
            boolean directoryFocused = screen.directoryPane != null && screen.directoryPane.isFocused();
            boolean personalFocused = screen.personalPane != null && screen.personalPane.isFocused();
            boolean searching = screen.organizationSearch != null && screen.organizationSearch.isFocused();
            screen.snapshot = snapshot;
            if (!snapshot.settlement().isBlank()) screen.draftName = snapshot.settlement();
            screen.review = false;
            screen.rebuildWidgets();
            if (screen.mainPane != null) screen.mainPane.scroll = scroll;
            if (screen.directoryPane != null) screen.directoryPane.scroll = directoryScroll;
            if (screen.personalPane != null) screen.personalPane.scroll = personalScroll;
            if (mainFocused && screen.mainPane != null) screen.setFocused(screen.mainPane);
            if (directoryFocused && screen.directoryPane != null) screen.setFocused(screen.directoryPane);
            if (personalFocused && screen.personalPane != null) screen.setFocused(screen.personalPane);
            if (searching && screen.organizationSearch != null) screen.setFocused(screen.organizationSearch);
        } else {
            minecraft.setScreen(new CharterScreen(snapshot));
        }
    }

    @Override protected void init() {
        panelW = Math.min(TARGET_W, width - 18);
        panelH = Math.min(TARGET_H, height - 18);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        rebuildWidgets();
    }

    @Override protected void rebuildWidgets() {
        clearWidgets();
        mainPane = null; personalPane = null; directoryPane = null; organizationSearch = null; reviewButton = null;
        if (snapshot == null) return;
        int state = snapshot.state();
        if (state == CharterSnapshotS2CPayload.UNFOUNDED && !snapshot.profiles().isEmpty() && !snapshot.cultures().isEmpty()) foundingWidgets();
        else if (state == CharterSnapshotS2CPayload.EXISTING) existingWidgets();
        else if (state == CharterSnapshotS2CPayload.PREPARED || state == CharterSnapshotS2CPayload.REPAIR && !snapshot.profiles().isEmpty()) preparedWidgets();
        else if (state == CharterSnapshotS2CPayload.FOUNDED || state == CharterSnapshotS2CPayload.REPAIR) foundedWidgets();
        else addRenderableWidget(new CharterButton(left + panelW - 112, top + panelH - 28, 96, 18,
                    Component.translatable("charter.townstead.retry"), button -> send(CharterActionC2SPayload.REFRESH)));
    }

    void updateSnapshot(CharterSnapshotS2CPayload value) { snapshot = value; }

    private void foundingWidgets() {
        int bodyTop = top + 76;
        int actionY = top + panelH - 28;
        if (!review) {
            PaperField name = addRenderableWidget(new PaperField(font, left + 26, bodyTop + 25,
                    Math.min(224, panelW - 52), 10, Component.translatable("charter.townstead.settlement_name")));
            name.setMaxLength(48);
            name.setValue(draftName);
            name.setResponder(value -> { draftName = value; if (reviewButton != null) reviewButton.active = snapshot.editable() && value.trim().length() >= 2; });
            addRenderableWidget(new CharterButton(left + 26, bodyTop + 73, Math.min(224, panelW - 52), 20,
                    selected(snapshot.cultures(), cultureIndex).name().component(), button -> {
                cultureIndex = next(cultureIndex, snapshot.cultures()); if (!customFaction) draftFaction = ""; rebuildWidgets();
            }));
            reviewButton = addRenderableWidget(new CharterButton(left + panelW - 132, actionY,
                    116, 18, Component.translatable("charter.townstead.review"), button -> {
                if (draftFaction.isBlank()) suggestFaction(); review = true; rebuildWidgets();
            }));
            reviewButton.active = snapshot.editable() && draftName.trim().length() >= 2;
        } else {
            int fieldWidth = Math.min(224, panelW - 150);
            PaperField faction = addRenderableWidget(new PaperField(font, left + 28, top + 144,
                    fieldWidth, 10, tr("faction_name")));
            faction.setMaxLength(48); faction.setValue(draftFaction);
            faction.setResponder(value -> { draftFaction = value; customFaction = true; });
            addRenderableWidget(new CharterButton(left + 36 + fieldWidth, top + 140, 94, 18,
                    tr("suggest_name"), b -> { suggestFaction(); rebuildWidgets(); }));
            addRenderableWidget(new CharterButton(left + 16, actionY, 92, 18,
                    Component.translatable("gui.back"), button -> { review = false; rebuildWidgets(); }));
            CharterButton prepare = addRenderableWidget(new CharterButton(left + panelW - 152, actionY,
                    136, 18, Component.translatable("charter.townstead.prepare"), button -> send(CharterActionC2SPayload.PREPARE)));
            prepare.active = snapshot.editable() && com.aetherianartificer.townstead.politics.charter.CharterIdentityService.normalize(draftFaction) != null;
            faction.setResponder(value -> { draftFaction = value; customFaction = true; prepare.active = snapshot.editable() && com.aetherianartificer.townstead.politics.charter.CharterIdentityService.normalize(value) != null; });
        }
    }

    private void preparedWidgets() {
        int y = top + panelH - 28;
        addRenderableWidget(new CharterButton(left + 16, y, 112, 18,
                Component.translatable("charter.townstead.return_to_bell"), button -> onClose()));
        CharterButton cancel = addRenderableWidget(new CharterButton(left + panelW - 142, y, 126, 18,
                Component.translatable("charter.townstead.cancel"), button -> send(CharterActionC2SPayload.CANCEL)));
        cancel.active = snapshot.editable();
    }

    private void existingWidgets() {
        addRenderableWidget(new CharterButton(left + panelW - 164, top + panelH - 28, 148, 18,
                Component.translatable("charter.townstead.link_existing"),
                button -> send(CharterActionC2SPayload.LINK_EXISTING))).active = snapshot.editable();
    }

    private void foundedWidgets() {
        var faction = snapshot.heraldry().stream().filter(v -> v.actor().startsWith("polity:")).findFirst().orElse(null);
        if (faction != null && faction.editable()) addRenderableWidget(new CharterButton(left + panelW - 116, top + 12,
                100, 18, tr("identity.rename"), b -> minecraft.setScreen(new FactionNameScreen(this, snapshot))));
        var pages = new ArrayList<Integer>(List.of(0, 2, 3));
        if (snapshot.organizations().stream().anyMatch(o -> !o.governing())) pages.add(1);
        if (snapshot.civic() != null) pages.add(4);
        if (!pages.contains(tab)) tab = 0;
        int tabWidth = Math.min(110, (panelW - 32) / pages.size());
        for (int i = 0; i < pages.size(); i++) {
            int index = pages.get(i);
            String key = switch (index) { case 1 -> "organizations"; case 2 -> "members"; case 3 -> "requests"; case 4 -> "government"; default -> "faction"; };
            CharterButton button = addRenderableWidget(new CharterButton(left + 16 + i * tabWidth, top + 55,
                    tabWidth - 3, 18, index == 3 && openRequestCount() > 0 ? tr("requests_count", openRequestCount()) : tr("tab." + key), value -> { tab = index; rebuildWidgets(); }));
            button.setSelected(tab == index);
        }
        bodyTop = top + (snapshot.state() == CharterSnapshotS2CPayload.REPAIR ? 99 : 79);
        bodyBottom = top + panelH - (snapshot.message().isBlank() ? 34 : 45);
        int available = panelW - 32, bodyHeight = Math.max(32, bodyBottom - bodyTop);
        int sideWidth = panelW >= 460 && tab == 0 ? Math.min(132, available / 3) : 0;
        int mainWidth = available - (sideWidth == 0 ? 0 : sideWidth + 10);
        if (tab == 1) {
            organizationSearch = addRenderableWidget(new PaperField(font, left + 21, bodyTop + 4, mainWidth - 10, 10, tr("search")));
            organizationSearch.setValue(searchQuery);
            organizationSearch.setResponder(value -> {
                searchQuery = value;
                if (directoryPane != null) directoryPane.scroll = 0;
                var matches = filteredOrganizations();
                if (matches.stream().noneMatch(o -> o.id().equals(selectedOrganization))) {
                    selectedOrganization = matches.isEmpty() ? "" : matches.get(0).id();
                    if (mainPane != null) mainPane.scroll = 0;
                }
            });
            if (filteredOrganizations().stream().noneMatch(o -> o.id().equals(selectedOrganization)))
                selectedOrganization = filteredOrganizations().isEmpty() ? "" : filteredOrganizations().get(0).id();
            int listWidth = mainWidth >= 330 ? Math.max(130, mainWidth * 36 / 100) : mainWidth;
            int listHeight = listWidth == mainWidth ? Math.min(85, Math.max(35, bodyHeight / 3)) : bodyHeight - 23;
            directoryPane = addRenderableWidget(new ScrollPaper(left + 16, bodyTop + 23, listWidth, listHeight, tr("organizations"), this::drawDirectory));
            int detailX = listWidth == mainWidth ? left + 16 : left + 26 + listWidth;
            int detailY = listWidth == mainWidth ? bodyTop + 28 + listHeight : bodyTop + 23;
            int detailWidth = listWidth == mainWidth ? mainWidth : mainWidth - listWidth - 10;
            mainPane = addRenderableWidget(new ScrollPaper(detailX, detailY, detailWidth,
                    Math.max(25, bodyBottom - detailY), tr("organization_details"), this::drawOrganization));
        } else {
            int paneY = bodyTop;
            if (tab == 3) {
                int filterW = Math.min(105, mainWidth / 2);
                for (int i = 0; i < 2; i++) {
                    int view = i;
                    var filter = addRenderableWidget(new CharterButton(left + 16 + i * filterW, bodyTop,
                            filterW - 3, 17, tr(i == 0 ? "requests_open" : "requests_history"), b -> { requestsView = view; rebuildWidgets(); }));
                    filter.setSelected(requestsView == i);
                }
                paneY += 23;
            }
            mainPane = addRenderableWidget(new ScrollPaper(left + 16, paneY, mainWidth, Math.max(25, bodyBottom - paneY),
                    tr(tab == 0 ? "tab.faction" : tab == 3 ? "tab.requests" : tab == 4 ? "tab.government" : "tab.members"), tab == 0 ? this::drawOverview : tab == 3 ? this::drawRequests : tab == 4 ? this::drawCivic : this::drawAffiliations));
        }
        if (sideWidth > 0) personalPane = addRenderableWidget(new ScrollPaper(left + panelW - 16 - sideWidth, bodyTop,
                sideWidth, bodyHeight, tr("your_standing"), this::drawPersonal));
        addRenderableWidget(new CharterButton(left + panelW - 78, top + panelH - 25, 62, 17,
                Component.translatable("gui.done"), button -> onClose()));
        addRenderableWidget(new CharterButton(left + 16, top + panelH - 25, 80, 17,
                tr("refresh"), button -> send(CharterActionC2SPayload.REFRESH)));
        if (!snapshot.heraldry().isEmpty()) addRenderableWidget(new CharterButton(left + 103, top + panelH - 25, 88, 17,
                tr("heraldry.title"), button -> minecraft.setScreen(new HeraldryScreen(this, snapshot))));
    }

    private List<CharterSnapshotS2CPayload.Organization> filteredOrganizations() {
        String query = searchQuery.toLowerCase(Locale.ROOT).trim();
        return snapshot.organizations().stream().filter(o -> !o.governing()).filter(o -> (o.name().component().getString() + " "
                + o.kind().component().getString()).toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private void suggestFaction() {
        customFaction = false;
        var names = selected(snapshot.cultures(), cultureIndex).naming();
        var patterns = selected(snapshot.profiles(), profileIndex).naming();
        var random = java.util.concurrent.ThreadLocalRandom.current();
        factionBase = names.isEmpty() ? draftName : names.get(random.nextInt(names.size()));
        factionPattern = patterns.isEmpty() ? "{name}" : patterns.get(random.nextInt(patterns.size()));
        draftFaction = factionPattern.replace("{name}", factionBase);
        if (com.aetherianartificer.townstead.politics.charter.CharterIdentityService.normalize(draftFaction) == null) {
            factionPattern = "{name}"; draftFaction = factionBase;
        }
    }

    private void send(int action) {
        CharterSnapshotS2CPayload.Option profile = snapshot.profiles().isEmpty() ? null : selected(snapshot.profiles(), profileIndex);
        CharterSnapshotS2CPayload.Option culture = snapshot.cultures().isEmpty() ? null : selected(snapshot.cultures(), cultureIndex);
        CharterActionC2SPayload payload = new CharterActionC2SPayload(snapshot.lectern(), action, draftName,
                profile == null ? "" : profile.id(), culture == null ? "" : culture.id(),
                factionPattern, factionBase, draftFaction, 0);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xA0100C08);
        FrameRenderer.drawInnerPanel(g, left, top, panelW, panelH);
        FrameRenderer.drawWoodenFrame(g, left, top, panelW, panelH, 6);
        g.fill(left + 8, top + 85, left + panelW - 8, top + panelH - 42, Palette.DESK_DEEP);
        if (snapshot.state() == CharterSnapshotS2CPayload.UNFOUNDED) {
            if (snapshot.profiles().isEmpty() || snapshot.cultures().isEmpty()) renderUnavailable(g); else renderFounding(g);
        }
        else if (snapshot.state() == CharterSnapshotS2CPayload.EXISTING) renderExisting(g);
        else if (snapshot.state() == CharterSnapshotS2CPayload.PREPARED
                || snapshot.state() == CharterSnapshotS2CPayload.REPAIR && !snapshot.profiles().isEmpty()) renderPrepared(g);
        else if (snapshot.state() == CharterSnapshotS2CPayload.FOUNDED || snapshot.state() == CharterSnapshotS2CPayload.REPAIR) renderFounded(g);
        else renderUnavailable(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderFounding(GuiGraphics g) {
        identity(g, Component.translatable("charter.townstead.unfounded"),
                Component.translatable("charter.townstead.unfounded_subtitle"));
        int x = left + 16, y = top + 86, w = panelW - 32, h = panelH - 124;
        paper(g, x, y, w, h);
        if (!review) {
            label(g, "charter.townstead.settlement_name", x + 10, y + 7);
            label(g, "charter.townstead.founding_culture", x + 10, y + 55);
            CharterSnapshotS2CPayload.Option culture = selected(snapshot.cultures(), cultureIndex);
            wrap(g, culture.description().component(), x + 250, y + 54, Math.max(100, w - 260), Palette.CARD_INK);
            wrap(g, culture.consequences().component(), x + 250, y + 83, Math.max(100, w - 260), Palette.CARD_INK_DIM);
            label(g, "charter.townstead.your_status", x + 10, y + 119);
            wrap(g, tr("founding_leader_status"), x + 10, y + 137, w - 20, Palette.CARD_INK);
            wrap(g, tr("founding_leader_help"), x + 10, y + 157, w - 20, Palette.CARD_INK_DIM);
        } else {
            g.drawString(font, Component.translatable("charter.townstead.review_heading"), x + 12, y + 10, Palette.INK_HEADER, false);
            reviewRow(g, "charter.townstead.settlement", draftName, x + 12, y + 26, w - 24);
            label(g, "charter.townstead.faction_name", x + 12, y + 44);
            reviewRow(g, "charter.townstead.your_status", tr("founding_leader_status").getString(), x + 12, y + 86, w - 24);
            reviewRow(g, "charter.townstead.founding_culture", selected(snapshot.cultures(), cultureIndex).name().component().getString(), x + 12, y + 108, w - 24);
            wrap(g, tr("founding_leader_help"), x + 12, y + 133, w - 24, Palette.CARD_INK_DIM);
            reviewRow(g, "charter.townstead.land", tr("consequence.land").getString(), x + 12, y + 152, w - 24);
        }
        message(g);
    }

    private void renderPrepared(GuiGraphics g) {
        identity(g, Component.literal(snapshot.settlement()), snapshot.governance().component());
        int x = left + 16, y = top + 86, w = panelW - 32, h = panelH - 124;
        paper(g, x, y, w, h);
        g.drawString(font, Component.translatable("charter.townstead.prepared"), x + 12, y + 12, Palette.INK_HEADER, false);
        reviewRow(g, "charter.townstead.settlement", snapshot.settlement(), x + 12, y + 38, w - 24);
        reviewRow(g, "charter.townstead.your_status", tr("founding_leader_status").getString(), x + 12, y + 62, w - 24);
        reviewRow(g, "charter.townstead.founding_culture", snapshot.foundingCulture().component().getString(), x + 12, y + 86, w - 24);
        reviewRow(g, "charter.townstead.faction", snapshot.polity(), x + 12, y + 108, w - 24);
        wrap(g, snapshot.authority().component(), x + 12, y + 138, w - 24, Palette.INK_GOOD);
        if (snapshot.state() == CharterSnapshotS2CPayload.REPAIR) {
            wrap(g, Component.translatable("charter.townstead.repair_before_ring"), x + 12, y + 150, w - 24, Palette.INK_BAD);
        }
        message(g);
    }

    private void renderExisting(GuiGraphics g) {
        identity(g, Component.literal(snapshot.polity().isBlank() ? snapshot.settlement() : snapshot.polity()), snapshot.governance().component());
        int x = left + 16, y = top + 86, w = panelW - 32;
        int asideW = w >= 440 ? 146 : 0;
        int mainW = w - (asideW == 0 ? 0 : asideW + 10);
        paper(g, x, y, mainW, Math.min(186, panelH - 130));
        fit(g, tr("existing_settlement"), x + 12, y + 12, mainW - 24, Palette.INK_HEADER);
        reviewRow(g, "charter.townstead.settlement", snapshot.settlement(), x + 12, y + 38, mainW - 24);
        reviewRow(g, "charter.townstead.faction", snapshot.polity(), x + 12, y + 62, mainW - 24);
        reviewRow(g, "charter.townstead.current_governance", snapshot.governance().component().getString(), x + 12, y + 86, mainW - 24);
        paragraph(g, snapshot.authority().component(), x + 12, y + 116, mainW - 24, Palette.CARD_INK_DIM);
        if (asideW > 0) {
            int ax = x + mainW + 10;
            sheet(g, ax, y, asideW, 186, tr("title"));
            icon(g, "minecraft:bell", ax + asideW / 2 - 24, y + 33, 3);
            paragraph(g, tr("linked_place"), ax + 12, y + 98, asideW - 24, Palette.CARD_INK);
        }
        message(g);
    }

    private void renderFounded(GuiGraphics g) {
        identity(g, Component.literal(snapshot.polity().isBlank() ? snapshot.settlement() : snapshot.polity()), snapshot.governance().component());
        if (snapshot.state() == CharterSnapshotS2CPayload.REPAIR) {
            g.fill(left + 16, top + 78, left + panelW - 16, top + 95, 0xFF7D2E1E);
            fit(g, tr("repair"), left + 21, top + 82, panelW - 42, 0xFFFFFFFF);
        }
        message(g);
    }

    private CharterSnapshotS2CPayload.CensusScope currentCensus() {
        return snapshot.censusScopes().isEmpty() ? new CharterSnapshotS2CPayload.CensusScope("settlement",
                CharterSnapshotS2CPayload.Text.of(tr("census_basis", snapshot.settlement())), snapshot.census(), snapshot.population(), true)
                : snapshot.censusScopes().get(Math.floorMod(censusIndex, snapshot.censusScopes().size()));
    }

    private int drawOverview(GuiGraphics g, int x, int y, int w) {
        int start = y;
        var governing = snapshot.organizations().stream().filter(CharterSnapshotS2CPayload.Organization::governing).findFirst().orElse(null);
        Component description = snapshot.civic() != null && snapshot.civic().controlsGovernment()
                ? snapshot.civic().description().component() : governing == null ? snapshot.authority().component() : governing.description().component();
        y = information(g, x, y, w, snapshot.governance().component(), description);
        var seat = snapshot.seat();
        if (seat != null) {
            y = information(g, x, y, w, seat.heading().component(), seat.body().component());
            if (!seat.actions().isEmpty()) y = drawActions(g, x, y - 2, w, seat.actions(), seat.actor()) + 7;
        }
        if (snapshot.legitimacy() != null) y = information(g, x, y, w, tr("legitimacy.title"), snapshot.legitimacy().component());
        if (snapshot.standing() != null) y = information(g, x, y, w, tr("standing.title"), snapshot.standing().component());
        if (personalPane == null) y = drawFactionStanding(g, x, y, w);
        int censusH = Math.max(88, 34 + currentCensus().groups().size() * 19);
        sheet(g, x, y, w, censusH, tr("cultures"));
        small(g, Component.empty().append(currentCensus().label().component()).append(snapshot.censusScopes().size() > 1 ? "  >" : ""),
                x + 9, y + 19, w - 18, Palette.CARD_INK_DIM);
        if (snapshot.censusScopes().size() > 1) {
            g.renderOutline(x + 7, y + 16, w - 14, 13, 0x448A5F1E);
            mainPane.hit(x + 7, y + 16, w - 14, 13, () -> censusIndex++);
        }
        if (!currentCensus().available()) {
            paragraph(g, tr("census.unavailable"), x + 9, y + 38, w - 18, Palette.CARD_INK_DIM);
        } else {
        int chartX = x + 39, chartY = y + 57;
        donut(g, chartX, chartY, 23);
        String count = Integer.toString(currentCensus().population());
        g.drawString(font, count, chartX - font.width(count) / 2, chartY - 4, Palette.CARD_INK, false);
        int legendX = x + 77, legendW = w - 88, ly = y + 36;
        if (currentCensus().groups().isEmpty()) paragraph(g, tr("census_empty"), legendX, ly, legendW, Palette.CARD_INK_DIM);
        java.text.NumberFormat percent = java.text.NumberFormat.getPercentInstance();
        percent.setMaximumFractionDigits(1);
        int index = 0;
        for (var group : currentCensus().groups()) {
            g.fill(legendX, ly + 1, legendX + 10, ly + 11, group.color());
            small(g, Component.literal(Integer.toString(++index)), legendX + 3, ly + 3, 6, 0xFFFFFFFF);
            int numberW = 55;
            fit(g, group.name().component(), legendX + 16, ly + 1, legendW - numberW - 18, Palette.CARD_INK);
            String values = group.count() + " / " + percent.format(currentCensus().population() == 0 ? 0 : (double) group.count() / currentCensus().population());
            small(g, Component.literal(values), legendX + legendW - numberW, ly + 2, numberW, Palette.CARD_INK_DIM);
            g.fill(legendX + 15, ly + 15, x + w - 9, ly + 16, 0x33A48B63);
            ly += 19;
        }
        }
        y += censusH + 7;
        y = information(g, x, y, w, tr("founding_tradition"), snapshot.foundingCulture().component());
        if (governing != null) {
            var management = governing.actions().stream().filter(this::managementAction).toList();
            if (!management.isEmpty()) {
                section(g, x, y, w, tr("faction_controls"), Component.empty()); y += 18;
                y = drawActions(g, x, y, w, management, governing.id()) + 7;
            }
        }
        return y - start;
    }

    private int drawRoles(GuiGraphics g, int x, int y, int w, CharterSnapshotS2CPayload.Organization organization) {
        section(g, x, y, w, tr(organization.governing() ? "members_by_role" : "roles"), Component.empty());
        y += 17;
        if (organization.roles().isEmpty()) {
            g.fill(x, y, x + w, y + 28, Palette.ROW);
            small(g, tr("roles_unavailable"), x + 9, y + 10, w - 18, Palette.LABEL_WARM);
            return y + 35;
        }
        for (var role : organization.roles()) {
            int h = 21 + textHeight(holders(role), w - 35);
            g.fill(x, y, x + w, y + h, Palette.ROW);
            g.fill(x + 9, y + 8, x + 16, y + 15, Palette.BRASS_DEEP);
            g.renderOutline(x + 7, y + 6, 11, 11, Palette.DESK_LIP);
            fit(g, role.name().component(), x + 26, y + 5, w - 35, Palette.LABEL_LIGHT);
            paragraph(g, holders(role), x + 26, y + 16, w - 35, Palette.LABEL_WARM);
            y += h + 3;
        }
        return y + 6;
    }

    private void section(GuiGraphics g, int x, int y, int w, Component title, Component detail) {
        fit(g, title, x + 2, y + 4, w - 28, Palette.LABEL_LIGHT);
        if (!detail.getString().isBlank()) small(g, detail, x + w - 23, y + 5, 21, Palette.LABEL_WARM);
    }

    private Component holders(CharterSnapshotS2CPayload.Role role) {
        if (role.holders().isEmpty()) return tr("no_visible_holders");
        var result = Component.empty();
        for (int i = 0; i < role.holders().size(); i++) {
            if (i > 0) result.append(" / ");
            result.append(role.holders().get(i).component());
        }
        return result;
    }

    private int drawDirectory(GuiGraphics g, int x, int y, int w) {
        int start = y;
        for (var organization : filteredOrganizations()) {
            if (organization.id().equals(selectedOrganization)) g.fill(x, y, x + w, y + 33, Palette.BRASS_DEEP);
            organizationRow(g, x + 2, y + 2, w - 4, organization);
            directoryPane.hit(x, y, w, 33, () -> { selectedOrganization = organization.id(); if (mainPane != null) mainPane.scroll = 0; });
            y += 37;
        }
        if (y == start) { sheet(g, x, y, w, 48, tr("organizations")); paragraph(g, tr("no_matches"), x + 8, y + 22, w - 16, Palette.CARD_INK_DIM); y += 48; }
        return y - start;
    }

    private void organizationRow(GuiGraphics g, int x, int y, int w, CharterSnapshotS2CPayload.Organization o) {
        g.fill(x, y, x + w, y + 30, Palette.ROW);
        var emblem = snapshot.heraldry().stream().filter(h -> h.actor().equals("organization:" + o.id()) && h.revision() > 0).findFirst().orElse(null);
        if (emblem == null || minecraft.level == null) icon(g, o.icon(), x + 7, y + 7, 1);
        else g.renderItem(com.aetherianartificer.townstead.politics.heraldry.EmblemItems.banner(minecraft.level.registryAccess(),
                com.aetherianartificer.townstead.politics.heraldry.EmblemRecipe.safe(emblem.recipe())), x + 7, y + 7);
        int badge = w > 245 ? 60 : 0;
        fit(g, o.name().component(), x + 30, y + 5, w - 37 - badge, Palette.LABEL_LIGHT);
        small(g, o.kind().component(), x + 30, y + 18, w - 37 - badge, Palette.LABEL_WARM);
        if (badge > 0) {
            g.renderOutline(x + w - badge - 5, y + 8, badge, 16, Palette.DESK_LIP);
            small(g, tr("relationship." + o.relationship()), x + w - badge, y + 13, badge - 8, Palette.LABEL_WARM);
        }
    }

    private int drawOrganization(GuiGraphics g, int x, int y, int w) {
        var organization = selectedOrganization();
        if (organization == null) { sheet(g, x, y, w, 55, tr("organization_details")); paragraph(g, tr("select_organization"), x + 10, y + 23, w - 20, Palette.CARD_INK_DIM); return 55; }
        int start = y;
        int inner = w - 18;
        int h = 35 + textHeight(organization.name().component(), inner) + textHeight(organization.description().component(), inner);
        int rulesHeight = 17 + textHeight(organization.admission().component(), inner)
                + 17 + textHeight(organization.departure().component(), inner);
        sheet(g, x, y, w, h + rulesHeight + 9, organization.kind().component());
        int cy = paragraph(g, organization.name().component(), x + 9, y + 20, inner, Palette.CARD_INK);
        cy = paragraph(g, organization.description().component(), x + 9, cy + 7, inner, Palette.CARD_INK_DIM) + 9;
        g.fill(x + 9, cy, x + w - 9, cy + 1, 0x33A48B63);
        small(g, tr("admission"), x + 9, cy + 8, inner, Palette.CARD_INK_DIM);
        cy = paragraph(g, organization.admission().component(), x + 9, cy + 19, inner, Palette.CARD_INK);
        small(g, tr("departure"), x + 9, cy + 6, inner, Palette.CARD_INK_DIM);
        cy = paragraph(g, organization.departure().component(), x + 9, cy + 17, inner, Palette.CARD_INK);
        y += h + rulesHeight + 16;
        if (personalPane == null) {
            var relationship = Component.empty().append(tr("relationship." + organization.relationship()));
            for (var role : organization.yourRoles()) relationship.append(" / ").append(role.component());
            y = information(g, x, y, w, tr("your_standing"), relationship);
        }
        y = drawActions(g, x, y, w, organization.actions(), organization.id());
        y = drawRoles(g, x, y, w, organization);
        return y - start;
    }

    private int information(GuiGraphics g, int x, int y, int w, Component heading, Component body) {
        int h = 31 + textHeight(body, w - 20);
        sheet(g, x, y, w, h, heading);
        paragraph(g, body, x + 10, y + 22, w - 20, Palette.CARD_INK);
        return y + h + 9;
    }

    private long activeMemberships() { return snapshot.organizations().stream().filter(o -> o.relationship().equals("active")).count(); }

    private CharterSnapshotS2CPayload.Organization selectedOrganization() {
        return tab == 1 ? filteredOrganizations().stream().filter(o -> o.id().equals(selectedOrganization)).findFirst().orElse(null) : null;
    }

    private boolean managementAction(CharterSnapshotS2CPayload.Action action) {
        return action.id().equals("transfer_leadership") || action.id().equals("dissolve_faction") || action.id().equals("cancel_amendment");
    }

    private int drawFactionStanding(GuiGraphics g, int x, int y, int w) {
        var governing = snapshot.organizations().stream().filter(CharterSnapshotS2CPayload.Organization::governing).findFirst().orElse(null);
        Component standing = governing == null ? tr("membership_unknown") : tr("relationship." + governing.relationship());
        if (governing != null && !governing.yourRoles().isEmpty()) {
            var roles = Component.empty();
            for (var role : governing.yourRoles()) { if (!roles.getString().isEmpty()) roles.append(" / "); roles.append(role.component()); }
            standing = roles;
        }
        if (snapshot.civic() != null && snapshot.civic().controlsGovernment()) standing = snapshot.civic().standing().component();
        return information(g, x, y, w, tr("your_role"), standing);
    }

    private int drawPersonal(GuiGraphics g, int x, int y, int w) {
        int start = y;
        y = drawFactionStanding(g, x, y, w);
        y = information(g, x, y, w, tr("tab.members"), tr("members_hint"));
        return y - start;
    }

    private int drawOrganizationStanding(GuiGraphics g, int x, int y, int w, CharterSnapshotS2CPayload.Organization organization) {
        int start = y, inner = w - 18;
        Component state = tr("relationship." + organization.relationship());
        int h = 56 + textHeight(organization.name().component(), inner) + textHeight(state, inner) + organization.yourRoles().size() * 2;
        for (var role : organization.yourRoles()) h += textHeight(role.component(), inner);
        paper(g, x, y, w, h);
        g.fill(x, y, x + w, y + 2, Palette.BRASS);
        small(g, tr("your_standing"), x + 9, y + 10, inner, Palette.CARD_INK_DIM);
        y = paragraph(g, organization.name().component(), x + 9, y + 24, inner, Palette.CARD_INK) + 9;
        g.fill(x + 9, y, x + w - 9, y + 1, 0x33A48B63);
        y = paragraph(g, state, x + 9, y + 10, inner, Palette.CARD_INK);
        for (var role : organization.yourRoles()) y = paragraph(g, role.component(), x + 9, y + 2, inner, Palette.CARD_INK_DIM);
        return Math.max(h, y - start + 9);
    }

    private boolean openRequest(CharterSnapshotS2CPayload.Request r) {
        return r.state().equals("pending") || r.state().equals("offered") || r.state().equals("notice");
    }
    private long openRequestCount() { return snapshot.requests().stream().filter(this::openRequest).count(); }

    private int drawCivicOffices(GuiGraphics g, int x, int y, int w) {
        var civic = snapshot.civic();
        if (civic == null) return y;
        var officeView = new CharterSnapshotS2CPayload.Organization(civic.actor(), civic.governance(), civic.governance(), civic.description(),
                "external", true, "minecraft:bell", Palette.BRASS, civic.description(), civic.description(), List.of(), civic.offices());
        return drawRoles(g, x, y, w, officeView);
    }
    private int drawCivic(GuiGraphics g, int x, int y, int w) {
        var civic = snapshot.civic();
        int start = y;
        if (civic == null || !civic.controlsGovernment()) {
            var governing = snapshot.organizations().stream().filter(CharterSnapshotS2CPayload.Organization::governing).findFirst().orElse(null);
            y = information(g, x, y, w, snapshot.governance().component(), governing == null ? snapshot.authority().component() : governing.description().component());
            if (governing != null) {
                y = drawRoles(g, x, y, w, governing);
                y = information(g, x, y, w, tr("admission"), governing.admission().component());
                y = drawActions(g, x, y, w, governing.actions(), governing.id());
            }
            if (civic == null) return y - start;
        }
        y = information(g, x, y, w, civic.governance().component(), civic.description().component());
        y = drawActions(g, x, y, w, civic.actions(), civic.actor());
        if (civic.controlsGovernment()) y = drawCivicOffices(g, x, y, w);
        if (civic.history().isEmpty()) return y - start;
        section(g, x, y, w, tr("chronicle"), Component.empty()); y += 18;
        if (civic.history().isEmpty()) y = information(g, x, y, w, tr("chronicle"), tr("history_empty"));
        for (var entry : civic.history()) {
            int h = textHeight(entry.component(), w - 18) + 16;
            paper(g, x, y, w, h);
            paragraph(g, entry.component(), x + 9, y + 8, w - 18, Palette.CARD_INK);
            y += h + 5;
        }
        return y - start;
    }
    @Override public void tick() {
        super.tick();
        if (isRecord() && ++refreshTicks >= 100) { refreshTicks = 0; send(CharterActionC2SPayload.REFRESH); }
    }

    private int drawRequests(GuiGraphics g, int x, int y, int w) {
        int start = y;
        var requests = snapshot.requests().stream().filter(r -> openRequest(r) == (requestsView == 0)).toList();
        if (requests.isEmpty()) return information(g, x, y, w, tr("tab.requests"), tr(requestsView == 0 ? "requests_open_empty" : "requests_history_empty")) - start;
        for (var request : requests) {
            int h = 43 + textHeight(request.organization().component(), w - 18);
            sheet(g, x, y, w, h, tr("request.kind." + request.kind()));
            int cy = paragraph(g, request.organization().component(), x + 9, y + 20, w - 18, Palette.CARD_INK);
            fit(g, request.person().component(), x + 9, cy + 3, w / 2 - 15, Palette.CARD_INK_DIM);
            small(g, tr("request.state." + request.state()), x + w / 2, cy + 4, w / 2 - 9, Palette.CARD_INK_DIM);
            y += h + 4;
            if (request.required() > 0 && (request.state().equals("pending") || request.state().equals("offered"))) {
                small(g, tr("request.approvals", request.approvals(), request.required()), x + 9, y + 3, w - 18, Palette.LABEL_WARM);
                y += 17;
            }
            y = drawActions(g, x, y, w, request.actions(), request.id()) + 7;
        }
        return y - start;
    }
    private int drawActions(GuiGraphics g, int x, int y, int w, List<CharterSnapshotS2CPayload.Action> actions, String target) {
        for (var action : actions) {
            var rect = new Controls.Rect(x, y, w, 20);
            boolean hot = painting != null && painting.isMouseOver(painting.mouseX, painting.mouseY)
                    && rect.contains(painting.mouseX, painting.mouseY);
            Controls.drawButton(g, font, rect, action.label().component().getString(), false, hot, true);
            if (painting != null) {
                painting.narration.append(action.label().component()).append(". ");
                painting.hit(rect.x(), rect.y(), rect.w(), rect.h(), () -> minecraft.setScreen(new ActionReview(action, target)));
            }
            y += 24;
        }
        return y;
    }
    private final class ActionReview extends Screen {
        private final CharterSnapshotS2CPayload.Action action;
        private final String target;
        private String person = "";
        ActionReview(CharterSnapshotS2CPayload.Action action, String target) {
            super(action.label().component()); this.action = action; this.target = target;
        }
        @Override protected void init() {
            int w = Math.min(330, width - 24), x = (width - w) / 2, y = (height - 180) / 2;
            if (action.personInput()) {
                PaperField input = addRenderableWidget(new PaperField(font, x + 16, y + 101, w - 32, 10, tr("player_name")));
                input.setMaxLength(16); input.setValue(person); input.setResponder(v -> person = v);
            }
            addRenderableWidget(new CharterButton(x + 12, y + 151, 90, 18, Component.translatable("gui.cancel"), b -> onClose()));
            addRenderableWidget(new CharterButton(x + w - 112, y + 151, 100, 18, tr("confirm_action"), b -> {
                if (action.personInput() && person.isBlank()) return;
                CharterActionC2SPayload payload = new CharterActionC2SPayload(snapshot.lectern(), snapshot.civic() != null && target.equals(snapshot.civic().actor()) ? CharterActionC2SPayload.CIVIC : CharterActionC2SPayload.MEMBERSHIP,
                        "", "", "", action.id(), target, person.trim(), snapshot.revision());
                //? if neoforge {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
                //?} else {
                /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
                *///?}
                onClose();
            }));
        }
        @Override public void render(GuiGraphics g, int mx, int my, float tick) {
            g.fill(0, 0, width, height, 0xD0100C08);
            int w = Math.min(330, width - 24), x = (width - w) / 2, y = (height - 180) / 2;
            FrameRenderer.drawInnerPanel(g, x, y, w, 180);
            FrameRenderer.drawWoodenFrame(g, x, y, w, 180, 6);
            paper(g, x + 10, y + 10, w - 20, 128);
            fit(g, title, x + 18, y + 22, w - 36, Palette.CARD_INK);
            paragraph(g, action.description().component(), x + 18, y + 43, w - 36, Palette.CARD_INK_DIM);
            if (action.personInput()) small(g, tr("player_name"), x + 18, y + 88, w - 36, Palette.CARD_INK_DIM);
            super.render(g, mx, my, tick);
        }
        @Override public Component getNarrationMessage() {
            return Component.empty().append(title).append(". ").append(action.description().component());
        }
        @Override public void onClose() { minecraft.setScreen(CharterScreen.this); }
        @Override public boolean isPauseScreen() { return false; }
        //? if >=1.21 {
        @Override public void renderBackground(GuiGraphics g, int x, int y, float tick) {}
        //?} else {
        /*@Override public void renderBackground(GuiGraphics g) {}
        *///?}
    }

    private int drawAffiliations(GuiGraphics g, int x, int y, int w) {
        int start = y;
        y = drawFactionStanding(g, x, y, w);
        var governing = snapshot.organizations().stream().filter(CharterSnapshotS2CPayload.Organization::governing).findFirst().orElse(null);
        if (governing != null) {
            y = drawActions(g, x, y, w, governing.actions().stream().filter(a -> !managementAction(a)).toList(), governing.id());
            y += 7;
            y = drawRoles(g, x, y, w, governing);
            y = information(g, x, y, w, tr("joining"), governing.admission().component());
        } else y = information(g, x, y, w, tr("tab.members"), tr("members_unavailable"));
        if (snapshot.civic() != null && snapshot.civic().controlsGovernment()) y = drawCivicOffices(g, x, y, w);
        var memberships = snapshot.organizations().stream().filter(o -> !o.governing() && o.relationship().equals("active")).toList();
        if (!memberships.isEmpty()) {
            section(g, x, y, w, tr("your_organizations"), Component.empty()); y += 18;
            for (var organization : memberships) {
                organizationRow(g, x, y, w, organization);
                mainPane.hit(x, y, w, 30, () -> { selectedOrganization = organization.id(); tab = 1; rebuildWidgets(); });
                y += 34;
            }
        }
        return y - start;
    }

    private void paper(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y + 2, x + w + 1, y + h + 2, 0x66000000);
        g.fill(x, y, x + w, y + h, Palette.CARD);
    }
    private void sheet(GuiGraphics g, int x, int y, int w, int h, Component title) {
        paper(g, x, y, w, h);
        small(g, title, x + 9, y + 8, w - 18, Palette.CARD_INK_DIM);
    }
    private void small(GuiGraphics g, Component text, int x, int y, int w, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0); g.pose().scale(.8f, .8f, 1);
        fit(g, text, 0, 0, (int) (w / .8f), color);
        g.pose().popPose();
    }
    private void fit(GuiGraphics g, Component text, int x, int y, int w, int color) {
        if (painting != null) painting.narration.append(text).append(". ");
        String raw = text.getString();
        String shown = font.width(raw) > w ? font.plainSubstrByWidth(raw, Math.max(0, w - font.width("…"))) + "…" : raw;
        g.drawString(font, shown, x, y, color, false);
    }
    private int textHeight(Component text, int w) { return Math.max(1, font.split(text, Math.max(20, w)).size()) * 11; }
    private int paragraph(GuiGraphics g, Component text, int x, int y, int w, int color) {
        if (painting != null) painting.narration.append(text).append(". ");
        for (var line : font.split(text, Math.max(20, w))) { g.drawString(font, line, x, y, color, false); y += 11; }
        return y;
    }
    private void icon(GuiGraphics g, String resource, int x, int y, float scale) {
        if (!IconArt.draw(g, resource, x, y, scale)) IconArt.draw(g, "minecraft:bell", x, y, scale);
    }

    private ScrollPaper painting;
    @FunctionalInterface private interface Content { int draw(GuiGraphics g, int x, int y, int w); }
    private record Hit(int x, int y, int w, int h, Runnable action) {}
    private final class ScrollPaper extends AbstractWidget {
        private final Content content;
        private final List<Hit> hits = new ArrayList<>();
        private double scroll;
        private int contentHeight, selectedHit;
        private int mouseX, mouseY;
        private boolean draggingThumb;
        private net.minecraft.network.chat.MutableComponent narration = Component.empty();
        ScrollPaper(int x, int y, int w, int h, Component title, Content content) {
            super(x, y, w, h, title); this.content = content;
        }
        void hit(int x, int y, int w, int h, Runnable action) { hits.add(new Hit(x, y, w, h, action)); }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
            mouseX = mx; mouseY = my;
            g.fill(getX(), getY(), getX() + width, getY() + height, Palette.DESK_DEEP);
            hits.clear(); narration = Component.empty().append(getMessage()).append(". ");
            g.enableScissor(getX(), getY(), getX() + width, getY() + height);
            painting = this;
            contentHeight = content.draw(g, getX() + 2, getY() + 2 - (int) scroll, width - 9) + 4;
            painting = null;
            scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - height)));
            selectedHit = Math.max(0, Math.min(selectedHit, hits.size() - 1));
            for (Hit hit : hits) if (isMouseOver(mx, my) && mx >= hit.x() && mx < hit.x() + hit.w()
                    && my >= hit.y() && my < hit.y() + hit.h()) g.renderOutline(hit.x(), hit.y(), hit.w(), hit.h(), Palette.BRASS);
            if (isFocused() && !hits.isEmpty()) {
                Hit hit = hits.get(selectedHit);
                g.renderOutline(hit.x(), hit.y(), hit.w(), hit.h(), Palette.BRASS_DEEP);
            }
            g.disableScissor();
            if (contentHeight > height) {
                int thumb = Math.max(10, height * height / contentHeight);
                int sy = getY() + (int) ((height - thumb) * scroll / (contentHeight - height));
                g.fill(getX() + width - 4, sy, getX() + width - 1, sy + thumb, Palette.BRASS);
            }
            if (isFocused()) g.renderOutline(getX(), getY(), width, height, Palette.BRASS);
        }
        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !isMouseOver(mx, my)) return false;
            if (mx >= getX() + width - 7 && contentHeight > height) { draggingThumb = true; dragThumb(my); return true; }
            for (Hit hit : hits) if (mx >= hit.x() && mx < hit.x() + hit.w() && my >= hit.y() && my < hit.y() + hit.h()) { hit.action().run(); return true; }
            return true;
        }
        private void dragThumb(double my) {
            int thumb = Math.max(10, height * height / Math.max(1, contentHeight));
            double fraction = (my - getY() - thumb / 2.0) / Math.max(1, height - thumb);
            scroll = Math.max(0, Math.min(1, fraction)) * Math.max(0, contentHeight - height);
        }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (button == 0 && draggingThumb) { dragThumb(my); return true; }
            return false;
        }
        @Override public boolean mouseReleased(double mx, double my, int button) { draggingThumb = false; return super.mouseReleased(mx, my, button); }
        private boolean scrollBy(double delta) {
            scroll = Math.max(0, Math.min(scroll - delta * 22, Math.max(0, contentHeight - height))); return true;
        }
        //? if >=1.21 {
        @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) { return isMouseOver(mx, my) && scrollBy(dy); }
        //?} else {
        /*@Override public boolean mouseScrolled(double mx, double my, double dy) { return isMouseOver(mx, my) && scrollBy(dy); }
        *///?}
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) return scrollBy((key == GLFW.GLFW_KEY_PAGE_DOWN ? -1 : 1) * height / 22.0);
            if (key == GLFW.GLFW_KEY_HOME) { scroll = 0; return true; }
            if (key == GLFW.GLFW_KEY_END) { scroll = Math.max(0, contentHeight - height); return true; }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                if (hits.isEmpty()) return scrollBy(key == GLFW.GLFW_KEY_DOWN ? -1 : 1);
                selectedHit = Math.max(0, Math.min(hits.size() - 1, selectedHit + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
                Hit hit = hits.get(selectedHit);
                if (hit.y() < getY()) scroll += hit.y() - getY();
                else if (hit.y() + hit.h() > getY() + height) scroll += hit.y() + hit.h() - getY() - height;
                return true;
            }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) && !hits.isEmpty()) { hits.get(selectedHit).action().run(); return true; }
            return super.keyPressed(key, scan, modifiers);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, narration);
            output.add(NarratedElementType.USAGE, tr("scroll_help"));
        }
    }

    private void identity(GuiGraphics g, Component name, Component subtitle) {
        int x = left + 16, y = top + 10;
        g.fill(x, y, x + 38, y + 38, Palette.WELL);
        var emblem = snapshot.heraldry().stream().filter(h -> h.actor().startsWith("polity:")).findFirst().orElse(null);
        if (emblem == null || emblem.revision() == 0 || minecraft.level == null) icon(g, "minecraft:bell", x + 3, y + 3, 2);
        else {
            var banner = com.aetherianartificer.townstead.politics.heraldry.EmblemItems.banner(minecraft.level.registryAccess(), com.aetherianartificer.townstead.politics.heraldry.EmblemRecipe.safe(emblem.recipe()));
            g.pose().pushPose(); g.pose().translate(x + 3, y + 3, 0); g.pose().scale(2, 2, 2); g.renderItem(banner, 0, 0); g.pose().popPose();
        }
        int textX = x + 48, textW = panelW - 82;
        if (isRecord() && snapshot.heraldry().stream().anyMatch(h -> h.actor().startsWith("polity:") && h.editable())) textW -= 110;
        g.pose().pushPose();
        g.pose().translate(textX, y + 4, 0); g.pose().scale(1.25f, 1.25f, 1);
        fit(g, name, 0, 0, (int) (textW / 1.25f), Palette.LABEL_LIGHT);
        g.pose().popPose();
        fit(g, subtitle, textX, y + 24, textW, Palette.BRASS);
        if (isRecord()) fit(g, tr("local_context", snapshot.settlement()), textX, y + 35, panelW - 82, Palette.LABEL_WARM);
    }

    private void renderUnavailable(GuiGraphics g) {
        identity(g, Component.translatable("charter.townstead.unavailable"), Component.empty());
        int x = left + 16, y = top + 86, w = panelW - 32, h = panelH - 124;
        paper(g, x, y, w, h);
        wrap(g, Component.literal(snapshot.message().isBlank() ? tr("unavailable").getString() : snapshot.message()),
                x + 18, y + 24, w - 36, Palette.INK_BAD);
        wrap(g, Component.translatable("charter.townstead.unavailable_not_unfounded"),
                x + 18, y + 60, w - 36, Palette.CARD_INK_DIM);
    }

    private void donut(GuiGraphics g, int cx, int cy, int radius) {
        if (currentCensus().population() <= 0 || currentCensus().groups().isEmpty()) {
            for (int yy = -radius; yy <= radius; yy++) for (int xx = -radius; xx <= radius; xx++) {
                double d = Math.sqrt(xx * xx + yy * yy);
                if (d >= radius - 5 && d <= radius) g.fill(cx + xx, cy + yy, cx + xx + 1, cy + yy + 1, 0xFFB7A783);
            }
            return;
        }
        double[] limits = new double[currentCensus().groups().size()];
        double total = 0;
        for (int i = 0; i < limits.length; i++) { total += currentCensus().groups().get(i).count(); limits[i] = total / currentCensus().population() * Math.PI * 2; }
        for (int yy = -radius; yy <= radius; yy++) for (int xx = -radius; xx <= radius; xx++) {
            double d = Math.sqrt(xx * xx + yy * yy);
            if (d < radius - 7 || d > radius) continue;
            double angle = Math.atan2(yy, xx) + Math.PI;
            int index = 0; while (index < limits.length - 1 && angle > limits[index]) index++;
            g.fill(cx + xx, cy + yy, cx + xx + 1, cy + yy + 1, currentCensus().groups().get(index).color());
        }
    }

    private void reviewRow(GuiGraphics g, String key, String value, int x, int y, int w) {
        int labelW = Math.min(126, w * 2 / 5);
        fit(g, Component.translatable(key), x, y, labelW - 8, Palette.CARD_INK_DIM);
        fit(g, Component.literal(value), x + labelW, y, w - labelW, Palette.CARD_INK);
        g.fill(x, y + 13, x + w, y + 14, 0x337A6540);
    }

    private void label(GuiGraphics g, String key, int x, int y) {
        g.drawString(font, Component.translatable(key), x, y, Palette.CARD_INK_DIM, false);
    }

    private void wrap(GuiGraphics g, Component text, int x, int y, int w, int color) {
        g.drawWordWrap(font, text, x, y, Math.max(20, w), color);
    }

    private void message(GuiGraphics g) {
        if (!snapshot.message().isBlank()) fit(g, Component.literal(snapshot.message()), left + 16,
                top + panelH - 37, panelW - 32, Palette.LABEL_WARM);
    }

    private static int next(int value, List<?> values) { return values.isEmpty() ? 0 : (value + 1) % values.size(); }
    private static <T> T selected(List<T> values, int index) { return values.get(Math.max(0, Math.min(index, values.size() - 1))); }

    @Override public boolean isPauseScreen() { return false; }
    //? if >=1.21 {
    @Override public void renderBackground(GuiGraphics g, int x, int y, float partialTick) {}
    //?} else {
    /*@Override public void renderBackground(GuiGraphics g) {}
    *///?}
}
