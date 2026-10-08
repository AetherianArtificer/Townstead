package com.aetherianartificer.townstead.api.impl.v1.client;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.api.v1.client.ChoicePanelPaint;
import com.aetherianartificer.townstead.api.v1.event.Subscription;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Painters that run after Townstead draws its dialogue choice panel. */
public final class ChoicePanelPaintHooks {
    private static final CopyOnWriteArrayList<Hook> HOOKS = new CopyOnWriteArrayList<>();

    private ChoicePanelPaintHooks() {}

    static Subscription subscribe(Consumer<ChoicePanelPaint> painter) {
        if (painter == null) throw new IllegalArgumentException("painter is required");
        Hook hook = new Hook(painter);
        HOOKS.add(hook);
        return hook;
    }

    public static boolean any() {
        return !HOOKS.isEmpty();
    }

    public static void fire(ChoicePanelPaint paint) {
        for (Hook hook : HOOKS) {
            try {
                hook.painter.accept(paint);
            } catch (Throwable t) {
                Townstead.LOGGER.debug("[TownsteadApi] choice panel painter threw: {}", t.toString());
            }
        }
    }

    private static final class Hook implements Subscription {
        private final Consumer<ChoicePanelPaint> painter;
        private volatile boolean active = true;

        Hook(Consumer<ChoicePanelPaint> painter) {
            this.painter = painter;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            active = false;
            HOOKS.remove(this);
        }
    }
}
