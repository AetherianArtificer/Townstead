package com.aetherianartificer.townstead.work.site;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * A registered place where work happens: Townstead's own record of a kitchen, a smithy, a butchery
 * yard. Named by the player, found through its {@link WorksiteKey}, and the home for the things
 * that are true of a <em>place</em> rather than of a person — its extent, its name, its orders.
 *
 * <p>Two things deliberately do <strong>not</strong> live here. <em>Capacity</em> is a profession
 * question, not a place one: how many workers a kitchen supports is meaningless without asking
 * "for whom", and {@code ProfessionCapacity} already answers it generically from a career's own
 * definition. Putting a second copy on the worksite would give one number two authorities, which is
 * the exact failure this whole design exists to avoid. <em>Shifts</em> say when someone works, not
 * where; per-site scheduling is a feature built on top of this, and all it needs from here is a
 * stable {@link #id()} to point at.</p>
 *
 * <p>The id is minted here and never reused, so it survives everything the world does to the binding
 * underneath. That is the whole point of the split: when MCA restructures its buildings — and it has
 * — a worksite loses its key, not its identity, and re-binding costs a lookup instead of a player's
 * settings.</p>
 */
public final class Worksite {

    /** No village known, or none applicable. */
    public static final int NO_VILLAGE = -1;

    private final long id;
    private WorksiteKey key;
    private String name;
    private int villageId;
    private final long createdGameTime;
    private long lastSeenGameTime;

    Worksite(long id, WorksiteKey key, String name, int villageId,
             long createdGameTime, long lastSeenGameTime) {
        this.id = id;
        this.key = key;
        this.name = name == null ? "" : name;
        this.villageId = villageId;
        this.createdGameTime = createdGameTime;
        this.lastSeenGameTime = lastSeenGameTime;
    }

    /** Minted by the register, monotonic, never reused. Stable across re-binding. */
    public long id() {
        return id;
    }

    public WorksiteKey key() {
        return key;
    }

    /**
     * Points this record at a different binding, keeping its id and everything hanging off it. The
     * register rekeys its own map, so this is package-private on purpose.
     */
    void rebind(WorksiteKey newKey) {
        if (newKey != null) this.key = newKey;
    }

    /** What the player calls it. Blank means "never named", so a caller can fall back to the binding. */
    public String name() {
        return name;
    }

    /** A player naming the place. Custom names stick; the building can change under them. */
    public void setName(@Nullable String value) {
        this.name = value == null ? "" : value;
        this.nameCustom = true;
    }

    private boolean nameCustom;

    /** Whether a player chose this name, as opposed to it being read off the building type. */
    public boolean nameCustom() {
        return nameCustom;
    }

    /**
     * The system keeping a derived name current: a kitchen rebuilt into a cafe should introduce
     * itself as one. Refuses to touch a name a player chose.
     */
    public void setDerivedName(String value) {
        if (nameCustom || value == null) return;
        this.name = value;
    }

    void loadNameCustom(boolean value) {
        this.nameCustom = value;
    }

    /**
     * The village this sits in, refreshed as metadata rather than keyed on: a building can change
     * hands between villages without becoming a different place.
     */
    public int villageId() {
        return villageId;
    }

    public void setVillageId(int value) {
        this.villageId = value;
    }

    public long createdGameTime() {
        return createdGameTime;
    }

    /** Last time a worker resolved to this site; what pruning of long-dead sites reads. */
    public long lastSeenGameTime() {
        return lastSeenGameTime;
    }

    void see(long gameTime) {
        if (gameTime > lastSeenGameTime) this.lastSeenGameTime = gameTime;
    }

    // ── Orders ──
    //
    // One list per worksite: everyone working here works from it, and "you specifically" is a field
    // on a line rather than a second list competing with this one.

    private final com.aetherianartificer.townstead.work.order.OrderList orders =
            new com.aetherianartificer.townstead.work.order.OrderList();

    public com.aetherianartificer.townstead.work.order.OrderList orders() {
        return orders;
    }

    private int ordersRevision;

    /**
     * How many times the orders here have changed since the world loaded.
     *
     * <p>Watched by open screens so they are told rather than having to ask. Not persisted and not
     * meaningful across a reload — it is a "has this changed since you last looked" marker, and the
     * only thing that ever compares it took its first reading in this session.</p>
     */
    public int ordersRevision() {
        return ordersRevision;
    }

    /** Anything an open orders screen would draw differently has happened. */
    public void bumpOrdersRevision() {
        ordersRevision++;
    }

    // ── Driver assignment ──
    //
    // Some places own a piece of work equipment which is powered by an entity rather than by a
    // worker interaction.  The workstation definition says which entities are eligible; the
    // player's choice belongs here, beside the orders for this place.  Null deliberately means
    // automatic selection, so existing worlds immediately acquire sensible behaviour without a
    // migration screen.

    @Nullable
    private UUID driver;

    /** The particular animal chosen for this worksite, or null for automatic selection. */
    @Nullable
    public UUID driver() {
        return driver;
    }

    /** Selects one animal; null restores automatic selection. */
    public void setDriver(@Nullable UUID value) {
        driver = value;
    }

    // ── Extent ──
    //
    // Where the place physically is: the walkable cells a worker reasons over. Deriving it is a
    // flood fill of up to a couple of thousand cells, which is why it is worth remembering, and
    // why the record is the right thing to remember it on — two cooks in one kitchen share one
    // answer instead of each deriving their own.
    //
    // Deliberately NOT persisted. It is a function of blocks in the world, so recomputing it after
    // a restart is correct and cheap-once, while storing it risks handing a villager the shape of a
    // room that has since been rebuilt.

    @Nullable
    private Set<Long> extent;
    private long extentFreshUntil;

    /** The stored extent, or null when it has never been computed or has gone stale. */
    @Nullable
    public Set<Long> extentIfFresh(long gameTime) {
        return extent != null && gameTime <= extentFreshUntil ? extent : null;
    }

    /**
     * Stores a freshly derived extent. {@code freshForTicks} is a backstop rather than the primary
     * invalidation: a rebuilt wall should be picked up by an explicit invalidation, and this only
     * bounds how wrong we can be if one is missed.
     */
    public void setExtent(@Nullable Set<Long> cells, long gameTime, long freshForTicks) {
        this.extent = cells;
        this.extentFreshUntil = cells == null ? Long.MIN_VALUE : gameTime + freshForTicks;
    }

    /** Forgets the extent, so the next ask re-derives it from the world. */
    public void invalidateExtent() {
        this.extent = null;
        this.extentFreshUntil = Long.MIN_VALUE;
    }

    @Override
    public String toString() {
        return "Worksite#" + id + "[" + (name.isEmpty() ? key.toString() : name) + "]";
    }
}
