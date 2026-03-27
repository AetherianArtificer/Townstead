package net.minecraft.core;

public class BlockPos {
    private final long encoded;

    public BlockPos(long encoded) {
        this.encoded = encoded;
    }

    public long asLong() {
        return encoded;
    }
}
