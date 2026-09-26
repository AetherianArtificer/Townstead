package com.aetherianartificer.townstead.client.gui.root;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Builds and wires the Roots tab's vertical-stack UI. Two native {@link Button}
 * tabs flip the column in place between two views that share the same regions;
 * the current view's tab is left {@code active=false} so it reads as the pressed,
 * greyed-out tab (matching MCA's own page-tab row):
 * <ul>
 *   <li><b>Root</b> — search box, grouped master list, description box.</li>
 *   <li><b>Genes</b> — the inherited-gene chips ({@link RootTraitsWidget}),
 *       full height; hovering a chip shows its full stat-block tooltip.</li>
 * </ul>
 * A single Apply row sits below both. No modal, so there is only ever one screen
 * and one background. The host adds the returned widgets via
 * {@code addRenderableWidget}; {@code onApply} sends the set packet.
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

    public record Widgets(Button tabRoot, Button tabGenes, EditBox search, RootListWidget list,
                          RootDescriptionWidget description, RootTraitsWidget master, Button apply) {}

    public static Widgets build(Minecraft mc, int x, int y, int w, int h, int target,
                                Consumer<String> onApply, Consumer<RootCatalogEntry> onPreview) {
        final int gap = 2, tabH = 20, searchH = 18, applyH = 20;

        int contentTop = y + tabH + gap;
        int applyTop = y + h - applyH;
        int contentBottom = applyTop - gap;   // content stops a gap above Apply

        // Root view: search / list / description.
        int listTop = contentTop + searchH + gap;
        int oUsable = contentBottom - listTop;
        int listH = Math.max(40, (int) (oUsable * 0.42));
        int descTop = listTop + listH + gap;
        int descH = contentBottom - descTop;

        int halfW = (w - gap) / 2;

        // EditBox draws its border 1px outside its bounds, so inset it by 1px and shrink
        // by 2px to make its outer edge sit flush with the panels below (which fill [x, x+w]).
        EditBox search = new EditBox(mc.font, x + 1, contentTop + 1, w - 2, searchH - 2,
                Component.translatable("townstead.origin.search"));
        search.setHint(Component.translatable("townstead.origin.search"));

        RootListWidget list = new RootListWidget(mc, x, w, listH, listTop, target);
        RootDescriptionWidget description = new RootDescriptionWidget(x, descTop, w, descH);
        // Genes view: the chips fill the whole content region (no detail pane).
        RootTraitsWidget master = new RootTraitsWidget(x, contentTop, w, contentBottom - contentTop);

        Button apply = Button.builder(Component.translatable("townstead.origin.apply"), b -> {
            RootCatalogEntry sel = list.selectedRoot();
            if (sel != null) onApply.accept(sel.id());
        }).pos(x, applyTop).size(w, applyH).build();
        apply.active = false;

        final RootCatalogEntry[] selectedRef = { null };
        final boolean[] previewReady = { false };   // suppress preview during the initial auto-select
        final Runnable[] mode = new Runnable[2];

        Button tabRoot = Button.builder(Component.translatable("townstead.origin.tab_origin"),
                b -> mode[0].run()).pos(x, y).size(halfW, tabH).build();
        Button tabGenes = Button.builder(Component.translatable("townstead.origin.tab_genes"),
                b -> mode[1].run()).pos(x + halfW + gap, y).size(w - halfW - gap, tabH).build();

        mode[0] = () -> {   // Root view
            search.visible = true;
            list.setShown(true);
            description.visible = true;
            master.visible = false;
            tabRoot.active = false;                       // current tab reads as pressed
            tabGenes.active = selectedRef[0] != null;
        };
        mode[1] = () -> {   // Genes view
            search.visible = false;
            search.setFocused(false);
            list.setShown(false);
            description.visible = false;
            master.visible = true;
            if (selectedRef[0] != null) master.setRoot(selectedRef[0]);
            tabRoot.active = true;
            tabGenes.active = false;                         // current tab reads as pressed
        };

        list.setOnSelect(entry -> {
            description.setRoot(entry);
            apply.active = entry != null;
            selectedRef[0] = entry;
            tabGenes.active = entry != null && !master.visible;
            if (previewReady[0] && entry != null) onPreview.accept(entry);
        });
        search.setResponder(list::setFilter);

        mode[0].run();   // start on the Root view

        list.rebuild();
        String current = list.currentRootId();
        for (RootCatalogEntry e : RootCatalogClient.origins()) {
            // A blocked current root has no row to select; the target keeps it until a new pick applies.
            if (e.id().equals(current) && e.listedFor(target == com.aetherianartificer.townstead.root.RootSetC2SPayload.SELF)) {
                list.choose(e);
                break;
            }
        }
        list.scrollToSelected();   // open scrolled to the target's current origin
        previewReady[0] = true;   // user selections from here on drive the live preview

        return new Widgets(tabRoot, tabGenes, search, list, description, master, apply);
    }
}
