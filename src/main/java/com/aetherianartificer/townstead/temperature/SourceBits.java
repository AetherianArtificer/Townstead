package com.aetherianartificer.townstead.temperature;

/** One chunk section's possible source cells as a 4096-bit set, indexed {@code y << 8 | z << 4 | x}. */
final class SourceBits {
    private final long[] words = new long[64];
    private int count;

    static int index(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    boolean set(int index, boolean value) {
        long bit = 1L << index;
        int word = index >>> 6;
        boolean had = (words[word] & bit) != 0;
        if (had == value) return false;
        words[word] ^= bit;
        count += value ? 1 : -1;
        return true;
    }

    boolean get(int index) {
        return (words[index >>> 6] & 1L << index) != 0;
    }

    int count() {
        return count;
    }

    interface CellVisitor {
        void visit(int index);
    }

    /** Set cells in storage order, one word at a time. */
    void forEach(CellVisitor visitor) {
        for (int word = 0; word < 64 && count > 0; word++) {
            long bits = words[word];
            while (bits != 0) {
                int bit = Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                visitor.visit(word << 6 | bit);
            }
        }
    }
}
