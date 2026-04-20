package com.aetherianartificer.townstead.spirit;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable registry of the named Spirits that can be expressed by a village.
 * Insertion order is load-bearing — it controls the render order of per-spirit
 * progress bars in the blueprint, the stable ordering used when picking top
 * spirits during classification, and the canonical pair ordering used when
 * looking up blend readout keys.
 *
 * Seven spirits for the MVP. Each Spirit carries the translation key for its
 * display name ("Nautical") and an ARGB color used to tint bars and
 * recognition particles. The former "adjective" form used in "Fishing Town"
 * style readouts has been replaced by per-tier descriptive names (see
 * {@link SpiritReadout#asComponent()}), so spirits no longer carry an
 * adjective key.
 */
public final class SpiritRegistry {
    public record Spirit(String id, String displayKey, int color, Item icon) {}

    private static final LinkedHashMap<String, Spirit> SPIRITS = new LinkedHashMap<>();

    static {
        register(new Spirit("nautical",    "townstead.spirit.nautical",    0xFF4A90B8, Items.FISHING_ROD));
        register(new Spirit("pastoral",    "townstead.spirit.pastoral",    0xFF8FBF6E, Items.WHEAT));
        register(new Spirit("martial",     "townstead.spirit.martial",     0xFFBF4A4A, Items.IRON_SWORD));
        register(new Spirit("scholar",     "townstead.spirit.scholar",     0xFF8A5EBF, Items.BOOK));
        register(new Spirit("industrious", "townstead.spirit.industrious", 0xFFBF8A3A, Items.ANVIL));
        register(new Spirit("commercial",  "townstead.spirit.commercial",  0xFFD9A14A, Items.EMERALD));
        register(new Spirit("tourism",     "townstead.spirit.tourism",     0xFFE85B8A, Items.COMPASS));
    }

    private static void register(Spirit s) {
        SPIRITS.put(s.id(), s);
    }

    private SpiritRegistry() {}

    public static Optional<Spirit> get(String id) {
        return Optional.ofNullable(SPIRITS.get(id));
    }

    public static List<Spirit> ordered() {
        return List.copyOf(SPIRITS.values());
    }

    public static boolean contains(String id) {
        return SPIRITS.containsKey(id);
    }

    /**
     * 0-based index of the given spirit id in registry insertion order, or -1
     * if unknown. Used to canonicalize blend pairs so
     * {@code pair(nautical, commercial)} and {@code pair(commercial, nautical)}
     * resolve to the same lang key.
     */
    public static int indexOf(String id) {
        int i = 0;
        for (String key : SPIRITS.keySet()) {
            if (key.equals(id)) return i;
            i++;
        }
        return -1;
    }
}
