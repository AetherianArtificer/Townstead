package com.aetherianartificer.townstead.pheno.combat;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.damage.DamageCondition;
import com.aetherianartificer.townstead.pheno.event.PhenoEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Damage links placed by {@code pheno:share_damage}: a part of each hit on the guarded entity goes
 * to its guardian instead. Links never stack: each hit uses the single link that moves the most.
 * A link with the same {@code id} from the same guardian replaces the old one. The guardian takes
 * its part as plain damage, which is never shared again.
 *
 * <p>Transient: links are short-lived combat state and are not saved.</p>
 */
public final class Guards {

    public record Spec(String id, float fraction, int durationTicks, float reach,
                       @Nullable DamageCondition damageCondition, @Nullable Action onShare) {}

    private static final class Link {
        final Spec spec;
        final WeakReference<LivingEntity> guardian;
        final long expiresAt;

        Link(Spec spec, LivingEntity guardian, long now) {
            this.spec = spec;
            this.guardian = new WeakReference<>(guardian);
            this.expiresAt = now + spec.durationTicks();
        }
    }

    private static final Map<LivingEntity, List<Link>> LINKS = new WeakHashMap<>();
    private static boolean sharing;

    private Guards() {}

    public static void place(LivingEntity guarded, LivingEntity guardian, Spec spec) {
        if (guarded.level().isClientSide || guarded == guardian || spec.fraction() <= 0f) return;
        List<Link> links = LINKS.computeIfAbsent(guarded, e -> new ArrayList<>());
        links.removeIf(l -> l.spec.id().equals(spec.id()) && l.guardian.get() == guardian);
        links.add(new Link(spec, guardian, guarded.level().getGameTime()));
    }

    /** Remove {@code id}, or every link when {@code id} is null. */
    public static void clear(LivingEntity guarded, @Nullable String id) {
        List<Link> links = LINKS.get(guarded);
        if (links == null) return;
        if (id == null) links.clear();
        else links.removeIf(l -> l.spec.id().equals(id));
    }

    public static boolean guarded(LivingEntity entity, @Nullable String id) {
        List<Link> links = LINKS.get(entity);
        if (links == null) return false;
        long now = entity.level().getGameTime();
        for (Link l : links) {
            if (l.expiresAt > now && (id == null || l.spec.id().equals(id))) return true;
        }
        return false;
    }

    /** The damage left for {@code victim} after its best link moves a part to the guardian. */
    public static float share(LivingEntity victim, DamageSource source, float amount) {
        if (sharing || amount <= 0f) return amount;
        List<Link> links = LINKS.get(victim);
        if (links == null || links.isEmpty()) return amount;
        long now = victim.level().getGameTime();
        links.removeIf(l -> l.expiresAt <= now || l.guardian.get() == null || !l.guardian.get().isAlive());
        Link best = null;
        for (Link l : links) {
            LivingEntity guardian = l.guardian.get();
            if (guardian == source.getEntity() || guardian.level() != victim.level()) continue;
            if (l.spec.reach() > 0f && guardian.distanceToSqr(victim) > l.spec.reach() * l.spec.reach()) continue;
            if (l.spec.damageCondition() != null && !l.spec.damageCondition().test(source, amount)) continue;
            if (best == null || l.spec.fraction() > best.spec.fraction()) best = l;
        }
        if (best == null) return amount;
        LivingEntity guardian = best.guardian.get();
        float moved = amount * Math.min(1f, best.spec.fraction());
        Link link = best;
        sharing = true;
        try {
            guardian.hurt(guardian.damageSources().generic(), moved);
            if (link.spec.onShare() != null) {
                PhenoEvent.during(new PhenoEvent.Frame(moved, source, ItemStack.EMPTY),
                        () -> link.spec.onShare().run(new ActionContext(guardian, victim, guardian)));
            }
        } finally {
            sharing = false;
        }
        return amount - moved;
    }

    public static void tick(LivingEntity entity) {
        List<Link> links = LINKS.get(entity);
        if (links == null) return;
        long now = entity.level().getGameTime();
        links.removeIf(l -> l.expiresAt <= now);
        if (links.isEmpty()) LINKS.remove(entity);
    }

    public static void clearAll() {
        LINKS.clear();
    }
}
