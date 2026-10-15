package com.aetherianartificer.townstead.client.gui.root;

import com.aetherianartificer.townstead.client.gui.quest.QuestIconButton;
import com.aetherianartificer.townstead.client.gui.quest.QuestIcons;
import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.RootSetC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and wires the Roots tab. Two native {@link Button} tabs flip the column in place; the
 * current view's tab is left {@code active=false} so it reads as pressed, like MCA's own page tabs:
 * <ul>
 *   <li><b>Root</b>: a summary of what is on show (art, name, backstory, facts, powers), with
 *       <b>Browse roots...</b> to open {@link RootBrowseScreen} and <b>Random</b> to try another.
 *       A mix shows as one too, and Random becomes <b>Roll again</b>.</li>
 *   <li><b>Genes</b>: the target's own genes ({@link HeritageGenesWidget}) while they are shown as
 *       they are; a single root on preview shows its inherited-gene chips ({@link RootTraitsWidget}).
 *       A mix on preview has none to show, since its genes are rolled on Apply.</li>
 * </ul>
 * The tab opens on the target as it is; anything else on show is a founding from its
 * {@link RootEditSession}, and Apply, below both, gives the target exactly that one. With a single root
 * to pick there is nothing to choose, so the tab is only the summary.
 */
public final class RootPicker {

    private RootPicker() {}

    /**
     * Returns the page list with {@code "origins"} inserted right after {@code "general"}
     * (so the tab sits between General and Body), or appended if there's no General page.
     * Idempotent: if {@code "origins"} is already present the list is returned unchanged.
     */
    public static String[] insertRootsPage(String[] pages) {
        for (String page : pages) {
            if ("origins".equals(page)) return pages;
        }
        String[] out = new String[pages.length + 1];
        int i = 0;
        boolean inserted = false;
        for (String page : pages) {
            out[i++] = page;
            if (!inserted && "general".equals(page)) {
                out[i++] = "origins";
                inserted = true;
            }
        }
        if (!inserted) out[i] = "origins";
        return out;
    }

    /** The tab's widgets, for the host to add with {@code addRenderableWidget}. */
    public record Widgets(List<AbstractWidget> all) {}

    public static Widgets build(Minecraft mc, Screen parent, @Nullable LivingEntity model,
                                int x, int y, int w, int h, RootEditSession session) {
        final int gap = 2, tabH = 20, buttonH = 20;
        boolean self = session.target() == RootSetC2SPayload.SELF;

        List<RootCatalogEntry> listed = new ArrayList<>();
        for (RootCatalogEntry e : RootCatalogClient.origins()) {
            if (e.listedFor(self)) listed.add(e);
        }
        String currentId = currentRootId(session.target());
        // A blocked current root is still shown; there is a choice whenever another root is on offer.
        boolean choice = listed.size() > 1 || (listed.size() == 1 && !listed.get(0).id().equals(currentId));

        int contentTop = y + tabH + gap;
        int applyTop = y + h - buttonH;
        int browseTop = applyTop - gap - buttonH;
        int summaryBottom = choice ? browseTop - gap : y + h;
        int halfW = (w - gap) / 2;

        RootSummaryWidget summary = new RootSummaryWidget(x, contentTop, w, summaryBottom - contentTop);
        int genesH = (choice ? applyTop - gap : y + h) - contentTop;
        RootTraitsWidget master = new RootTraitsWidget(x, contentTop, w, genesH);
        HeritageGenesWidget own = new HeritageGenesWidget(x, contentTop, w, genesH);
        own.visible = false;

        Button apply = Button.builder(Component.translatable("townstead.origin.apply"), b -> session.apply())
                .pos(x, applyTop).size(w, buttonH).build();
        int randomW = Math.max(44, w / 3);
        Button browse = new QuestIconButton(x, browseTop, w - randomW - gap, buttonH,
                Component.translatable("townstead.root.browse.open"),
                b -> mc.setScreen(new RootBrowseScreen(parent, self, model, session)),
                QuestIcons.Icon.DETAILS);
        Button random = Button.builder(Component.translatable("townstead.root.random"), b -> {
            RootEditSession.Choice shown = session.shownChoice();
            if (shown == null) return;
            if (shown.mix() != null) {
                session.roll(shown);
                return;
            }
            if (listed.size() < 2) return;
            RootCatalogEntry pick;
            do {
                pick = listed.get(mc.level != null ? mc.level.random.nextInt(listed.size())
                        : (int) (Math.random() * listed.size()));
            } while (pick.id().equals(shown.rootId()));
            session.want(RootEditSession.Choice.root(pick.id()));
        }).pos(x + w - randomW, browseTop).size(randomW, buttonH).build();

        final Runnable[] mode = new Runnable[2];
        Button tabRoot = Button.builder(Component.translatable("townstead.origin.tab_origin"),
                b -> mode[0].run()).pos(x, y).size(halfW, tabH).build();
        Button tabGenes = Button.builder(Component.translatable("townstead.origin.tab_genes"),
                b -> mode[1].run()).pos(x + halfW + gap, y).size(w - halfW - gap, tabH).build();

        // What is on show: the founding when one is, else the target as it is.
        final RootEditSession.Choice[] last = { null };
        final RootCatalogEntry[] single = { null };
        final boolean[] genesView = { false };
        // The target as they are, not a founding on preview: their own genes can show.
        final boolean[] asIs = { false };
        Runnable showGenes = () -> {
            boolean ownGenes = asIs[0];
            own.visible = genesView[0] && ownGenes;
            master.visible = genesView[0] && !ownGenes;
            if (own.visible) own.show(targetUuid(mc, session.target()));
            if (master.visible) master.setRoot(single[0]);
        };
        Runnable refresh = () -> {
            RootEditSession.Choice shown = session.shownChoice();
            RootEditSession.Founding founding = session.shown();
            RootEditSession.Identity identity = session.identity();
            single[0] = shown != null && shown.mix() == null ? RootCatalogClient.origin(shown.rootId()) : null;
            boolean wasAsIs = asIs[0];
            asIs[0] = shown != null && founding == null;
            if (shown == null) {
                summary.setStatus(Component.translatable("townstead.root.loading"));
            } else {
                summary.setStatus(null);
                if (!shown.same(last[0]) || wasAsIs != asIs[0]) {
                    if (shown.mix() != null) {
                        String name = founding != null ? founding.name() : identity != null ? identity.name() : "";
                        String backstory = founding != null ? founding.backstory() : identity != null ? identity.backstory() : "";
                        summary.setMix(shown.mix(), name, backstory);
                    } else {
                        summary.setRoot(single[0]);
                    }
                    showGenes.run();
                }
            }
            last[0] = shown;
            apply.active = session.canApply();
            random.active = shown != null && !session.rolling();
            browse.active = shown != null;
            tabGenes.active = (asIs[0] || single[0] != null) && !genesView[0];
            random.setMessage(Component.translatable(shown != null && shown.mix() != null
                    ? "townstead.root.roll" : "townstead.root.random"));
        };
        mode[0] = () -> {   // Root view
            summary.visible = true;
            browse.visible = choice;
            random.visible = choice && listed.size() > 1;
            genesView[0] = false;
            showGenes.run();
            tabRoot.active = false;
            tabGenes.active = asIs[0] || single[0] != null;
        };
        mode[1] = () -> {   // Genes view
            summary.visible = false;
            browse.visible = false;
            random.visible = false;
            genesView[0] = true;
            showGenes.run();
            tabRoot.active = true;
            tabGenes.active = false;
        };
        apply.visible = choice;

        mode[0].run();
        refresh.run();
        session.onChange(refresh);
        // The editor rebuilt the tab and reset its model; the founding on show goes back on it as it was.
        session.redisplay();

        return new Widgets(List.of(tabRoot, tabGenes, summary, master, own, browse, random, apply));
    }

    @Nullable
    private static java.util.UUID targetUuid(Minecraft mc, int target) {
        if (target == RootSetC2SPayload.SELF) return mc.player == null ? null : mc.player.getUUID();
        net.minecraft.world.entity.Entity entity = mc.level == null ? null : mc.level.getEntity(target);
        return entity == null ? null : entity.getUUID();
    }

    static String currentRootId(int target) {
        String id = RootClientStore.get(target);
        return id.isEmpty() ? RootRegistry.DEFAULT_ID.toString() : id;
    }
}
