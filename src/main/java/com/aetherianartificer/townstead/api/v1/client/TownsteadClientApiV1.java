package com.aetherianartificer.townstead.api.v1.client;

import com.aetherianartificer.townstead.api.v1.event.Subscription;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Client-side integration surface, version 1. Only ever loaded on the client; the server never
 * references this class. Same contract as {@code TownsteadApiV1}.
 */
public interface TownsteadClientApiV1 {

    String IMPLEMENTATION = "com.aetherianartificer.townstead.api.impl.v1.client.TownsteadClientApiV1Impl";

    static TownsteadClientApiV1 get() {
        return ClientApiHolder.get();
    }

    int getApiVersion();

    int getApiRevision();

    /** True when {@code screen} is Townstead's dialogue screen and its choice panel is showing. */
    boolean isDialogueScreen(Screen screen);

    /** The choice rows currently visible on Townstead's dialogue screen, top to bottom. Empty otherwise. */
    List<ChoiceRow> visibleChoices(Screen screen);

    /** Selects a visible row by its index in {@link #visibleChoices} through Townstead's own routine. */
    boolean selectChoice(Screen screen, int index);

    /**
     * Adds a language namespace whose {@code lang/en_us.json} values may carry emotion tags, so
     * dialogue text from that namespace is styled like MCA's own.
     */
    void registerEmotionTagNamespace(String namespace);

    /** Called after Townstead paints its choice panel, with the rows it drew, so a mod can draw over them. */
    Subscription onChoicePanelPainted(Consumer<ChoicePanelPaint> painter);

    /**
     * Asks Townstead to number its choice rows, 1 to 9 top to bottom. Townstead draws them itself,
     * in the panel's own hand and gutter, so numbered choices look native rather than stamped on;
     * pair this with {@link #visibleChoices} and {@link #selectChoice} to act on a digit key.
     *
     * <p>{@code enabled} is read when the panel lays out, so a config toggle takes effect on the
     * next choice list rather than mid-frame. Pass {@code null} to stop numbering. Last caller wins.
     */
    default void setChoiceNumbering(BooleanSupplier enabled) {
    }

    /** The constant form of {@link #setChoiceNumbering(BooleanSupplier)}. */
    default void setChoiceNumbering(boolean numbered) {
        setChoiceNumbering(numbered ? () -> true : null);
    }
}
