package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * The ordered list of {@link LifeStage}s an origin progresses through. Lives
 * on {@link Ancestry}/{@link Heritage}/{@link Origin} just like {@link Genome};
 * the effective cycle is resolved by {@link OriginRegistry#effectiveLifeCycle}.
 *
 * <p>Composition is replace-not-merge: a later layer's non-empty cycle fully
 * overrides the earlier layer. A butterfly origin doesn't want to inherit
 * human stages and patch them; it wants Egg→Caterpillar→Larva→Butterfly.</p>
 */
public record LifeCycle(List<LifeStage> stages) {
    public static final LifeCycle EMPTY = new LifeCycle(List.of());

    public LifeCycle {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public boolean isEmpty() {
        return stages.isEmpty();
    }

    public int size() {
        return stages.size();
    }

    /** Sum of the stages' authored (un-scaled) game-day durations. */
    public int totalBaseDays() {
        int total = 0;
        for (LifeStage s : stages) total += s.days();
        return total;
    }

    /**
     * True when no stage authored an explicit {@code narrative_age}, so apparent
     * age should derive linearly from days alive ({@code bioAgeDays / agingScale}).
     * False if any stage overrides — those cycles use their authored bands instead.
     */
    public boolean derivesNarrative() {
        for (LifeStage s : stages) if (s.explicitNarrative()) return false;
        return true;
    }

    public LifeStage stageAt(int index) {
        return stages.get(index);
    }

    /** Find a stage by its stable id. Used by the runtime to resume a villager's stage after reload. */
    public Optional<LifeStage> findById(String id) {
        if (id == null) return Optional.empty();
        for (LifeStage s : stages) if (s.id().equals(id)) return Optional.of(s);
        return Optional.empty();
    }

    /** First stage presenting as the given canonical axis, or empty if none. */
    public Optional<LifeStage> firstPresentingAs(CanonicalStage canonical) {
        for (LifeStage s : stages) if (s.presentsAs() == canonical) return Optional.of(s);
        return Optional.empty();
    }

    /** Effective end action for the stage at {@code index}: {@code NEXT} for non-last, {@code STAY} for last unless overridden. */
    public StageEndAction effectiveEndAction(int index) {
        LifeStage stage = stages.get(index);
        if (stage.onEnd() != null) return stage.onEnd();
        return index == stages.size() - 1 ? StageEndAction.STAY : StageEndAction.NEXT;
    }

    /**
     * Stable hash over the stages' pacing-affecting shape (ids, day counts,
     * canonical kinds, end actions) — NOT labels or narrative ranges, which are
     * display-only. Stamped on a villager when its {@code stageDays} are rolled;
     * a mismatch on load means the cycle was re-authored, so the villager re-rolls
     * (self-heal for existing saves after a data-pack change).
     */
    public int fingerprint() {
        int h = 1;
        for (LifeStage s : stages) {
            h = 31 * h + s.id().hashCode();
            h = 31 * h + s.days();
            h = 31 * h + s.presentsAs().ordinal();
            h = 31 * h + (s.onEnd() == null ? 0 : s.onEnd().ordinal() + 1);
        }
        return h;
    }

    /**
     * Replace semantics: if {@code override} carries any stages, it wins outright;
     * otherwise this is kept. (Cycles are not stage-by-stage compositional — a
     * butterfly origin's stage list isn't supposed to inherit a human Toddler.)
     */
    public LifeCycle mergedWith(LifeCycle override) {
        if (override == null || override.isEmpty()) return this;
        return override;
    }

    /**
     * The fallback when no origin/ancestry/heritage declares one. Six canonical
     * stages whose base day-counts equal their apparent-year spans (baby 2, …,
     * adult 47, senior 25 = 90 total), so apparent age derives linearly as
     * {@code daysAlive / agingScale}. The spawn-time aging scale then stretches
     * this 90-"day" skeleton to the configured human lifespan (default 730 days =
     * 2 calendar years). Longer-lived races author bigger spans and live longer
     * by the same rule. The childhood spans are deliberately substantial so child
     * stages last long enough to give nurseries/schools/apprenticeships purpose.
     */
    public static LifeCycle defaultHumanLike() {
        return new LifeCycle(List.of(
                LifeStage.of("baby",    label("townstead.stage.baby"),    CanonicalStage.BABY,    2,  null),
                LifeStage.of("toddler", label("townstead.stage.toddler"), CanonicalStage.TODDLER, 2,  null),
                LifeStage.of("child",   label("townstead.stage.child"),   CanonicalStage.CHILD,   9,  null),
                LifeStage.of("teen",    label("townstead.stage.teen"),    CanonicalStage.TEEN,    5,  null),
                LifeStage.of("adult",   label("townstead.stage.adult"),   CanonicalStage.ADULT,   47, null),
                LifeStage.of("senior",  label("townstead.stage.senior"),  CanonicalStage.SENIOR,  25, StageEndAction.STAY)
        ));
    }

    /**
     * Narrative ("life years") span of a baseline human life — the sum of the
     * default cycle's stage spans. Reference/documentation constant only: aging is
     * now a flat game-days-per-narrative-year rate (see {@code agingScale} config),
     * not derived from this. A human at 90 narrative years × the default scale (8)
     * lives ~720 game-days. Kept so callers have the human baseline to compare against.
     */
    public static final int HUMAN_APPARENT_LIFESPAN = 90;

    private static Component label(String key) {
        return Component.translatable(key);
    }
}
