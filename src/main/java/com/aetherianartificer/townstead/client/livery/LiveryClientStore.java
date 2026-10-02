package com.aetherianartificer.townstead.client.livery;

import com.aetherianartificer.townstead.livery.LiveryS2CPayload;
import com.aetherianartificer.townstead.livery.LiveryView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** The liveries this client has been told about, by entity network id, for the level it is in. */
public final class LiveryClientStore {
    private static final Map<Integer, LiveryView> VIEWS = new HashMap<>();
    private static ClientLevel level;

    private LiveryClientStore() {}

    public static void accept(LiveryS2CPayload payload) {
        sameLevel();
        if (payload.view() == null) VIEWS.remove(payload.entity());
        else VIEWS.put(payload.entity(), payload.view());
        LiveryRender.forget(payload.entity());
    }

    /** A livery shown on a figure that exists only in a screen, such as the heraldry desk's preview. */
    public static void preview(int entity, @Nullable LiveryView view) {
        sameLevel();
        if (view == null) VIEWS.remove(entity);
        else VIEWS.put(entity, view);
    }

    public static @Nullable LiveryView of(int entity) {
        sameLevel();
        return VIEWS.get(entity);
    }

    /** Entity ids are only unique within one level, so a new level starts empty. */
    private static void sameLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == level) return;
        level = current;
        VIEWS.clear();
        LiveryRender.forgetAll();
    }
}
