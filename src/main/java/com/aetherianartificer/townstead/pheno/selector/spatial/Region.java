package com.aetherianartificer.townstead.pheno.selector.spatial;

import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A spatial selection resolved against the focus: a center the {@code anchor} computes (a place
 * like {@code below} or {@code looking_at}, which can be null when it needs a facing there is none
 * of) plus box half-extents, optionally clipped to a sphere. The same region feeds both kinds: an
 * entity selector queries {@link #bounds} then keeps those {@link #contains}, a block selector
 * enumerates {@link #positions}. A single place is a region with zero extent.
 */
public final class Region {

    private final Function<SelectorContext, BlockPos> anchor;
    private final int ex;
    private final int ey;
    private final int ez;
    private final double sphereSq; // > 0 clips the box to a sphere of this squared radius

    public Region(Function<SelectorContext, BlockPos> anchor, int ex, int ey, int ez, double sphere) {
        this.anchor = anchor;
        this.ex = ex;
        this.ey = ey;
        this.ez = ez;
        this.sphereSq = sphere > 0 ? sphere * sphere : 0;
    }

    @Nullable
    public BlockPos anchor(SelectorContext ctx) {
        return anchor.apply(ctx);
    }

    @Nullable
    public AABB bounds(SelectorContext ctx) {
        BlockPos c = anchor.apply(ctx);
        if (c == null) return null;
        return new AABB(c.getX() - ex, c.getY() - ey, c.getZ() - ez,
                c.getX() + ex + 1, c.getY() + ey + 1, c.getZ() + ez + 1);
    }

    /** Whether a world point lies in the region (sphere clip when set, else the box). */
    public boolean contains(SelectorContext ctx, Vec3 point) {
        BlockPos c = anchor.apply(ctx);
        if (c == null) return false;
        if (sphereSq <= 0) {
            return point.x >= c.getX() - ex && point.x <= c.getX() + ex + 1
                    && point.y >= c.getY() - ey && point.y <= c.getY() + ey + 1
                    && point.z >= c.getZ() - ez && point.z <= c.getZ() + ez + 1;
        }
        return Vec3.atCenterOf(c).distanceToSqr(point) <= sphereSq;
    }

    public List<BlockPos> positions(SelectorContext ctx, int limit) {
        BlockPos c = anchor.apply(ctx);
        if (c == null) return List.of();
        List<BlockPos> out = new ArrayList<>();
        for (int dy = -ey; dy <= ey; dy++) {
            for (int dx = -ex; dx <= ex; dx++) {
                for (int dz = -ez; dz <= ez; dz++) {
                    if (sphereSq > 0 && (double) dx * dx + dy * dy + dz * dz > sphereSq) continue;
                    out.add(c.offset(dx, dy, dz));
                    if (limit > 0 && out.size() >= limit) return out;
                }
            }
        }
        return out;
    }
}
