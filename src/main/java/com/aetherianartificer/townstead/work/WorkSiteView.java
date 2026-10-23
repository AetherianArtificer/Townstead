package com.aetherianartificer.townstead.work;

import com.aetherianartificer.townstead.work.site.Worksite;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Where one villager is working, resolved for one moment: the anchor to reason from and the cells
 * that count as inside.
 *
 * <p>Distinct from {@link Worksite}, which is the registered place itself. This is the view of it a
 * task computes while working; the record is what the place is when nobody is looking. A view
 * carries its record when there is one, so anything downstream — orders, shifts, a name to put in a
 * message — can reach it without resolving all over again.</p>
 */
public record WorkSiteView(
        Kind kind,
        @Nullable BlockPos anchor,
        Set<Long> ownedBounds,
        int horizontalRadius,
        int verticalRadius,
        /** The registered place this view is of, when it has been resolved. */
        @Nullable Worksite site
) {
    public enum Kind {
        BUILDING,
        ZONE
    }

    public static WorkSiteView building(@Nullable BlockPos anchor, Set<Long> ownedBounds,
                                        @Nullable Worksite site) {
        return new WorkSiteView(Kind.BUILDING, anchor == null ? null : anchor.immutable(),
                Set.copyOf(ownedBounds), 0, 0, site);
    }

    public static WorkSiteView zone(@Nullable BlockPos anchor, int horizontalRadius, int verticalRadius,
                                    @Nullable Worksite site) {
        return new WorkSiteView(Kind.ZONE, anchor == null ? null : anchor.immutable(),
                Set.of(), horizontalRadius, verticalRadius, site);
    }

    public boolean contains(BlockPos pos) {
        if (pos == null) return false;
        if (kind == Kind.BUILDING) {
            return ownedBounds.contains(pos.asLong());
        }
        if (anchor == null) return false;
        return Math.abs(pos.getX() - anchor.getX()) <= horizontalRadius
                && Math.abs(pos.getZ() - anchor.getZ()) <= horizontalRadius
                && Math.abs(pos.getY() - anchor.getY()) <= verticalRadius;
    }

    public String describe() {
        String anchorDesc = anchor == null ? "none" : anchor.getX() + "," + anchor.getY() + "," + anchor.getZ();
        return switch (kind) {
            case BUILDING -> "building@" + anchorDesc + "[" + ownedBounds.size() + "]";
            case ZONE -> "zone@" + anchorDesc + "[r=" + horizontalRadius + ",y=" + verticalRadius + "]";
        };
    }
}
