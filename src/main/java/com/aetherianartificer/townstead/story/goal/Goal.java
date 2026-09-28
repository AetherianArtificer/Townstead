package com.aetherianartificer.townstead.story.goal;

import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * One quest goal, built from data by {@link Goals}. It is one of three shapes: a Pheno condition
 * that must hold, a Pheno value that must reach a target, or a Townstead event that must happen
 * a number of times. Conditions and values are read on the goal's subject, with the other party
 * of the story as their {@code other}.
 */
public final class Goal {
    /** A reading that could not be taken right now, such as when the teller is unloaded. */
    public static final long UNKNOWN = -1L;

    public enum Subject { TELLER, PLAYER }

    private final String text;
    private final long total;
    private final Subject subject;
    private final @Nullable Condition condition;
    private final @Nullable Value value;
    private final @Nullable Class<? extends TownsteadEvent> event;
    private final @Nullable EventMatcher matcher;
    private @Nullable String marker;

    Goal(String text, long total, Subject subject, @Nullable Condition condition, @Nullable Value value,
         @Nullable Class<? extends TownsteadEvent> event, @Nullable EventMatcher matcher) {
        this.text = text;
        this.total = Math.max(1L, total);
        this.subject = subject;
        this.condition = condition;
        this.value = value;
        this.event = event;
        this.matcher = matcher;
    }

    public long total() { return total; }

    /** The player marker whose position fills {@code {x}} and {@code {z}} in the text, if any. */
    public @Nullable String marker() { return marker; }

    Goal withMarker(@Nullable String marker) {
        this.marker = marker;
        return this;
    }

    public boolean isCounter() { return event != null; }
    public @Nullable Class<? extends TownsteadEvent> event() { return event; }

    /** The ledger line. {@code {teller}}, {@code {player}} and {@code {count}} are filled in here. */
    public String label(String teller, String player) {
        return text.replace("{teller}", teller).replace("{player}", player).replace("{count}", Long.toString(total));
    }

    /** The current reading of a condition or value goal, or {@link #UNKNOWN}. */
    public long read(GoalContext ctx) {
        if (isCounter()) return UNKNOWN;
        LivingEntity self = subject == Subject.TELLER ? ctx.speaker() : ctx.player();
        LivingEntity other = subject == Subject.TELLER ? ctx.player() : ctx.speaker();
        if (self == null) return UNKNOWN;
        try {
            if (condition != null) return condition.test(new ConditionContext(self, other)) ? 1L : 0L;
            if (value != null) {
                double reading = value.get(new SelectorContext(self, other, self, self.level(), self.position()));
                return Double.isFinite(reading) ? Math.max(0L, (long) Math.floor(reading)) : UNKNOWN;
            }
        } catch (RuntimeException e) {
            return UNKNOWN;
        }
        return UNKNOWN;
    }

    /** How far a posted event advances this counting goal. */
    public long increment(TownsteadEvent posted, GoalContext ctx) {
        if (event == null || matcher == null || !event.isInstance(posted)) return 0L;
        try {
            return matcher.matches(posted, ctx) ? 1L : 0L;
        } catch (RuntimeException e) {
            return 0L;
        }
    }
}
