package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.List;

/**
 * A racial resource meter (mana, blood, charge, ...) keyed by the gene id. Holds a
 * value in {@code [min,max]} that regenerates {@code regen} per {@code regen_interval}
 * ticks. Active abilities can cost it, and the {@code change_resource} action moves
 * it. The value persists server-side through {@code ResourceValues} and reaches the player's HUD
 * through {@code ResourceSyncS2CPayload} into {@code ResourceHudOverlay}. The meter is not
 * Root-only: any power source can express one, career skills included.
 *
 * <p>{@code on_reach} runs an entity action on the holder when the meter crosses a threshold upward
 * (a charge filling to full firing a buff); {@code then: reset} drops the meter back to its start
 * after firing, the spend half of a charge-and-spend loop.</p>
 *
 * <p>JSON: {@code { "type":"pheno:resource", "min":0, "max":100,
 * "start":100, "regen":1, "regen_interval":20, "color":"#3FA0FF",
 * "display":{"shape":"squircle","fill_mode":"segmented","color_theme":"townstead:arcane"} }}</p>
 */
public final class ResourceGeneType implements GeneType {

    public static final String KEY = "pheno:resource";

    public record Instance(int min, int max, int start, int regen, int regenInterval,
                           int color,
                           boolean persistOnDeath, ResourceDisplay resourceDisplay,
                           List<ReachHook> onReach)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        int min = GsonHelper.getAsInt(json, "min", 0);
        int max = Math.max(min + 1, GsonHelper.getAsInt(json, "max", 100));
        int start = Math.max(min, Math.min(max, GsonHelper.getAsInt(json, "start", max)));
        int regen = GsonHelper.getAsInt(json, "regen", 1);
        int regenInterval = Math.max(1, GsonHelper.getAsInt(json, "regen_interval", 20));
        ResourceDisplay display = ResourceDisplay.parse(json);
        String colorRaw = GsonHelper.getAsString(json, "color", "#3FA0FF");
        int color = parseHex(colorRaw, 0x3FA0FF);
        boolean persistOnDeath = GsonHelper.getAsBoolean(json, "persist_on_death", false);
        List<ReachHook> onReach = json.has("on_reach") ? ReachHook.parseList(json.get("on_reach")) : List.of();
        return new Instance(min, max, start, regen, regenInterval, color,
                persistOnDeath, display, onReach);
    }

    private static int parseHex(String raw, int fallback) {
        String s = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
