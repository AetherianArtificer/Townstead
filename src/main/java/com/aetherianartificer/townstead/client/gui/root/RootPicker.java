package com.aetherianartificer.townstead.client.gui.root;

import com.aetherianartificer.townstead.client.gui.quest.QuestIconButton;
import com.aetherianartificer.townstead.client.gui.quest.QuestIcons;
import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.RootMix;
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
import java.util.function.Consumer;

/**
 * Builds and wires the Roots tab. Two native {@link Button} tabs flip the column in place; the
 * current view's tab is left {@code active=false} so it reads as pressed, like MCA's own page tabs:
 * <ul>
 *   <li><b>Root</b>: a summary of the root on show (art, name, backstory, facts, powers), with
 *       <b>Browse roots...</b> to open {@link RootBrowseScreen} and <b>Random</b> to try another.
 *       A mix chosen in the browser shows as one too, and Random becomes <b>Roll again</b>.</li>
 *   <li><b>Genes</b>: the inherited-gene chips ({@link RootTraitsWidget}), full height; hovering a
 *       chip shows its full stat-block tooltip.</li>
 * </ul>
 * Apply sits below both and commits the root or mix on show. With a single root to pick there is nothing
 * to choose, so the tab is only the summary.
 */
public final class RootPicker {

    private RootPicker() {}

    /** A root id or {@link RootMix} picked in the browser, previewed when the editor rebuilds the tab on return. */
    private static @Nullable Object pending;

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
                                int x, int y, int w, int h, int target,
                                Consumer<String> onApply, Consumer<RootCatalogEntry> onPreview,
                                Consumer<RootMix> onApplyMix, Consumer<RootMix> onPreviewMix) {
        final int gap = 2, tabH = 20, buttonH = 20;
        boolean self = target == RootSetC2SPayload.SELF;

        List<RootCatalogEntry> listed = new ArrayList<>();
        for (RootCatalogEntry e : RootCatalogClient.origins()) {
            if (e.listedFor(self)) listed.add(e);
        }
        String currentId = currentRootId(target);
        RootCatalogEntry current = RootCatalogClient.origin(currentId);
        // A blocked current root is still shown; there is a choice whenever another root is on offer.
        boolean choice = listed.size() > 1 || (listed.size() == 1 && !listed.get(0).id().equals(currentId));

        int contentTop = y + tabH + gap;
        int applyTop = y + h - buttonH;
        int browseTop = applyTop - gap - buttonH;
        int summaryBottom = choice ? browseTop - gap : y + h;
        int halfW = (w - gap) / 2;

        RootSummaryWidget summary = new RootSummaryWidget(x, contentTop, w, summaryBottom - contentTop);
        RootTraitsWidget master = new RootTraitsWidget(x, contentTop, w, (choice ? applyTop - gap : y + h) - contentTop);

        final RootCatalogEntry[] shown = { current };
        final RootMix[] shownMix = { null };
        Button apply = Button.builder(Component.translatable("townstead.origin.apply"), b -> {
            if (shownMix[0] != null) onApplyMix.accept(shownMix[0]);
            else if (shown[0] != null) onApply.accept(shown[0].id());
        }).pos(x, applyTop).size(w, buttonH).build();
        int randomW = Math.max(44, w / 3);
        Button browse = new QuestIconButton(x, browseTop, w - randomW - gap, buttonH,
                Component.translatable("townstead.root.browse.open"),
                b -> mc.setScreen(new RootBrowseScreen(parent, self, model, currentId, shown[0], shownMix[0],
                        new RootBrowseScreen.Callbacks(onPreview, onPreviewMix,
                                picked -> pending = picked.id(), mix -> pending = mix))),
                QuestIcons.Icon.DETAILS);
        final Runnable[] select = new Runnable[1];
        Button random = Button.builder(Component.translatable("townstead.root.random"), b -> {
            if (shownMix[0] != null) {
                onPreviewMix.accept(shownMix[0]);
                return;
            }
            if (listed.size() < 2) return;
            RootCatalogEntry pick;
            do {
                pick = listed.get(mc.level != null ? mc.level.random.nextInt(listed.size())
                        : (int) (Math.random() * listed.size()));
            } while (pick == shown[0]);
            shown[0] = pick;
            select[0].run();
            onPreview.accept(pick);
        }).pos(x + w - randomW, browseTop).size(randomW, buttonH).build();

        final Runnable[] mode = new Runnable[2];
        Button tabRoot = Button.builder(Component.translatable("townstead.origin.tab_origin"),
                b -> mode[0].run()).pos(x, y).size(halfW, tabH).build();
        Button tabGenes = Button.builder(Component.translatable("townstead.origin.tab_genes"),
                b -> mode[1].run()).pos(x + halfW + gap, y).size(w - halfW - gap, tabH).build();

        select[0] = () -> {
            // A mix's genes are rolled server-side, so its Genes view would only show one root's: off.
            if (shownMix[0] != null) summary.setMix(shownMix[0]);
            else summary.setRoot(shown[0]);
            if (master.visible && shown[0] != null) master.setRoot(shown[0]);
            apply.active = shownMix[0] != null || shown[0] != null && !shown[0].id().equals(currentId);
            tabGenes.active = shown[0] != null && shownMix[0] == null && !master.visible;
            random.setMessage(Component.translatable(shownMix[0] != null ? "townstead.root.roll" : "townstead.root.random"));
        };
        mode[0] = () -> {   // Root view
            summary.visible = true;
            browse.visible = choice;
            random.visible = choice && listed.size() > 1;
            master.visible = false;
            tabRoot.active = false;
            tabGenes.active = shown[0] != null && shownMix[0] == null;
        };
        mode[1] = () -> {   // Genes view
            summary.visible = false;
            browse.visible = false;
            random.visible = false;
            master.visible = true;
            if (shown[0] != null) master.setRoot(shown[0]);
            tabRoot.active = true;
            tabGenes.active = false;
        };
        apply.visible = choice;

        mode[0].run();
        select[0].run();

        Object returned = pending;
        pending = null;
        if (returned instanceof RootMix mix) {
            shownMix[0] = mix;
            shown[0] = RootCatalogClient.origin(mix.largest());
            select[0].run();
            onPreviewMix.accept(mix);
        }
        String picked = returned instanceof String id ? id : null;
        if (picked != null && !picked.equals(currentId)) {
            for (RootCatalogEntry e : listed) {
                if (!e.id().equals(picked)) continue;
                shown[0] = e;
                select[0].run();
                onPreview.accept(e);
                break;
            }
        }

        return new Widgets(List.of(tabRoot, tabGenes, summary, master, browse, random, apply));
    }

    static String currentRootId(int target) {
        String id = RootClientStore.get(target);
        return id.isEmpty() ? RootRegistry.DEFAULT_ID.toString() : id;
    }
}
