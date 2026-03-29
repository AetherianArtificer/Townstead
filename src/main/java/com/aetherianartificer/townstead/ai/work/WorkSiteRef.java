package com.aetherianartificer.townstead.ai.work;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Set;

public record WorkSiteRef(
        Kind kind,
        @Nullable BlockPos anchor,
        Set<Long> ownedBounds,
        int horizontalRadius,
        int verticalRadius
) {
    public enum Kind {
        BUILDING,
        ZONE
    }

    public static WorkSiteRef building(@Nullable BlockPos anchor, Set<Long> ownedBounds) {
        return new WorkSiteRef(Kind.BUILDING, anchor == null ? null : anchor.immutable(), Set.copyOf(ownedBounds), 0, 0);
    }

    public static WorkSiteRef zone(@Nullable BlockPos anchor, int horizontalRadius, int verticalRadius) {
        return new WorkSiteRef(Kind.ZONE, anchor == null ? null : anchor.immutable(), Set.of(), horizontalRadius, verticalRadius);
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
