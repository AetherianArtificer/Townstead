package com.aetherianartificer.townstead.temperature;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TemperatureClientStore {
    private static final Map<Integer, int[]> STATE = new ConcurrentHashMap<>();
    private static Runnable onChange;

    private TemperatureClientStore() {}

    public static void setOnChange(Runnable callback) {
        onChange = callback;
    }

    public static void clearOnChange() {
        onChange = null;
    }

    public static void set(int entityId, int bodyTenths, int ambientTenths, int flags) {
        STATE.put(entityId, new int[] {bodyTenths, ambientTenths, flags});
        if (onChange != null) onChange.run();
    }

    public static boolean has(int entityId) {
        return STATE.containsKey(entityId);
    }

    public static int getBodyTenths(int entityId) {
        int[] s = STATE.get(entityId);
        return s == null ? TemperatureData.tenths(TemperatureData.DEFAULT_NEUTRAL) : s[0];
    }

    public static int getAmbientTenths(int entityId) {
        int[] s = STATE.get(entityId);
        return s == null ? TemperatureData.tenths(TemperatureData.AMBIENT_REFERENCE) : s[1];
    }

    public static boolean isWet(int entityId) {
        int[] s = STATE.get(entityId);
        return s != null && (s[2] & TemperatureSyncPayload.FLAG_WET) != 0;
    }

    public static TemperatureData.Tier getTier(int entityId) {
        int[] s = STATE.get(entityId);
        return s == null ? TemperatureData.Tier.COMFORTABLE : TemperatureData.tierFromFlags(s[2]);
    }

    public static net.minecraft.network.chat.Component tooltip(int entityId, boolean fahrenheit) {
        var tier = getTier(entityId);
        var label = net.minecraft.network.chat.Component.translatable("townstead.temperature.icon.tooltip",
                TemperatureData.format(getBodyTenths(entityId), fahrenheit),
                net.minecraft.network.chat.Component.translatable(tier.getTranslationKey()),
                TemperatureData.format(getAmbientTenths(entityId), fahrenheit));
        if (isWet(entityId)) label.append(net.minecraft.network.chat.Component.translatable("townstead.temperature.wet"));
        if (isSeekingRelief(entityId)) label.append(net.minecraft.network.chat.Component.translatable("townstead.temperature.relief"));
        var core = getCoreTier(entityId);
        if (core.wantsRelief()) label.append(net.minecraft.network.chat.Component.translatable(
                core.isCold() ? "townstead.temperature.core_cold" : "townstead.temperature.core_hot"));
        return label.withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(core.isCrisis() ? core.getColor() : tier.getColor()));
    }

    public static boolean isSeekingRelief(int entityId) {
        int[] s = STATE.get(entityId);
        return s != null && (s[2] & TemperatureSyncPayload.FLAG_SEEKING_RELIEF) != 0;
    }

    public static TemperatureData.Tier getCoreTier(int entityId) {
        int[] s = STATE.get(entityId);
        return s == null ? TemperatureData.Tier.COMFORTABLE
                : TemperatureData.Tier.values()[Math.min(6, (s[2] >> 5) & 7)];
    }

    public static void remove(int entityId) {
        STATE.remove(entityId);
    }

    public static void clear() {
        STATE.clear();
    }
}
