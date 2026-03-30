package com.aetherianartificer.townstead.animation;

import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VillagerResponseAnimationClientStore {
    private static final Map<Integer, ActiveResponse> RESPONSES = new ConcurrentHashMap<>();

    private VillagerResponseAnimationClientStore() {}

    public static void set(int entityId, VillagerResponseAnimation animation, long startedAtGameTime, int durationTicks) {
        if (animation == null || durationTicks <= 0) {
            RESPONSES.remove(entityId);
            return;
        }

        RESPONSES.put(entityId, new ActiveResponse(animation, startedAtGameTime, durationTicks));
    }

    public static VillagerResponseAnimation getActive(int entityId) {
        ActiveResponse response = RESPONSES.get(entityId);
        if (response == null) return null;
        if (isExpired(response)) {
            RESPONSES.remove(entityId, response);
            return null;
        }
        return response.animation();
    }

    public static long getStartedAtGameTime(int entityId) {
        ActiveResponse response = RESPONSES.get(entityId);
        if (response == null || isExpired(response)) return 0L;
        return response.startedAtGameTime();
    }

    public static int getDurationTicks(int entityId) {
        ActiveResponse response = RESPONSES.get(entityId);
        if (response == null || isExpired(response)) return 0;
        return response.durationTicks();
    }

    public static float getNormalizedProgress(int entityId, float partialTick) {
        ActiveResponse response = RESPONSES.get(entityId);
        if (response == null) return -1.0f;
        if (isExpired(response)) {
            RESPONSES.remove(entityId, response);
            return -1.0f;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || response.durationTicks() <= 0) {
            return 0.0f;
        }

        float elapsed = (minecraft.level.getGameTime() - response.startedAtGameTime()) + partialTick;
        return Math.max(0.0f, Math.min(1.0f, elapsed / response.durationTicks()));
    }

    public static void remove(int entityId) {
        RESPONSES.remove(entityId);
    }

    public static void clear() {
        RESPONSES.clear();
    }

    private static boolean isExpired(ActiveResponse response) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return false;
        long now = minecraft.level.getGameTime();
        return now > (response.startedAtGameTime() + response.durationTicks());
    }

    private record ActiveResponse(VillagerResponseAnimation animation, long startedAtGameTime, int durationTicks) {
    }
}
