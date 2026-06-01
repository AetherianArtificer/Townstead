package com.aetherianartificer.townstead.origin.chronotype;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shared vocabulary of chronotype variants (variant id → label + sleep window),
 * loaded from {@code data/<ns>/chronotype/<id>.json}. Every chronotype gene re-weights
 * these canonical variants by id; a gene may also define its own variant inline (then
 * it carries its own {@code sleep_hours} and doesn't consult this catalog).
 *
 * <p>Populated by {@link ChronotypeCatalogLoader} before {@code GeneJsonLoader}, so a
 * weight-only variant entry can resolve its window at gene-load time.</p>
 */
public final class ChronotypeCatalog {

    /** One catalog variant: resolved label and sleep window (tick-hours, 0 == 6 AM). */
    public record Entry(Component label, int[] sleepHours) {}

    private static volatile Map<String, Entry> ENTRIES = Map.of();

    private ChronotypeCatalog() {}

    static void replaceAll(Map<String, Entry> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
    }

    @Nullable
    public static Entry get(String id) {
        return id == null ? null : ENTRIES.get(id);
    }
}
