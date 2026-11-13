package com.aetherianartificer.townstead.pheno.combat;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition;
import com.aetherianartificer.townstead.pheno.event.PhenoEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Damage wards placed by {@code pheno:ward}: a budget of damage an entity is spared, optionally
 * only part of each hit and only from matching damage, for a limited time. Wards never stack: each
 * hit is softened by the single ward that spares the most, so two allies' wards on one target do
 * not add up. A ward with the same {@code id} replaces the old one.
 *
 * <p>Transient: wards are short-lived combat state and are not saved.</p>
 */
public final class Wards {

    public record Spec(String id, float fraction, int durationTicks,
                       @Nullable DamageCondition damageCondition,
                       @Nullable Action onAbsorb, @Nullable Action onBreak, @Nullable Action onEnd) {}

    private static final class Ward {
        final Spec spec;
        final long expiresAt;
        final java.lang.ref.WeakReference<LivingEntity> caster;
        float remaining;
        float spared;

        Ward(Spec spec, long now, float budget, @Nullable LivingEntity caster) {
            this.spec = spec;
            this.caster = new java.lang.ref.WeakReference<>(caster);
            this.expiresAt = now + spec.durationTicks();
            this.remaining = budget;
        }
    }

    private static final Map<LivingEntity, List<Ward>> WARDS = new WeakHashMap<>();

    private Wards() {}

    /** Place {@code spec} on {@code entity} with {@code budget} health of protection. */
    public static void place(LivingEntity entity, Spec spec, float budget) {
        place(entity, spec, budget, null);
    }

    /** As {@link #place(LivingEntity, Spec, float)}; the ward's reactions see {@code caster} as the origin. */
    public static void place(LivingEntity entity, Spec spec, float budget, @Nullable LivingEntity caster) {
        if (entity.level().isClientSide || budget <= 0f || spec.durationTicks() <= 0) return;
        List<Ward> wards = WARDS.computeIfAbsent(entity, e -> new ArrayList<>());
        wards.removeIf(w -> w.spec.id().equals(spec.id()));
        wards.add(new Ward(spec, entity.level().getGameTime(), budget, caster));
    }

    /** Remove {@code id}, or every ward when {@code id} is null. */
    public static void clear(LivingEntity entity, @Nullable String id) {
        List<Ward> wards = WARDS.get(entity);
        if (wards == null) return;
        if (id == null) wards.clear();
        else wards.removeIf(w -> w.spec.id().equals(id));
    }

    /** Health left in {@code id}'s ward (the strongest ward when null), or 0. */
    public static float remaining(LivingEntity entity, @Nullable String id) {
        List<Ward> wards = WARDS.get(entity);
        if (wards == null) return 0f;
        long now = entity.level().getGameTime();
        float best = 0f;
        for (Ward w : wards) {
            if (w.expiresAt <= now || (id != null && !w.spec.id().equals(id))) continue;
            best = Math.max(best, w.remaining);
        }
        return best;
    }

    /** The damage left after the best eligible ward spares what it can. */
    public static float absorb(LivingEntity victim, DamageSource source, float amount) {
        List<Ward> wards = WARDS.get(victim);
        if (wards == null || wards.isEmpty() || amount <= 0f) return amount;
        long now = victim.level().getGameTime();
        expire(victim, wards, now);
        wards.removeIf(w -> w.remaining <= 0f);
        // A ward with fraction 0 spares nothing. It only reacts, on every matching hit, whichever
        // ward spares that hit.
        LivingEntity striker = source.getEntity() instanceof LivingEntity le ? le : null;
        for (Ward w : new ArrayList<>(wards)) {
            if (w.spec.fraction() > 0f || w.spec.onAbsorb() == null) continue;
            if (w.spec.damageCondition() != null && !w.spec.damageCondition().test(source, amount)) continue;
            PhenoEvent.during(new PhenoEvent.Frame(amount, source, ItemStack.EMPTY),
                    () -> w.spec.onAbsorb().run(context(victim, striker, w)));
        }
        List<Candidate> candidates = new ArrayList<>(wards.size());
        for (Ward w : wards) {
            boolean eligible = w.spec.damageCondition() == null || w.spec.damageCondition().test(source, amount);
            candidates.add(new Candidate(w.remaining, w.spec.fraction(), eligible));
        }
        int chosen = best(candidates, amount);
        if (chosen < 0) return amount;
        Ward best = wards.get(chosen);
        float bestSpared = candidates.get(chosen).spares(amount);
        best.remaining -= bestSpared;
        best.spared += bestSpared;
        if (best.remaining <= 0f) wards.remove(best);
        LivingEntity attacker = source.getEntity() instanceof LivingEntity le ? le : null;
        Ward ward = best;
        PhenoEvent.during(new PhenoEvent.Frame(bestSpared, source, ItemStack.EMPTY), () -> {
            if (ward.spec.onAbsorb() != null) ward.spec.onAbsorb().run(context(victim, attacker, ward));
            if (ward.remaining <= 0f && ward.spec.onBreak() != null) {
                ward.spec.onBreak().run(context(victim, attacker, ward));
            }
        });
        return amount - bestSpared;
    }

    /** One ward as the selection sees it. */
    record Candidate(float remaining, float fraction, boolean eligible) {
        float spares(float amount) {
            return eligible ? Math.min(remaining, amount * fraction) : 0f;
        }
    }

    /** The index of the ward that spares the most of {@code amount} (first wins a tie), or -1. */
    static int best(List<Candidate> candidates, float amount) {
        int best = -1;
        float bestSpared = 0f;
        for (int i = 0; i < candidates.size(); i++) {
            float spared = candidates.get(i).spares(amount);
            if (spared > bestSpared) {
                best = i;
                bestSpared = spared;
            }
        }
        return best;
    }

    /** Drop expired wards; called from the living tick so nothing outlives its duration. */
    public static void tick(LivingEntity entity) {
        List<Ward> wards = WARDS.get(entity);
        if (wards == null) return;
        expire(entity, wards, entity.level().getGameTime());
        if (wards.isEmpty()) WARDS.remove(entity);
    }

    private static ActionContext context(LivingEntity wearer, @Nullable LivingEntity attacker, Ward ward) {
        LivingEntity caster = ward.caster.get();
        return new ActionContext(wearer, attacker, caster != null && caster.isAlive() ? caster : wearer);
    }

    /** Drops wards whose time is up. {@code on_end} runs with the total the ward spared as the event damage. */
    private static void expire(LivingEntity wearer, List<Ward> wards, long now) {
        List<Ward> ended = null;
        for (Ward w : wards) {
            if (w.expiresAt > now) continue;
            if (ended == null) ended = new ArrayList<>();
            ended.add(w);
        }
        if (ended == null) return;
        wards.removeAll(ended);
        for (Ward w : ended) {
            if (w.spec.onEnd() == null || w.remaining <= 0f || !wearer.isAlive()) continue;
            PhenoEvent.during(new PhenoEvent.Frame(w.spared, null, ItemStack.EMPTY),
                    () -> w.spec.onEnd().run(context(wearer, null, w)));
        }
    }

    public static void clearAll() {
        WARDS.clear();
        Guards.clearAll();
        Fears.clearAll();
    }
}
