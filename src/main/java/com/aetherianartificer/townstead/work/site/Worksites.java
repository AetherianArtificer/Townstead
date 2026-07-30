package com.aetherianartificer.townstead.work.site;

import com.aetherianartificer.townstead.compat.mca.McaRoomBinding;
import com.aetherianartificer.townstead.work.WorkSiteBounds;

import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

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

    /** The record for a standalone workstation — an outdoor post, a smoker in a yard. */
    @Nullable
    public static Worksite of(ServerLevel level, @Nullable BlockPos anchor) {
        if (anchor == null) return null;
        WorksiteKey key = WorksiteKey.at(
                WorksiteBindings.ANCHOR, level.dimension().location(), anchor);
        return register(level, key);
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
    private static Worksite register(ServerLevel level, WorksiteKey key) {
        WorksiteRegister register = register(level);
        if (register == null) return null;
        Worksite existing = register.find(key);
        if (existing != null) {
            long now = level.getGameTime();
            if (now - existing.lastSeenGameTime() >= SEEN_GRANULARITY_TICKS) {
                register.touch(key, now);
            }
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
        if (site != null) site.setExtent(derived, gameTime, EXTENT_FRESH_TICKS);
        return derived;
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
