package com.aetherianartificer.townstead.work.order;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import com.aetherianartificer.townstead.work.producer.ProducerRecipe;

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

    /**
     * What a line names.
     *
     * <p>Most work has a thing at the end of it and can be counted. A butcher's day mostly does
     * not: slaughtering, dressing a carcass, mopping blood and carrying a delivery are things you
     * either do or do not, gated by whether there is an animal, a carcass, a puddle or a parcel.
     * They belong on the same list because they compete for the same hours, but "make twenty of
     * them" is not a sentence about any of them.</p>
     */
    public enum Kind {
        /** Names an item. Has a target, counts stock or production. */
        ITEM,
        /** Names a job. No target, always {@link Mode#STANDING}: permission, not instruction. */
        ACTIVITY,
        /**
         * Names an item tag: "any cooked meat" rather than one dish. Counts and matches over the
         * set's members, and otherwise behaves exactly like an item line — which member gets made
         * is left to the worker's own pick among the candidates that qualify.
         */
        TAG;

        /** Unknown kind reads as an item — that is what every save written before this was. */
        public static Kind parse(@Nullable String raw) {
            if (ACTIVITY.name().equalsIgnoreCase(raw)) return ACTIVITY;
            if (TAG.name().equalsIgnoreCase(raw)) return TAG;
            return ITEM;
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

    /** Who physically drives an entity-operated station while its worker manages the order. */
    public enum Operation {
        AUTOMATIC, WORKER, ENTITY;

        public static Operation parse(@Nullable String raw) {
            if (raw != null) {
                for (Operation value : values()) {
                    if (value.name().equalsIgnoreCase(raw)) return value;
                }
            }
            return AUTOMATIC;
        }
    }

    private final ResourceLocation output;
    /** Exact product identity; ordinary items use {@link #output}. */
    private ResourceLocation product;
    private String productName = "";
    private Mode mode;
    private int target;
    private CountScope scope;
    private boolean paused;

    /** Who may work it: null profession means anyone, and a named villager narrows it to one. */
    @Nullable private ResourceLocation profession;
    private int minRank;
    @Nullable private UUID villager;
    private Operation operation = Operation.AUTOMATIC;
    @Nullable private UUID operator;

    /** Completions credited to this line. Only {@link Mode#MAKE} reads it. */
    private int produced;

    /**
     * A commission's escrowed workpiece — the actual stack the player handed over, held by the
     * line until a worker collects it. Serialized NBT rather than a live stack so this class
     * stays registry-free for the order arithmetic tests; the name rides beside it for display.
     */
    @Nullable private net.minecraft.nbt.CompoundTag workpiece;
    private String workpieceName = "";

    /**
     * Workers who have committed to this line and not yet stored the result. Without it two cooks
     * both read "8 of 10" and both make two.
     */
    private int inProgress;

    private final Kind kind;

    public Order(ResourceLocation output, Mode mode, int target) {
        this(output, Kind.ITEM, mode, target);
    }

    public Order(ResourceLocation output, Kind kind, Mode mode, int target) {
        this.output = output;
        this.product = output;
        this.kind = kind == null ? Kind.ITEM : kind;
        // An activity has nothing to count, so it is standing whatever it was asked to be.
        this.mode = this.kind == Kind.ACTIVITY ? Mode.STANDING : (mode == null ? Mode.STANDING : mode);
        this.target = Math.max(0, target);
        this.scope = CountScope.HERE;
    }

    public Kind kind() { return kind; }

    /** True when this line names a job rather than a thing, and so has no number to set. */
    public boolean isActivity() { return kind == Kind.ACTIVITY; }

    /** True when this line names a set of items rather than one. */
    public boolean isTag() { return kind == Kind.TAG; }

    /** The item this line orders, the tag it draws from, or the id of the job it permits. */
    public ResourceLocation output() { return output; }

    /** Exact identity used for component-bearing products such as registered potions. */
    public ResourceLocation product() { return product; }

    public String productName() { return productName; }

    public void setProduct(@Nullable ResourceLocation value, @Nullable String name) {
        product = value == null ? output : value;
        productName = name == null ? "" : name;
    }

    public boolean exactProduct() {
        return OrderProducts.exact(product, output);
    }

    /**
     * Whether a candidate's output satisfies this line: equality for an item, membership for a
     * tag. Every place that used to compare outputs directly must ask this instead, or tag lines
     * silently match nothing.
     */
    public boolean matches(@Nullable ResourceLocation candidateOutput) {
        if (candidateOutput == null) return false;
        if (kind == Kind.TAG) return OrderTags.contains(output, candidateOutput);
        if (exactProduct()) return false;
        return output.equals(candidateOutput);
    }

    /** Recipe-aware match preserving exact stack-bearing product identity. */
    public boolean matches(@Nullable ProducerRecipe candidate) {
        if (candidate == null || candidate.output() == null) return false;
        if (kind == Kind.TAG) return OrderTags.contains(output, candidate.output());
        return output.equals(candidate.output()) && product.equals(OrderProducts.key(candidate));
    }

    public Mode mode() { return mode; }

    public void setMode(Mode value) {
        // An activity's mode is not the player's to change: there is nothing to count.
        if (value != null && kind != Kind.ACTIVITY) this.mode = value;
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

    public Operation operation() { return operation; }

    public void setOperation(Operation value) {
        operation = value == null ? Operation.AUTOMATIC : value;
        if (operation != Operation.ENTITY) operator = null;
    }

    @Nullable public UUID operator() { return operator; }

    public void setOperator(@Nullable UUID value) {
        operator = value;
        operation = value == null ? Operation.AUTOMATIC : Operation.ENTITY;
    }

    @Nullable public net.minecraft.nbt.CompoundTag workpiece() { return workpiece; }

    public String workpieceName() { return workpieceName; }

    public void setWorkpiece(@Nullable net.minecraft.nbt.CompoundTag tag, @Nullable String name) {
        this.workpiece = tag;
        this.workpieceName = name == null ? "" : name;
    }

    /** Hands the escrowed workpiece to a worker; the name stays for the line's display. */
    @Nullable public net.minecraft.nbt.CompoundTag takeWorkpiece() {
        net.minecraft.nbt.CompoundTag taken = workpiece;
        workpiece = null;
        return taken;
    }

    public int produced() { return produced; }

    public void setProduced(int value) { this.produced = Math.max(0, value); }

    public int inProgress() { return inProgress; }

    public void setInProgress(int value) { this.inProgress = Math.max(0, value); }

    /** A worker has committed this many output items to the line. */
    public void claim(int count) { inProgress += Math.max(1, count); }

    /** One-output compatibility form used by non-batching work engines. */
    public void claim() { claim(1); }

    /** A quantity commitment produced something: release its reservation and count the result. */
    public void finish(int claimed, int producedCount) {
        inProgress = Math.max(0, inProgress - Math.max(1, claimed));
        produced += Math.max(0, producedCount);
    }

    /** One-output compatibility form used by non-batching work engines. */
    public void finish(int count) { finish(1, count); }

    /** A quantity commitment came to nothing. The line goes back to where it was. */
    public void abandon(int claimed) {
        inProgress = Math.max(0, inProgress - Math.max(1, claimed));
    }

    /** One-output compatibility form used by non-batching work engines. */
    public void abandon() { abandon(1); }

    // ── Progress ──

    /**
     * Whether the requested level has actually been reached, without treating somebody else's
     * promised work as finished output.  This is the lifecycle boundary for a station participant:
     * a claim can make {@link #outstanding(OrderContext)} zero while another batch is still in
     * flight, but an animal or worker should leave only once the real count reaches the target.
     */
    public boolean satisfied(OrderContext context) {
        if (mode == Mode.STANDING) return false;
        int want = mode == Mode.PER_VILLAGER
                ? target * Math.max(0, context.villagerCount())
                : target;
        int have = mode.countsProduction() ? produced : context.stockOf(this, scope);
        return have >= want;
    }

    /**
     * How many more are wanted, counting work already under way so several workers do not all
     * start the last one. {@link Mode#STANDING} is never "done", so it reports one outstanding.
     */
    public int outstanding(OrderContext context) {
        if (mode == Mode.STANDING) return 1;
        int want = mode == Mode.PER_VILLAGER
                ? target * Math.max(0, context.villagerCount())
                : target;
        int have = mode.countsProduction() ? produced : context.stockOf(this, scope);
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
