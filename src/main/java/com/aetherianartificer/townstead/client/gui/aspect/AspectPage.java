package com.aetherianartificer.townstead.client.gui.aspect;

import com.aetherianartificer.townstead.aspect.AspectC2SPayload;
import com.aetherianartificer.townstead.aspect.AspectS2CPayload;
import com.aetherianartificer.townstead.client.gui.character.CharacterTabStrip;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Aspects page of the villager editor and Destiny. A tab strip of None and every pickable
 * aspect picks which one the card shows; Apply makes them it. The looks of the aspect on show
 * (a werewolf's coat) wait for Apply when it is not theirs yet, and change at once when it is.
 *
 * <p>The server holds the answer. The page keeps the last one per target and draws from it, so the
 * editor rebuilding the page never blanks it; it asks again once per opening. Every change waits
 * for its reply, and the page is locked until all of them are back.</p>
 */
public final class AspectPage {
    public static final String PAGE = "aspects";
    private static final int STRIP_H = 20;
    private static final int GAP = 2;
    private static final int BUTTON_H = 20;

    /** The last answer for each target. */
    private static final Map<Integer, AspectS2CPayload> answers = new HashMap<>();
    /** One entry per request still unanswered, in order: whether it locks the page. */
    private static final Deque<Boolean> inFlight = new ArrayDeque<>();
    /** Redraws the open page; null when no Aspects page is open. */
    private static @Nullable Runnable rebuild;
    /** The target this opening has asked about. */
    private static @Nullable Integer askedFor;
    /** The aspect on show ("" for none), or null for what they are now. */
    private static @Nullable String shown;
    /** Looks picked for the aspect on show while it is not theirs yet, by option id. */
    private static final Map<String, Integer> staged = new HashMap<>();

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
     * Builds the page for {@code target} in the box x, y, w, h, handing each widget to {@code add}.
     * {@code redraw} rebuilds the page; it runs when an answer comes or the page changes.
     */
    public static void build(int target, int x, int y, int w, int h, Consumer<AbstractWidget> add, Runnable redraw) {
        rebuild = redraw;
        if (askedFor == null || askedFor != target) {
            askedFor = target;
            shown = null;
            staged.clear();
            send(new AspectC2SPayload(target, "", false), false);
        }
        AspectS2CPayload answer = answers.get(target);
        int cardTop = y + STRIP_H + GAP;
        if (answer == null) {
            add.accept(new AspectCard(x, cardTop, w, y + h - cardTop,
                    Component.translatable("townstead.aspect.loading"), false, null, List.of(), null));
            return;
        }

        boolean busy = waiting();
        String current = answer.current();
        String showing = shown != null && (shown.isEmpty() || answer.choice(shown) != null) ? shown : current;
        boolean same = showing.equals(current);

        List<CharacterTabStrip.Entry> entries = new ArrayList<>();
        entries.add(new CharacterTabStrip.Entry("", label("")));
        for (AspectS2CPayload.Choice choice : answer.options()) entries.add(new CharacterTabStrip.Entry(choice.id(), label(choice.id())));
        CharacterTabStrip strip = new CharacterTabStrip(x, y, w, STRIP_H, entries, showing, id -> {
            shown = id;
            staged.clear();
            redraw();
        });
        strip.setMarked(current);
        strip.active = !busy;
        add.accept(strip);

        List<AspectCard.Row> rows = new ArrayList<>();
        AspectS2CPayload.Choice choice = showing.isEmpty() ? null : answer.choice(showing);
        if (choice != null) {
            boolean canStyle = !busy && answer.styleAllowed() && (same || answer.allowed());
            for (AspectS2CPayload.Look look : choice.looks()) {
                int value = same ? look.value() : staged.getOrDefault(look.id(), look.value());
                rows.add(new AspectCard.Row(lookText(look, value), canStyle, step -> {
                    if (same) {
                        send(new AspectC2SPayload(target, "", false, look.id(), value + step), true);
                    } else {
                        staged.put(look.id(), Math.floorMod(value + step, look.count()));
                    }
                    redraw();
                }));
            }
        }

        String about = showing.isEmpty() ? "townstead.aspect.none.description" : key(showing) + ".description";
        Component notice = answer.allowed() ? null : Component.translatable(answer.locked());
        int cardBottom = answer.allowed() ? y + h - BUTTON_H - GAP : y + h;
        add.accept(new AspectCard(x, cardTop, w, cardBottom - cardTop, label(showing), same,
                I18n.exists(about) ? Component.translatable(about) : null, rows, notice));

        if (answer.allowed()) {
            Component text = Component.translatable(busy ? "townstead.aspect.applying"
                    : same ? "townstead.aspect.current" : "townstead.aspect.apply");
            AspectS2CPayload.Choice picked = choice;
            Button apply = Button.builder(text, b -> {
                send(new AspectC2SPayload(target, showing, true), true);
                if (picked != null) {
                    for (AspectS2CPayload.Look look : picked.looks()) {
                        Integer value = staged.get(look.id());
                        if (value != null && value != look.value()) {
                            send(new AspectC2SPayload(target, "", false, look.id(), value), true);
                        }
                    }
                }
                redraw();
            }).bounds(x, y + h - BUTTON_H, w, BUTTON_H).build();
            apply.active = !busy && !same;
            add.accept(apply);
        }
    }

    /** The page closed: stop redrawing it, and ask again next time it opens. */
    public static void closed() {
        rebuild = null;
        // A reply that never comes must not lock the page for good; a late one finds nothing to clear.
        inFlight.clear();
        askedFor = null;
        shown = null;
        staged.clear();
    }

    /** An answer from the server. */
    public static void accept(AspectS2CPayload payload) {
        inFlight.poll();
        answers.put(payload.entityId(), payload);
        // Once everything is back, the page follows what they are, unless Apply did not take.
        if (!waiting() && shown != null && shown.equals(payload.current())) {
            shown = null;
            staged.clear();
        }
        redraw();
    }

    private static boolean waiting() {
        return inFlight.contains(Boolean.TRUE);
    }

    private static void redraw() {
        if (rebuild != null) rebuild.run();
    }

    private static Component lookText(AspectS2CPayload.Look look, int value) {
        String valueKey = "townstead.aspect.option." + look.id() + "." + value;
        return I18n.exists(valueKey) ? Component.translatable(valueKey)
                : Component.translatable("townstead.aspect.option." + look.id(), value + 1, look.count());
    }

    private static Component label(String id) {
        return id.isEmpty() ? Component.translatable("townstead.aspect.none") : Component.translatable(key(id));
    }

    private static String key(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? id : "state." + rl.getNamespace() + "." + rl.getPath();
    }

    private static void send(AspectC2SPayload payload, boolean locks) {
        inFlight.add(locks);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
