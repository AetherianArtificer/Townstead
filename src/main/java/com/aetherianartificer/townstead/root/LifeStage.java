package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.loot.LootDrop;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * One entry in a {@link LifeCycle}'s ordered list.
 *
 * <p>{@code id} is the stable identifier the runtime uses (NBT, telemetry,
 * dialogue). {@code label} is the localized name shown to players (caterpillar,
 * elder, ...). {@code presentsAs} maps the stage onto the canonical axis so
 * MCA's hardcoded {@code == ADULT} gates still work for a root that calls
 * its adult stage "Butterfly".</p>
 *
 * <p>Two independent axes carry the time model:</p>
 * <ul>
 *   <li>{@code days} - how long the stage lasts in Townstead life days. Life
 *       days are the biological age counter, protected from calendar relabeling
 *       so changing the displayed date does not age villagers. Scaled
 *       per-villager by the Lifespan variance at spawn.</li>
 *   <li>{@code narrativeStart}/{@code narrativeEnd} — the <em>apparent</em> age
 *       range in "life years" this stage represents (e.g. adult 18 to 64). Display
 *       interpolates across it by progress, giving the dog-years age shown to
 *       players (the real elapsed game-time is far too small to be a headline).</li>
 * </ul>
 *
 * <p>{@code onEnd} controls what happens when the stage expires; null means
 * {@link StageEndAction#NEXT} for non-last entries and {@link StageEndAction#STAY}
 * for the last.</p>
 */
public record LifeStage(
        String id,
        Component label,
        CanonicalStage presentsAs,
        int days,
        float narrativeStart,
        float narrativeEnd,
        @Nullable StageEndAction onEnd,
        float scale,
        boolean explicitNarrative,
        // Optional rig id this stage renders as, overriding the species rig (e.g. an "egg" stage rendering
        // an egg model). Null/empty = use the species rig. Reaches the client per-root via the root
        // catalog (RootCatalogEntry.stageRigs).
        @Nullable String rig,
        // Server-side stage behavior flags (all default true). An "egg" stage sets them false: it can't
        // move (frozen AI), has no needs (hunger/thirst pinned), and can't be talked to (interaction
        // blocked). Resolved server-side from the gene, so no client sync.
        boolean mobile,
        boolean needs,
        boolean talkable,
        // Open, data-authored semantic tags for permissions and capabilities that vary by life
        // stage (for example adult admission or alcohol consumption). These are deliberately
        // separate from presentsAs: two stages may present as adults while supporting different
        // activities.
        Set<ResourceLocation> tags,
        // Items this stage drops when the villager (or a player of the origin) dies, ADDED on top of normal
        // drops (an egg stage drops a spider egg, an adult drops silk). Rolled + spawned server-side by
        // DeathLoot; never null (empty = no extra drops). Server-only, no client sync.
        List<LootDrop> deathLoot
) {
    public LifeStage {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("stage id is required");
        if (presentsAs == null) throw new IllegalArgumentException("presentsAs is required");
        if (days < 1) days = 1;
        if (narrativeStart < 0f) narrativeStart = 0f;
        if (narrativeEnd < narrativeStart) narrativeEnd = narrativeStart;
        if (scale <= 0f) scale = 1f;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        deathLoot = deathLoot == null ? List.of() : List.copyOf(deathLoot);
    }

    /**
     * Build a stage whose apparent-age range and model {@code scale} both default
     * from its canonical kind ({@code presents_as}). The common path: a human-like
     * cycle authors only pacing ({@code days}) and lets apparent age derive linearly
     * from days alive (see {@code explicitNarrative}).
     */
    public static LifeStage of(String id, Component label, CanonicalStage presentsAs,
                               int days, @Nullable StageEndAction onEnd) {
        return new LifeStage(id, label, presentsAs, days,
                presentsAs.defaultNarrativeStart(), presentsAs.defaultNarrativeEnd(), onEnd,
                presentsAs.defaultScale(), false, null, true, true, true,
                defaultTags(presentsAs), List.of());
    }

    /**
     * Compatibility defaults for life-cycle data authored before stage tags existed. Supplying an
     * explicit {@code tags} array (including an empty one) replaces these defaults in the parser.
     */
    public static Set<ResourceLocation> defaultTags(CanonicalStage stage) {
        if (stage != CanonicalStage.ADULT && stage != CanonicalStage.SENIOR) return Set.of();
        return Set.of(
                ResourceLocation.tryParse("townstead_lifecycle:adult"),
                ResourceLocation.tryParse("townstead_lifecycle:can_consume_alcohol")
        );
    }

    /** Apparent ("life years") age at {@code delta} (0..1) through this stage. */
    public float narrativeAgeAt(float delta) {
        float d = delta < 0f ? 0f : (delta > 1f ? 1f : delta);
        return narrativeStart + d * (narrativeEnd - narrativeStart);
    }
}
