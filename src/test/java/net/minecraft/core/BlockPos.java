package net.minecraft.core;

public class BlockPos {
    private final long encoded;

    public BlockPos(long encoded) {
        this.encoded = encoded;
    }

    public BlockPos(int x, int y, int z) {
        this(asLong(x, y, z));
    }

    public static BlockPos of(long encoded) {
        return new BlockPos(encoded);
    }

    public static long asLong(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | ((long) y & 0xFFFL);
    }

    public long asLong() {
        return encoded;
    }

    public int getX() {
        return (int) (encoded >> 38);
    }

    public int getY() {
        return (int) (encoded << 52 >> 52);
    }

    public int getZ() {
        return (int) (encoded << 26 >> 38);
    }

    public BlockPos offset(int x, int y, int z) {
        return new BlockPos(getX() + x, getY() + y, getZ() + z);
    }
}
