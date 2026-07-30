package com.aetherianartificer.townstead.work.order;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One line of a worksite's production orders: make this, this much, and who may make it.
 *
 * <p>An order names an <strong>item</strong>, not a recipe, so any recipe producing bread satisfies
 * "make 10 bread" and a cook and a baker at different stations can both work the same line.</p>
 */
public final class Order {

    /**
     * How the number is read. Two of these count a <em>flow</em> and two read a <em>level</em>, and
     * confusing them produces the two classic bugs: a "make 10" that reads storage never finishes
     * once somebody eats the pies, and a "keep 10" that counts production never stops making them.
     */
    public enum Mode {
        /** Ten completions, then the line retires. Counts what was produced. */
        MAKE(true),
        /** Works below the target, rests above it. Reads what is stored. */
        KEEP_STOCKED(false),
        /** The same as keeping stocked, with a target that grows with the village. */
        PER_VILLAGER(false),
        /** No target: make this when there is nothing more pressing. */
        STANDING(false);

        private final boolean countsProduction;

        Mode(boolean countsProduction) {
            this.countsProduction = countsProduction;
        }

        /** True when progress comes from work done, false when it comes from what is on the shelf. */
        public boolean countsProduction() {
            return countsProduction;
        }

        public boolean hasTarget() {
            return this != STANDING;
        }

        /**
         * Reads a stored mode, falling back to {@link #STANDING} for anything unrecognised. A save
         * written by a newer build must not turn "keep twenty in stock" into "make twenty" — the
         * safe unknown is the one with no target at all.
         */
        public static Mode parse(@Nullable String raw) {
            if (raw == null) return STANDING;
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(raw)) return mode;
            }
            return STANDING;
        }
    }

    /** Where "how many do we have" is measured. */
    public enum CountScope {
        HERE, VILLAGE;

        /** Unknown scope reads as the narrower one: counting less over-produces, never under. */
        public static CountScope parse(@Nullable String raw) {
            if (raw != null && VILLAGE.name().equalsIgnoreCase(raw)) return VILLAGE;
            return HERE;
        }
    }

    private final ResourceLocation output;
    private Mode mode;
    private int target;
    private CountScope scope;
    private boolean paused;

    /** Who may work it: null profession means anyone, and a named villager narrows it to one. */
    @Nullable private ResourceLocation profession;
    private int minRank;
    @Nullable private UUID villager;

    /** Completions credited to this line. Only {@link Mode#MAKE} reads it. */
    private int produced;

    /**
     * Workers who have committed to this line and not yet stored the result. Without it two cooks
     * both read "8 of 10" and both make two.
     */
    private int inProgress;

    public Order(ResourceLocation output, Mode mode, int target) {
        this.output = output;
        this.mode = mode == null ? Mode.STANDING : mode;
        this.target = Math.max(0, target);
        this.scope = CountScope.HERE;
    }

    public ResourceLocation output() { return output; }

    public Mode mode() { return mode; }

    public void setMode(Mode value) {
        if (value != null) this.mode = value;
    }

    public int target() { return target; }

    public void setTarget(int value) { this.target = Math.max(0, value); }

    public CountScope scope() { return scope; }

    public void setScope(CountScope value) {
        if (value != null) this.scope = value;
    }

    public boolean paused() { return paused; }

    public void setPaused(boolean value) { this.paused = value; }

    @Nullable public ResourceLocation profession() { return profession; }

    public void setProfession(@Nullable ResourceLocation value) { this.profession = value; }

    public int minRank() { return minRank; }

    public void setMinRank(int value) { this.minRank = Math.max(0, value); }

    @Nullable public UUID villager() { return villager; }

    public void setVillager(@Nullable UUID value) { this.villager = value; }

    public int produced() { return produced; }

    public void setProduced(int value) { this.produced = Math.max(0, value); }

    public int inProgress() { return inProgress; }

    public void setInProgress(int value) { this.inProgress = Math.max(0, value); }

    /** A worker has committed to this line. Released by {@link #finish} or {@link #abandon}. */
    public void claim() { inProgress++; }

    /** A commitment produced something: it stops being in progress and starts counting. */
    public void finish(int count) {
        if (inProgress > 0) inProgress--;
        produced += Math.max(0, count);
    }

    /** A commitment came to nothing. The line goes back to where it was. */
    public void abandon() {
        if (inProgress > 0) inProgress--;
    }

    // ── Progress ──

    /**
     * How many more are wanted, counting work already under way so several workers do not all
     * start the last one. {@link Mode#STANDING} is never "done", so it reports one outstanding.
     */
    public int outstanding(OrderContext context) {
        if (mode == Mode.STANDING) return 1;
        int want = mode == Mode.PER_VILLAGER
                ? target * Math.max(0, context.villagerCount())
                : target;
        int have = mode.countsProduction() ? produced : context.stockOf(output, scope);
        return Math.max(0, want - have - inProgress);
    }

    /** Whether this line still wants work right now. */
    public boolean wantsWork(OrderContext context) {
        return !paused && outstanding(context) > 0;
    }

    /**
     * Whether the line has finished for good. Only a counted-production order ever retires — a
     * stocked one goes quiet and waits for the shelf to empty again.
     */
    public boolean retired() {
        return mode == Mode.MAKE && produced >= target;
    }

    @Override
    public String toString() {
        return "Order[" + output + " " + mode + " " + target + (paused ? " paused" : "") + "]";
    }
}
