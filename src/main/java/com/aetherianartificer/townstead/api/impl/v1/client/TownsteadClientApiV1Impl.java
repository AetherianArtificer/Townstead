package com.aetherianartificer.townstead.api.impl.v1.client;

import com.aetherianartificer.townstead.api.v1.client.ChoicePanelPaint;
import com.aetherianartificer.townstead.api.v1.client.ChoiceRow;
import com.aetherianartificer.townstead.api.v1.client.TownsteadClientApiV1;
import com.aetherianartificer.townstead.api.v1.event.Subscription;
import com.aetherianartificer.townstead.client.gui.dialogue.EmotionTagOverrides;
import com.aetherianartificer.townstead.client.gui.dialogue.RpgDialogueScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.Consumer;

public final class TownsteadClientApiV1Impl implements TownsteadClientApiV1 {
    private static final int API_VERSION = 1;
    private static final int API_REVISION = 1;

    @Override
    public int getApiVersion() {
        return API_VERSION;
    }

    @Override
    public int getApiRevision() {
        return API_REVISION;
    }

    @Override
    public boolean isDialogueScreen(Screen screen) {
        return screen instanceof RpgDialogueScreen dialogue && dialogue.apiChoicesVisible();
    }

    @Override
    public List<ChoiceRow> visibleChoices(Screen screen) {
        return screen instanceof RpgDialogueScreen dialogue ? dialogue.apiVisibleChoices() : List.of();
    }

    @Override
    public boolean selectChoice(Screen screen, int index) {
        return screen instanceof RpgDialogueScreen dialogue && dialogue.apiSelectChoice(index);
    }

    @Override
    public void registerEmotionTagNamespace(String namespace) {
        EmotionTagOverrides.registerNamespace(namespace);
    }

    @Override
    public Subscription onChoicePanelPainted(Consumer<ChoicePanelPaint> painter) {
        return ChoicePanelPaintHooks.subscribe(painter);
    }
}
