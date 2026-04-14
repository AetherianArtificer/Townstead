package com.aetherianartificer.townstead.farming;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Server -> Client: grid snapshot + crop palette + status data for the Plot Planner.
 * Sent when a player opens a Field Post or requests a refresh.
 */
//? if neoforge {
public record FieldPostGridSyncPayload(
        BlockPos pos,
        GridSnapshot snapshot,
        Map<String, String> cropPalette,
        Map<String, Integer> villageSeedCounts,
        Map<String, Integer> seedSoilCompat,
        int farmerCount,
        int totalPlots,
        int tilledPlots,
        int hydrationPercent
) implements CustomPacketPayload {

    public static final Type<FieldPostGridSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "field_post_grid_sync"));

    public static final StreamCodec<FriendlyByteBuf, FieldPostGridSyncPayload> STREAM_CODEC =
            StreamCodec.of(FieldPostGridSyncPayload::encode, FieldPostGridSyncPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record FieldPostGridSyncPayload(
        BlockPos pos,
        GridSnapshot snapshot,
        Map<String, String> cropPalette,
        Map<String, Integer> villageSeedCounts,
        Map<String, Integer> seedSoilCompat,
        int farmerCount,
        int totalPlots,
        int tilledPlots,
        int hydrationPercent
) {
*///?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "field_post_grid_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "field_post_grid_sync");
    *///?}

    private static void encode(FriendlyByteBuf buf, FieldPostGridSyncPayload p) { p.write(buf); }
    private static FieldPostGridSyncPayload decode(FriendlyByteBuf buf) { return read(buf); }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        snapshot.write(buf);
        // Crop palette
        buf.writeVarInt(cropPalette.size());
        cropPalette.forEach((seedId, productId) -> {
            buf.writeUtf(seedId);
            buf.writeUtf(productId);
        });
        // Village seed counts
        buf.writeVarInt(villageSeedCounts.size());
        villageSeedCounts.forEach((seedId, count) -> {
            buf.writeUtf(seedId);
            buf.writeVarInt(count);
        });
        // Seed → compatible soil bitmask (bit N = SoilType.values()[N])
        buf.writeVarInt(seedSoilCompat.size());
        seedSoilCompat.forEach((seedId, bits) -> {
            buf.writeUtf(seedId);
            buf.writeVarInt(bits);
        });
        // Status
        buf.writeVarInt(farmerCount);
        buf.writeVarInt(totalPlots);
        buf.writeVarInt(tilledPlots);
        buf.writeVarInt(hydrationPercent);
    }

    public static FieldPostGridSyncPayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        GridSnapshot snapshot = GridSnapshot.read(buf);
        int paletteCount = buf.readVarInt();
        Map<String, String> palette = new HashMap<>();
        for (int i = 0; i < paletteCount; i++) palette.put(buf.readUtf(), buf.readUtf());
        int seedCountsSize = buf.readVarInt();
        Map<String, Integer> seedCounts = new HashMap<>();
        for (int i = 0; i < seedCountsSize; i++) seedCounts.put(buf.readUtf(), buf.readVarInt());
        int compatSize = buf.readVarInt();
        Map<String, Integer> compat = new HashMap<>();
        for (int i = 0; i < compatSize; i++) compat.put(buf.readUtf(), buf.readVarInt());
        int farmerCount = buf.readVarInt();
        int totalPlots = buf.readVarInt();
        int tilledPlots = buf.readVarInt();
        int hydrationPercent = buf.readVarInt();
        return new FieldPostGridSyncPayload(pos, snapshot, palette, seedCounts, compat,
                farmerCount, totalPlots, tilledPlots, hydrationPercent);
    }
}
