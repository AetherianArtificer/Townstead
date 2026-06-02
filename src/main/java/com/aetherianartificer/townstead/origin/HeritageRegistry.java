package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side registry of {@link HeritageProfile}s plus the resolution that turns
 * a villager's {@link Heritage} vector into a displayed race name. Populated by
 * {@link HeritageJsonLoader}.
 *
 * <p>Resolution: the highest-priority matching profile wins; with none, a near-pure
 * heritage uses its dominant ancestry's own name, and any other blend gets a
 * generated "A-B" label from its two largest shares, so nothing is ever nameless.</p>
 */
public final class HeritageRegistry {

    /** A heritage near or above this single-ancestry share is treated as that pure ancestry. */
    private static final float PURE_THRESHOLD = 0.95f;

    private static volatile Map<ResourceLocation, HeritageProfile> ENTRIES = Map.of();

    private HeritageRegistry() {}

    /** A resolved race name and (optional) demonym for a heritage. */
    public record Resolved(Component displayName, @Nullable Demonym demonym) {}

    static void replaceAll(Map<ResourceLocation, HeritageProfile> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
    }

    @Nullable
    public static HeritageProfile byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static List<HeritageProfile> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }

    /**
     * The displayed race name for a heritage. Falls back to {@code originDisplayName}
     * (the founder origin's name) when the heritage is empty or unresolvable, so a
     * freshly-seeded founder still reads correctly before its vector is meaningful.
     */
    public static Resolved resolve(Heritage heritage, Component originDisplayName) {
        if (heritage == null || heritage.isEmpty()) {
            return new Resolved(originDisplayName, null);
        }

        HeritageProfile best = null;
        for (HeritageProfile profile : ENTRIES.values()) {
            if (!profile.matches(heritage)) continue;
            if (best == null || profile.priority() > best.priority()) best = profile;
        }
        if (best != null) return new Resolved(best.displayName(), best.demonym());

        // No named blend matched. A near-pure villager keeps its founder origin/lineage
        // name (a pure "Dark Elf" stays "Dark Elf", not the coarser ancestry "Elf"); a
        // genuine blend with no authored profile gets a generated "A-B" label.
        List<ResourceLocation> ranked = heritage.ranked();
        ResourceLocation dominant = ranked.isEmpty() ? null : ranked.get(0);
        if (dominant != null && heritage.fractionOf(dominant) >= PURE_THRESHOLD) {
            return new Resolved(originDisplayName, null);
        }
        return new Resolved(generatedBlendName(ranked), null);
    }

    /** "Human-Elf" from the two largest ancestry shares; single-name when only one resolves. */
    private static Component generatedBlendName(List<ResourceLocation> ranked) {
        List<Component> names = new ArrayList<>(2);
        for (ResourceLocation id : ranked) {
            Ancestry ancestry = AncestryRegistry.byId(id);
            names.add(ancestry != null ? ancestry.displayName() : Component.literal(id.getPath()));
            if (names.size() == 2) break;
        }
        if (names.isEmpty()) return Component.literal("Mixed");
        if (names.size() == 1) return names.get(0);
        return Component.empty().append(names.get(0)).append(Component.literal("-")).append(names.get(1));
    }
}
