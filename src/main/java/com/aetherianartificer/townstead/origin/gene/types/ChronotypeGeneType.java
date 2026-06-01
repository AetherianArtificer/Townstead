package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.chronotype.ChronotypeCatalog;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeSet;

/**
 * A villager's daily sleep rhythm. The gene is a weighted pick-one over chronotype
 * variants; one is rolled at birth and carried, and fatigue/the shift UI read the
 * carried variant's {@code sleep_hours} window.
 *
 * <p>Variants are referenced by id from the shared {@link ChronotypeCatalog} (the common
 * early_bird/standard/night_owl/nocturnal vocabulary, written once) and a gene re-weights
 * them. A variant entry that carries its own {@code sleep_hours} instead defines a new
 * variant inline, local to that gene.</p>
 *
 * <p>{@code sleep_hours} is authored as clock hours (0 = midnight); stored as tick-hours
 * (0 = 6 AM, matching the fatigue clock and the shift grid).</p>
 */
public final class ChronotypeGeneType implements GeneType {

    public static final String KEY = "townstead_origins:chronotype";

    /** Sleep window as tick-hours (0 == 6 AM), sorted and de-duplicated. */
    public record Instance(int[] sleepHours) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        int[] hours = tickHours(json);
        return hours == null ? null : new Instance(hours);
    }

    /**
     * A variant carries its own {@code sleep_hours} (an inline definition) or, lacking one,
     * borrows the window of the same-id variant in the shared {@link ChronotypeCatalog}.
     */
    @Override
    public GeneInstance parseVariant(String variantId, JsonObject json, Map<String, String> lang) {
        int[] inline = tickHours(json);
        if (inline != null) return new Instance(inline);
        ChronotypeCatalog.Entry entry = ChronotypeCatalog.get(variantId);
        return entry == null ? null : new Instance(entry.sleepHours());
    }

    @Nullable
    @Override
    public Component variantLabel(String variantId) {
        ChronotypeCatalog.Entry entry = ChronotypeCatalog.get(variantId);
        return entry == null ? null : entry.label();
    }

    /** Parse a {@code sleep_hours} clock-hour array into sorted tick-hours; {@code null} if absent/empty. */
    @Nullable
    public static int[] tickHours(JsonObject json) {
        if (!json.has("sleep_hours") || !json.get("sleep_hours").isJsonArray()) return null;
        JsonArray arr = json.getAsJsonArray("sleep_hours");
        if (arr.isEmpty()) return null;
        TreeSet<Integer> ticks = new TreeSet<>();
        for (int i = 0; i < arr.size(); i++) {
            int clock = Math.floorMod(arr.get(i).getAsInt(), 24); // clock hour, 0 == midnight
            ticks.add(Math.floorMod(clock - 6, 24));              // tick hour, 0 == 6 AM
        }
        int[] out = new int[ticks.size()];
        int i = 0;
        for (int t : ticks) out[i++] = t;
        return out;
    }
}
