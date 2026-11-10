package com.aetherianartificer.townstead.client.gui.naming;

import com.aetherianartificer.townstead.client.naming.NameSyncClient;
import com.aetherianartificer.townstead.client.naming.NamingCatalogClient;
import com.aetherianartificer.townstead.naming.NameClientStore;
import com.aetherianartificer.townstead.naming.NamingCatalogS2CPayload;
import com.aetherianartificer.townstead.naming.NamingTradition;
import com.aetherianartificer.townstead.villager.TownsteadEditorCommitPayload;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The row under a person's name: their surname, typed in place, and the tradition it comes from,
 * which opens {@link NamingScreen}. A typed surname goes to the server when the row is left.
 */
public final class NamingRow {

    /** The space between this row and the name row above it, which editors shift their rows down by. */
    public static final int SHIFT = 20;
    /** MCA's name row: a field of this width, then its Random button. This row lines up with it. */
    private static final int NAME_FIELD_W = 116;
    private static final int NAME_BUTTON_W = 56;

    /** The row on screen, so the screens hosting it can commit without holding it. */
    private static NamingRow active;

    private final NamingScreen.Target target;
    private EditBox surname;
    private Button tradition;
    private boolean dirty;

    private NamingRow(NamingScreen.Target target) {
        this.target = target;
    }

    public static boolean available() {
        try {
            return com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.NAMING);
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** Commits what the row on screen holds. Safe to call with no row. */
    public static void commitActive() {
        if (active != null) active.commit();
    }

    /**
     * Adds the row at {@code y}, and captions MCA's name field above it at {@code nameY} as the given
     * name. Both fields start at the same place, under a caption wide enough for either.
     */
    public static void add(Screen parent, NamingScreen.Target target, Font font, int x, int nameY, int y,
                           List<? extends GuiEventListener> existing, Consumer<AbstractWidget> widgets) {
        commitActive();
        NamingRow row = new NamingRow(target);
        active = row;
        Component caption = Component.translatable("gui.townstead.naming.surname_short");
        Component givenCaption = Component.translatable("gui.townstead.naming.given_short");
        int captionW = Math.max(font.width(caption), font.width(givenCaption)) + 4;
        for (GuiEventListener child : existing) {
            if (child instanceof EditBox name && name.getX() == x && name.getY() == nameY) {
                name.setX(x + captionW);
                name.setWidth(NAME_FIELD_W - captionW);
                widgets.accept(new StringWidget(x, nameY, captionW - 4, 18, givenCaption, font).alignRight());
                break;
            }
        }
        widgets.accept(new StringWidget(x, y, captionW - 4, 18, caption, font).alignRight());
        row.surname = new EditBox(font, x + captionW, y, NAME_FIELD_W - captionW, 18, caption);
        row.surname.setMaxLength(TownsteadEditorCommitPayload.MAX_NAME_LENGTH);
        row.surname.setHint(Component.translatable("gui.townstead.naming.none").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        row.show(NameClientStore.family(target.entityId()));
        row.surname.setResponder(value -> row.dirty = !value.equals(NameClientStore.family(target.entityId())));
        row.tradition = Button.builder(traditionLabel(target.entityId()),
                        b -> Minecraft.getInstance().setScreen(new NamingScreen(parent, target)))
                .bounds(x + NAME_FIELD_W + 1, y - 1, NAME_BUTTON_W, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.townstead.naming.open")))
                .build();
        row.applyState();
        NameSyncClient.watch(target.entityId(), row::onSynced);
        widgets.accept(row.surname);
        widgets.accept(row.tradition);
    }

    private void commit() {
        if (!dirty) return;
        dirty = false;
        TownsteadEditorCommitPayload payload = TownsteadEditorCommitPayload.familyNameOnly(target.uuid(), surname.getValue());
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    private void onSynced() {
        int id = target.entityId();
        if (NameClientStore.familyLocked(id)) dirty = false;
        if (!dirty) {
            show(NameClientStore.family(id));
            dirty = false;
        }
        tradition.setMessage(traditionLabel(id));
        applyState();
    }

    /** From the start of the name, since a long one is wider than the field. */
    private void show(String name) {
        surname.setValue(name);
        surname.setCursorPosition(0);
        surname.setHighlightPos(0);
    }

    private void applyState() {
        int id = target.entityId();
        surname.setEditable(!NameClientStore.familyLocked(id));
        surname.setTooltip(Tooltip.create(Component.translatable(
                surnameTooltip(NameClientStore.familyLocked(id), NameClientStore.rule(id)))));
    }

    static String surnameTooltip(boolean locked, NamingTradition.FamilyType rule) {
        if (locked) return "gui.townstead.naming.tooltip.locked";
        return "gui.townstead.naming.tooltip." + rule.name().toLowerCase(Locale.ROOT);
    }

    /** The culture or tradition this person is named by, or a prompt to choose one. */
    private static Component traditionLabel(int entityId) {
        String culture = NameClientStore.culture(entityId);
        String tradition = NameClientStore.tradition(entityId);
        for (NamingCatalogS2CPayload.Entry entry : NamingCatalogClient.entries()) {
            if (!culture.isEmpty() && entry.culture().equals(culture)) return Component.literal(pickName(entry));
        }
        return tradition.isEmpty() ? Component.translatable("gui.townstead.naming.choose") : NamingCatalogClient.label(tradition);
    }

    /** What a culture or tradition is called on screen. */
    static String pickName(NamingCatalogS2CPayload.Entry entry) {
        return entry.label().isEmpty() ? NamingCatalogClient.label(entry.id()).getString() : entry.label();
    }
}
