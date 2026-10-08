package com.aetherianartificer.townstead.temperature;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import java.util.OptionalDouble;

/** The local player's server measurement, also used by LSO's client-side handheld thermometer. */
public final class PlayerEnvironmentClient {
    private record Reading(int entityId, float celsius, long tick) {}
    private static final java.util.Map<Level, Reading> READINGS = new java.util.WeakHashMap<>();
    private PlayerEnvironmentClient() {}
    public static void accept(PlayerEnvironmentPayload payload) {
        var level = Minecraft.getInstance().level;
        var player = Minecraft.getInstance().player;
        if (level == null || player == null || player.getId() != payload.entityId()
                || !level.dimension().location().equals(payload.dimension()) || !Float.isFinite(payload.celsius())) return;
        READINGS.put(level, new Reading(payload.entityId(), payload.celsius(), level.getGameTime()));
    }
    public static OptionalDouble at(Level level, Entity entity) {
        Reading reading = READINGS.get(level);
        long now = level.getGameTime();
        return reading != null && entity.getId() == reading.entityId() && now >= reading.tick()
                && now - reading.tick() <= 40 ? OptionalDouble.of(reading.celsius()) : OptionalDouble.empty();
    }
    public static void clear() { READINGS.clear(); }
}
