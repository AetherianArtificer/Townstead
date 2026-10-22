package com.aetherianartificer.townstead.root;

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
 * heritage uses its founder's own name unless it mixes lineages (then "A-B" from its
 * two largest lineages), and any other blend gets a generated "A-B" label from its two
 * largest ancestry shares, so nothing is ever nameless.</p>
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

        HeritageProfile best = bestMatch(heritage);
        if (best != null) return new Resolved(best.displayName(), best.demonym());

        // No named blend matched. A near-pure villager keeps its founder assignment-profile/lineage
        // name (a pure "Moon Elf" stays "Moon Elf", not the coarser ancestry "Elf"); a
        // genuine blend with no authored profile gets a generated "A-B" label.
        List<ResourceLocation> ranked = heritage.ranked();
        ResourceLocation dominant = ranked.isEmpty() ? null : ranked.get(0);
        if (dominant != null && heritage.fractionOf(dominant) >= PURE_THRESHOLD) {
            // Wholly one ancestry but a mix of its lineages with no authored profile (a Fire Drake ×
            // Volt Drake): name the two largest lineages rather than passing for the founder's.
            List<ResourceLocation> lines = heritage.rankedLineages();
            if (lines.size() >= 2 && heritage.lineageFractionOf(lines.get(0)) < PURE_THRESHOLD) {
                return new Resolved(generatedLineageName(lines), null);
            }
            return new Resolved(originDisplayName, null);
        }
        return new Resolved(generatedBlendName(heritage, ranked), null);
    }

    /** The lineage carrying at least 70% of an ancestry's share, or null when none does. */
    @Nullable
    private static Lineage mainLineage(Heritage heritage, ResourceLocation ancestry) {
        float total = heritage.fractionOf(ancestry);
        if (total <= 0f) return null;
        for (ResourceLocation id : heritage.rankedLineages()) {
            Lineage lineage = LineageRegistry.byId(id);
            if (lineage == null || !ancestry.equals(lineage.ancestry())) continue;
            return heritage.lineageFractionOf(id) >= total * 0.7f ? lineage : null;
        }
        return null;
    }

    /** "Fire Drake-Volt Drake" from the two largest lineage shares. */
    private static Component generatedLineageName(List<ResourceLocation> ranked) {
        List<Component> names = new ArrayList<>(2);
        for (ResourceLocation id : ranked) {
            Lineage lineage = LineageRegistry.byId(id);
            names.add(lineage != null ? lineage.displayName() : Component.literal(id.getPath()));
            if (names.size() == 2) break;
        }
        return Component.empty().append(names.get(0)).append(Component.literal("-")).append(names.get(1));
    }

    /** Highest-priority authored profile matching this realized heritage, or null. */
    @Nullable
    public static HeritageProfile bestMatch(@Nullable Heritage heritage) {
        if (heritage == null || heritage.isEmpty()) return null;
        HeritageProfile best = null;
        for (HeritageProfile profile : ENTRIES.values()) {
            if (!profile.matches(heritage)) continue;
            if (best == null || profile.priority() > best.priority()) best = profile;
        }
        return best;
    }

    /**
     * "Human-Elf" from the two largest ancestry shares; single-name when only one resolves. An
     * ancestry carried mostly through one lineage is named by it ("Moon Elf-Feline", not
     * "Elf-Bestial").
     */
    private static Component generatedBlendName(Heritage heritage, List<ResourceLocation> ranked) {
        List<Component> names = new ArrayList<>(2);
        for (ResourceLocation id : ranked) {
            Lineage line = mainLineage(heritage, id);
            Ancestry ancestry = AncestryRegistry.byId(id);
            names.add(line != null ? line.displayName()
                    : ancestry != null ? ancestry.displayName() : Component.literal(id.getPath()));
            if (names.size() == 2) break;
        }
        if (names.isEmpty()) return Component.translatable("townstead.heritage.mixed");
        if (names.size() == 1) return names.get(0);
        return Component.empty().append(names.get(0)).append(Component.literal("-")).append(names.get(1));
    }
}
