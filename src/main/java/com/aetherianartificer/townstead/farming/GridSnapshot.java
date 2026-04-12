package com.aetherianartificer.townstead.farming;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Compact per-cell snapshot of the farm grid, built server-side and sent to the client.
 * Each cell encodes ground block, crop product, growth stage, and flags.
 * Arrays are row-major order: index = gz * gridSize + gx.
 */
public record GridSnapshot(
        int gridSize,
        byte[] flags,
        int[] groundBlockIds,  // registry int IDs from BuiltInRegistries.BLOCK
        int[] cropItemIds,     // registry int IDs from BuiltInRegistries.ITEM, 0 = no crop
        byte[] cropAges,
        byte[] cropMaxAges
) {
    // Flag bits
    public static final byte FLAG_AIR       = 0x01;
    public static final byte FLAG_WATER     = 0x02;
    public static final byte FLAG_FARMLAND  = 0x04;
    public static final byte FLAG_MOIST     = 0x08;
    public static final byte FLAG_HAS_CROP  = 0x10;
    public static final byte FLAG_MATURE    = 0x20;
    public static final byte FLAG_POST      = 0x40;

    public int cellCount() { return gridSize * gridSize; }

    public int index(int gx, int gz) { return gz * gridSize + gx; }

    public void write(FriendlyByteBuf buf) {
        int count = cellCount();
        buf.writeVarInt(gridSize);
        buf.writeByteArray(flags);
        for (int i = 0; i < count; i++) buf.writeVarInt(groundBlockIds[i]);
        for (int i = 0; i < count; i++) buf.writeVarInt(cropItemIds[i]);
        buf.writeByteArray(cropAges);
        buf.writeByteArray(cropMaxAges);
    }

    public static GridSnapshot read(FriendlyByteBuf buf) {
        int gridSize = buf.readVarInt();
        int count = gridSize * gridSize;
        byte[] flags = buf.readByteArray();
        int[] groundBlockIds = new int[count];
        for (int i = 0; i < count; i++) groundBlockIds[i] = buf.readVarInt();
        int[] cropItemIds = new int[count];
        for (int i = 0; i < count; i++) cropItemIds[i] = buf.readVarInt();
        byte[] cropAges = buf.readByteArray();
        byte[] cropMaxAges = buf.readByteArray();
        return new GridSnapshot(gridSize, flags, groundBlockIds, cropItemIds, cropAges, cropMaxAges);
    }
}
