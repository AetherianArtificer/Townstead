package com.aetherianartificer.townstead.farming.cellplan;

import net.minecraft.core.Direction;

/**
 * Per-cell settings of a {@link SoilType#TRELLIS} cell: how tall the farmer builds the support,
 * and which way a lattice panel faces. Poles ignore the facing.
 *
 * <p>Stored packed in one int so the plan, NBT, and network carry a single value per cell.</p>
 */
public record TrellisSpec(int height, Facing facing) {
    public static final int MIN_HEIGHT = 2;
    public static final int MAX_HEIGHT = 8;
    public static final TrellisSpec DEFAULT = new TrellisSpec(MIN_HEIGHT, Facing.NORTH);

    public enum Facing {
        NORTH(Direction.NORTH),
        EAST(Direction.EAST),
        SOUTH(Direction.SOUTH),
        WEST(Direction.WEST),
        /** One flat panel at the cell's height instead of an upright stack. */
        FLAT(Direction.NORTH);

        private final Direction direction;

        Facing(Direction direction) { this.direction = direction; }

        public Direction direction() { return direction; }

        public Facing next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public TrellisSpec {
        height = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height));
        if (facing == null) facing = Facing.NORTH;
    }

    public boolean flat() { return facing == Facing.FLAT; }

    public int pack() { return height | (facing.ordinal() << 4); }

    public static TrellisSpec unpack(int packed) {
        if (packed == 0) return DEFAULT;
        int facingOrdinal = (packed >>> 4) & 0xF;
        Facing[] facings = Facing.values();
        Facing facing = facingOrdinal < facings.length ? facings[facingOrdinal] : Facing.NORTH;
        return new TrellisSpec(packed & 0xF, facing);
    }
}
