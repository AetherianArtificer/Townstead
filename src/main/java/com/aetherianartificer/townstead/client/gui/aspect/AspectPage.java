package com.aetherianartificer.townstead.client.gui.aspect;

import com.aetherianartificer.townstead.aspect.AspectC2SPayload;
import com.aetherianartificer.townstead.aspect.AspectS2CPayload;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Aspects page of the villager editor and Destiny: None, then every pickable aspect, one
 * button each. The current one is pressed down; when this player may not change it, none are
 * active. The server holds the answer, so the page asks when it opens and rebuilds when told.
 */
public final class AspectPage {
    public static final String PAGE = "aspects";
    private static final int ROW = 22;

    /** The last answer, kept until a page uses it. */
    private static @Nullable AspectS2CPayload fresh;
    /** Rebuilds the open page when an answer arrives; null when no Aspects page is open. */
    private static @Nullable Runnable rebuild;

    private AspectPage() {}

    /** Whether anything could be on offer: an aspect-giving mod is installed. */
    public static boolean available() {
        return ModCompat.isLoaded("vampirism") || ModCompat.isLoaded("werewolves");
    }

    /** {@code pages} with the Aspects page after {@code anchor} (appended if absent); idempotent. */
    public static String[] insertPage(String[] pages, String anchor) {
        for (String page : pages) if (PAGE.equals(page)) return pages;
        String[] out = new String[pages.length + 1];
        int i = 0;
        boolean inserted = false;
        for (String page : pages) {
            out[i++] = page;
            if (!inserted && anchor.equals(page)) {
                out[i++] = PAGE;
                inserted = true;
            }
        }
        if (!inserted) out[i] = PAGE;
        return out;
    }

    /**
     * Builds the page for {@code target} at x, y, width w, handing each button to {@code add}. Asks
     * the server first when there is no fresh answer; {@code onAnswer} reopens the page when it comes.
     */
    public static void build(int target, int x, int y, int w, Consumer<Button> add, Runnable onAnswer) {
        rebuild = onAnswer;
        AspectS2CPayload answer = fresh;
        if (answer == null || answer.entityId() != target) {
            send(new AspectC2SPayload(target, "", false));
            return;
        }
        fresh = null;
        List<String> rows = new ArrayList<>();
        rows.add("");
        rows.addAll(answer.options());
        for (int i = 0; i < rows.size(); i++) {
            String id = rows.get(i);
            Button button = Button.builder(label(id), b -> send(new AspectC2SPayload(target, id, true)))
                    .bounds(x, y + i * ROW, w, 20).build();
            String about = id.isEmpty() ? "townstead.aspect.none.description" : key(id) + ".description";
            if (I18n.exists(about)) button.setTooltip(Tooltip.create(Component.translatable(about)));
            button.active = answer.allowed() && !id.equals(answer.current());
            add.accept(button);
        }
        // The looks of their aspect, such as a werewolf's coat: "< Coat 3 of 11 >".
        int top = y + rows.size() * ROW + 6;
        for (int i = 0; i < answer.looks().size(); i++) {
            AspectS2CPayload.Look look = answer.looks().get(i);
            int rowY = top + i * ROW;
            String valueKey = "townstead.aspect.option." + look.id() + "." + look.value();
            Component name = I18n.exists(valueKey) ? Component.translatable(valueKey)
                    : Component.translatable("townstead.aspect.option." + look.id(), look.value() + 1, look.count());
            Button value = Button.builder(name, b -> {}).bounds(x + 22, rowY, w - 44, 20).build();
            value.active = false;
            Button prev = Button.builder(Component.literal("<"),
                    b -> send(new AspectC2SPayload(target, "", false, look.id(), look.value() - 1))).bounds(x, rowY, 20, 20).build();
            Button next = Button.builder(Component.literal(">"),
                    b -> send(new AspectC2SPayload(target, "", false, look.id(), look.value() + 1))).bounds(x + w - 20, rowY, 20, 20).build();
            prev.active = next.active = answer.styleAllowed();
            add.accept(prev);
            add.accept(value);
            add.accept(next);
        }
    }

    /** The page closed: stop rebuilding it. */
    public static void closed() {
        rebuild = null;
    }

    /** An answer from the server. */
    public static void accept(AspectS2CPayload payload) {
        fresh = payload;
        if (rebuild != null) rebuild.run();
    }

    private static Component label(String id) {
        return id.isEmpty() ? Component.translatable("townstead.aspect.none") : Component.translatable(key(id));
    }

    private static String key(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? id : "state." + rl.getNamespace() + "." + rl.getPath();
    }

    private static void send(AspectC2SPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
