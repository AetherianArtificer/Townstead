package com.aetherianartificer.townstead.client.gui.origin;

import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Builds and wires the Origins tab's vertical-stack UI — search box, grouped
 * master list, a description box, a traits box, and an Apply button — so both the
 * Villager Editor and Destiny mixins share one layout. The host adds the returned
 * widgets via {@code addRenderableWidget}; {@code onApply} (branch-specific packet
 * send) fires when Apply is pressed.
 */
public final class OriginPicker {

    private OriginPicker() {}

    public record Widgets(EditBox search, OriginListWidget list,
                          OriginDescriptionWidget description, OriginTraitsWidget traits, Button apply) {}

    public static Widgets build(Minecraft mc, int x, int y, int w, int h, int target, Consumer<String> onApply) {
        int searchH = 18;  // matches MCA's name field height
        int applyH = 20;   // matches MCA's Done button height
        int gap = 2;

        int contentTop = y + searchH + gap;
        int applyTop = y + h - applyH;
        int usable = applyTop - contentTop - gap * 3; // gaps between list/desc/traits and before apply
        int listH = Math.max(36, (int) (usable * 0.42));   // biggest
        int descH = Math.max(30, (int) (usable * 0.34));   // a bit more
        int traitsH = Math.max(22, usable - listH - descH); // smallest
        int listTop = contentTop;
        int descTop = listTop + listH + gap;
        int traitsTop = descTop + descH + gap;

        EditBox search = new EditBox(mc.font, x, y, w, searchH, Component.translatable("townstead.origin.search"));
        search.setHint(Component.translatable("townstead.origin.search"));

        OriginListWidget list = new OriginListWidget(mc, x, w, listH, listTop, target);
        OriginDescriptionWidget description = new OriginDescriptionWidget(x, descTop, w, descH);
        OriginTraitsWidget traits = new OriginTraitsWidget(x, traitsTop, w, traitsH);

        Button apply = Button.builder(Component.translatable("townstead.origin.apply"), b -> {
            OriginCatalogEntry sel = list.selectedOrigin();
            if (sel != null) onApply.accept(sel.id());
        }).pos(x, applyTop).size(w, applyH).build();
        apply.active = false;

        list.setOnSelect(entry -> {
            description.setOrigin(entry);
            traits.setOrigin(entry);
            apply.active = entry != null;
        });
        search.setResponder(text -> list.setFilter(text));

        list.rebuild();
        String current = list.currentOriginId();
        for (OriginCatalogEntry e : com.aetherianartificer.townstead.client.origin.OriginCatalogClient.origins()) {
            if (e.id().equals(current)) {
                list.choose(e);
                break;
            }
        }

        return new Widgets(search, list, description, traits, apply);
    }
}
