package com.aetherianartificer.townstead.work.site;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * How a {@link Worksite} finds itself in the world: which binding owns it, and the one stable number
 * that binding identifies it by.
 *
 * <p>The key is deliberately <em>borrowed</em> identity rather than minted identity. Townstead never
 * has to answer "is this fuzzy area the same kitchen as before" because it keys off something that
 * already carries an id — an MCA building id, a block position. A worksite's own id
 * ({@link Worksite#id()}) is separate and outlives the key, so a binding that breaks costs a lookup,
 * not a record.</p>
 *
 * <p>{@code value} is binding-specific and documented by the binding: the MCA room binding stores a
 * building id (unique per world, since MCA's counter lives on the village manager rather than on a
 * village), and the anchor binding stores a packed {@link BlockPos}. Keeping it a primitive means a
 * per-tick probe allocates one small record and never builds a string.</p>
 */
public record WorksiteKey(ResourceLocation binding, ResourceLocation dimension, long value) {

    public WorksiteKey {
        if (binding == null) throw new IllegalArgumentException("worksite key needs a binding");
        if (dimension == null) throw new IllegalArgumentException("worksite key needs a dimension");
    }

    /** A key for a binding that identifies places by block position. */
    public static WorksiteKey at(ResourceLocation binding, ResourceLocation dimension, BlockPos pos) {
        return new WorksiteKey(binding, dimension, pos.asLong());
    }

    /** The position this key packs, meaningful only for position-identified bindings. */
    public BlockPos pos() {
        return BlockPos.of(value);
    }

    @Override
    public String toString() {
        return binding + "@" + dimension + "#" + value;
    }
}
