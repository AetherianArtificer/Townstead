package com.aetherianartificer.townstead.work.site;

import com.aetherianartificer.townstead.compat.mca.McaRoomBinding;
import com.aetherianartificer.townstead.work.WorkSiteBounds;

import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The server-side way in to the register: turn a place in the world into its record, and ask that
 * record where it physically is.
 *
 * <p>Resolving allocates a key and touches a map, so it is a <em>transition</em> operation — call it
 * when a villager takes up a station or is reassigned, then hold the returned {@link Worksite} and
 * read from that. Villager AI runs at 20Hz per villager and has twice been made slower by caches
 * whose own machinery cost more than the work they saved; nothing here belongs in an inner loop.</p>
 */
public final class Worksites {

    /**
     * How long a derived extent is trusted without an explicit invalidation. Matched to the nav
     * snapshot's existing window on purpose: this must not make anything staler than the cook path
     * already tolerates.
     */
    public static final long EXTENT_FRESH_TICKS = 80L;

    private Worksites() {}

    // ── Resolving ──

    /** The record for an MCA room, registering it the first time anyone works there. */
    @Nullable
    public static Worksite of(ServerLevel level, @Nullable Building building) {
        if (building == null) return null;
        WorksiteKey key = new WorksiteKey(
                McaRoomBinding.ID, level.dimension().location(), building.getId());
        return register(level, key);
    }

    /**
     * The record for a place identified by a block: a smoker in a yard, a pot on a counter.
     *
     * <p>Resolves through <em>every</em> binding in priority order rather than assuming the block
     * is its own worksite. A station inside a recognised building belongs to that building, so the
     * cook standing at the pot and the player opening the room land on the same record. Before
     * this, they did not, and each got its own order list.</p>
     *
     * <p>It also gives a player back their own kitchen: a pot in a house resolves to the house,
     * which is nobody's workplace, so it never becomes a standalone post for a passing villager to
     * claim. A station only becomes a place of work where it stands somewhere that already is
     * one, or out in the open where somebody deliberately put it.</p>
     */
    @Nullable
    public static Worksite of(ServerLevel level, @Nullable BlockPos anchor) {
        if (anchor == null) return null;
        return register(level, canonicalKeyAt(level, anchor));
    }

    /** The highest-priority binding that claims this position, or null when none does. */
    @Nullable
    public static WorksiteKey canonicalKeyAt(ServerLevel level, BlockPos pos) {
        for (WorksiteBindings.Binding binding : WorksiteBindings.all()) {
            WorksiteKey key = binding.keyAt(level, pos);
            if (key != null) return key;
        }
        return null;
    }

    /**
     * How coarsely "last seen" is tracked. Resolution can happen every tick while a villager walks
     * to work, and pruning only needs to tell a quiet worksite from an abandoned one — so the
     * timestamp moves in minutes rather than ticks, and the register is not marked dirty 20 times a
     * second for a number nobody reads that precisely.
     */
    private static final long SEEN_GRANULARITY_TICKS = 1200L;

    /**
     * The record for a key, registering it if this is the first anybody has asked. Public because a
     * player opening a worksite's screen is exactly the kind of deliberate act that should create
     * one — waiting for a villager to happen to work there first is not discoverable.
     */
    @Nullable
    public static Worksite of(ServerLevel level, WorksiteKey key) {
        return key == null ? null : register(level, key);
    }

    @Nullable
    private static Worksite register(ServerLevel level, @Nullable WorksiteKey key) {
        if (key == null) return null;
        WorksiteRegister register = register(level);
        if (register == null) return null;
        Worksite existing = register.find(key);
        if (existing != null) {
            long now = level.getGameTime();
            if (now - existing.lastSeenGameTime() >= SEEN_GRANULARITY_TICKS) {
                register.touch(key, now);
            }
            absorbStrayPosts(level, register, existing);
            return existing;
        }
        WorksiteBindings.Binding binding = WorksiteBindings.forKey(key);
        String name = binding == null ? "" : binding.defaultName(level, key);
        int villageId = McaRoomBinding.ID.equals(key.binding())
                ? McaRoomBinding.villageOf(level, key)
                : Worksite.NO_VILLAGE;
        return register.register(key, name, villageId, level.getGameTime());
    }

    @Nullable
    private static WorksiteRegister register(ServerLevel level) {
        return level.getServer() == null ? null : WorksiteRegister.get(level.getServer());
    }

    /** Rooms already swept for strays this session, so the scan runs once per room, not per tick. */
    private static final java.util.Set<Long> SWEPT = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Folds any block-bound record standing inside this room into it, then retires the stray.
     *
     * <p>Worlds that ran the older resolution have two records for one kitchen, each with its own
     * order list — the player wrote to one and the villagers read the other. The room's list is
     * authoritative; a stray's lines are appended only where the room does not already order that
     * item, so nothing written is lost and nothing is doubled.</p>
     *
     * <p>Runs once per room per session. The scan is over the register, not the world, and the
     * usual outcome is that there is nothing to move.</p>
     */
    private static void absorbStrayPosts(ServerLevel level, WorksiteRegister register, Worksite room) {
        if (WorksiteBindings.ANCHOR.equals(room.key().binding())) return;
        if (!SWEPT.add(room.id())) return;

        List<WorksiteKey> strays = new ArrayList<>();
        int moved = 0;
        for (Worksite other : register.all()) {
            if (other == room) continue;
            if (!WorksiteBindings.ANCHOR.equals(other.key().binding())) continue;
            if (!other.key().dimension().equals(room.key().dimension())) continue;
            // "Inside this room" is asked of the binding rather than measured here, so the answer
            // is the same one resolution itself would give.
            if (!room.key().equals(canonicalKeyAt(level, other.key().pos()))) continue;
            moved += room.orders().absorb(other.orders());
            if (room.driver() == null && other.driver() != null) {
                room.setDriver(other.driver());
                moved++;
            }
            strays.add(other.key());
        }
        if (strays.isEmpty()) return;
        strays.forEach(register::remove);
        if (moved > 0) room.bumpOrdersRevision();
        register.setDirty();
    }

    // ── Extent ──

    /**
     * Where this worksite physically is, deriving it only when nobody has recently. The derivation
     * is the same world flood fill as before, so the answer is identical — it is simply asked once
     * per kitchen rather than once per villager per tick.
     */
    public static Set<Long> extentOf(ServerLevel level, @Nullable Worksite site,
                                     @Nullable Building building, @Nullable BlockPos anchor) {
        long gameTime = level.getGameTime();
        if (site != null) {
            Set<Long> fresh = site.extentIfFresh(gameTime);
            if (fresh != null) return fresh;
        }
        Set<Long> derived = building != null
                ? WorkSiteBounds.workArea(level, building)
                : anchor != null ? WorkSiteBounds.workAreaAround(level, anchor) : Set.of();
        if (site != null) {
            site.setExtent(derived, gameTime, EXTENT_FRESH_TICKS);
            refreshDerivedName(level, site);
        }
        return derived;
    }

    /**
     * A building that changed type renames its site — a kitchen rebuilt into a cafe should say
     * so — unless a player named the place themselves. Runs on the extent's refresh cadence, so
     * it costs a name comparison, not a per-tick lookup.
     */
    private static void refreshDerivedName(ServerLevel level, Worksite site) {
        if (site.nameCustom()) return;
        WorksiteBindings.Binding binding = WorksiteBindings.forKey(site.key());
        if (binding == null) return;
        String derived = binding.defaultName(level, site.key());
        if (derived.isEmpty() || derived.equals(site.name())) return;
        site.setDerivedName(derived);
        WorksiteRegister register = register(level);
        if (register != null) register.setDirty();
    }

    /**
     * Where this worksite is, asking its own binding when nobody has recently.
     *
     * <p>The form to use when all you hold is the record. The four-argument version needs a caller
     * who already knows the building or the anchor; anyone who does not knows only the key, and a
     * key's {@code value} means different things to different bindings.</p>
     */
    public static Set<Long> extentOf(ServerLevel level, @Nullable Worksite site) {
        if (site == null) return Set.of();
        long gameTime = level.getGameTime();
        Set<Long> fresh = site.extentIfFresh(gameTime);
        if (fresh != null) return fresh;
        WorksiteBindings.Binding binding = WorksiteBindings.forKey(site.key());
        Set<Long> derived = binding == null ? Set.of() : binding.extentOf(level, site.key());
        site.setExtent(derived, gameTime, EXTENT_FRESH_TICKS);
        refreshDerivedName(level, site);
        return derived;
    }

    /** Drops a site's stored extent, so the next ask re-derives it. Called when its room changes. */
    public static void invalidateExtent(ServerLevel level, @Nullable Building building) {
        WorksiteRegister register = register(level);
        if (register == null || building == null) return;
        Worksite site = register.find(new WorksiteKey(
                McaRoomBinding.ID, level.dimension().location(), building.getId()));
        if (site != null) site.invalidateExtent();
    }
}
